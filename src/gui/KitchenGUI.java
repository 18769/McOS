package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.json.*;
import db.DBHelper;

public class KitchenGUI {
    private JButton scriptBtn;
    private Thread scriptThread = null; // 用來保存當前執行的執行緒

    private JFrame frame;
    private JPanel waitPanel, prodPanel;
    private JTextArea scheduleArea, historyArea;
    private JCheckBox takeoutBox;
    private JLabel statusLabel;
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private LinkedList<String> historyList = new LinkedList<>();
    private LinkedHashMap<String, Integer> mealPrepTimes = new LinkedHashMap<>();
    private LinkedHashMap<String, String> combos = new LinkedHashMap<>();  // <combo_name, food_items>
    private boolean isProductionRunning = false;
    private int orderIdCounter = 1;
    private boolean isScriptRunning = false;

    // --- Modern Theme Colors ---
    private static final Color BG_DARK = new Color(24, 24, 27); // zinc-900
    private static final Color PANEL_BG = new Color(39, 39, 42); // zinc-800
    private static final Color BORDER_COLOR = new Color(63, 63, 70); // zinc-700
    private static final Color ACCENT_GOLD = new Color(255, 199, 44);
    private static final Color TEXT_PRIMARY = new Color(250, 250, 250); // zinc-50
    private static final Color TEXT_SECONDARY = new Color(161, 161, 170); // zinc-400

    public KitchenGUI() {
        loadMeals(); // 載入餐點資料庫
        loadCombos(); // 載入套餐資料庫

        // --- 1. 全域外觀設定 ---
        frame = new JFrame("McOS 智慧廚房 - 專業出餐系統");
        frame.setSize(1200, 750); // 維持大小
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG_DARK);

        // --- 2. 頂部狀態控制區 (Top Bar) ---
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(18, 18, 20)); // darker than BG_DARK
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_GOLD),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));

        JLabel appTitle = new JLabel("McOS 智慧廚房");
        appTitle.setFont(new Font("Microsoft JhengHei", Font.BOLD, 22));
        appTitle.setForeground(ACCENT_GOLD);
        topBar.add(appTitle, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        actionPanel.setBackground(new Color(18, 18, 20));

        statusLabel = new JLabel("● 系統狀態: 檢測中");
        statusLabel.setForeground(TEXT_SECONDARY);
        statusLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        actionPanel.add(statusLabel);

        takeoutBox = new JCheckBox("外帶模式");
        takeoutBox.setForeground(TEXT_PRIMARY);
        takeoutBox.setBackground(new Color(18, 18, 20));
        takeoutBox.setFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
        takeoutBox.setFocusPainted(false);
        actionPanel.add(takeoutBox);

        scriptBtn = createFlatButton("📂 載入腳本", new Color(0, 120, 215), Color.WHITE);
        scriptBtn.addActionListener(e -> {
            if (!isScriptRunning) {
                openScriptFileChooser(); // 傳入按鈕
            } else {
                executeScriptFile(null, scriptBtn); // 傳入 null File 觸發中斷判斷
            }
        });
        actionPanel.add(scriptBtn);

        JButton clearBtn = createFlatButton("🗑️ 清空暫存", new Color(220, 53, 69), Color.WHITE);
        clearBtn.addActionListener(e -> {
            orderBuffer.clear();
            updateWaitPanel();
        });
        actionPanel.add(clearBtn);

        JButton runBtn = createFlatButton("🚀 送出排程", ACCENT_GOLD, Color.BLACK);
        runBtn.addActionListener(e -> processOrders());
        actionPanel.add(runBtn);

        topBar.add(actionPanel, BorderLayout.EAST);

        // --- 3. 主面板配置 (Main Content) ---
        JPanel mainContent = new JPanel(new BorderLayout(15, 0));
        mainContent.setBackground(BG_DARK);
        mainContent.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // [左側] 點餐網格區 (Grid Menu)
        JPanel menuSection = createSectionPanel("點餐選項");
        menuSection.setPreferredSize(new Dimension(300, 0));

        // 改用 BoxLayout 垂直排列，方便分組
        JPanel menuContentPanel = new JPanel();
        menuContentPanel.setLayout(new BoxLayout(menuContentPanel, BoxLayout.Y_AXIS));
        menuContentPanel.setBackground(PANEL_BG);
        menuContentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== 套餐區 (Combo Section) =====
        if (!combos.isEmpty()) {
            JLabel comboLabel = new JLabel("🍱 套 餐");
            comboLabel.setForeground(ACCENT_GOLD);
            comboLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
            comboLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            menuContentPanel.add(comboLabel);
            menuContentPanel.add(Box.createVerticalStrut(8));
            
            JPanel comboGridPanel = new JPanel(new GridLayout(0, 2, 10, 10));
            comboGridPanel.setBackground(PANEL_BG);
            comboGridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            for (String comboName : combos.keySet()) {
                addMenuButton(comboGridPanel, comboName, 0);
            }
            menuContentPanel.add(comboGridPanel);
            menuContentPanel.add(Box.createVerticalStrut(15));
        }

        // ===== 分隔線 =====
        if (!combos.isEmpty() && !mealPrepTimes.isEmpty()) {
            JSeparator sep = new JSeparator();
            sep.setForeground(BORDER_COLOR);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            menuContentPanel.add(sep);
            menuContentPanel.add(Box.createVerticalStrut(15));
        }

        // ===== 餐點區 (Meal Section) =====
        if (!mealPrepTimes.isEmpty()) {
            JLabel mealLabel = new JLabel("🍔 餐 點");
            mealLabel.setForeground(new Color(100, 200, 150));
            mealLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
            mealLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            menuContentPanel.add(mealLabel);
            menuContentPanel.add(Box.createVerticalStrut(8));
            
            JPanel mealGridPanel = new JPanel(new GridLayout(0, 2, 10, 10));
            mealGridPanel.setBackground(PANEL_BG);
            mealGridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            for (Map.Entry<String, Integer> entry : mealPrepTimes.entrySet()) {
                addMenuButton(mealGridPanel, entry.getKey(), entry.getValue());
            }
            menuContentPanel.add(mealGridPanel);
        }

        // 確保內容不為空時加入最後的垂直填充
        menuContentPanel.add(Box.createVerticalGlue());

        // 隱藏滾動條維持乾淨外觀
        JScrollPane menuScroll = new JScrollPane(menuContentPanel);
        menuScroll.setBorder(null);
        menuScroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = BORDER_COLOR;
                this.trackColor = PANEL_BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
        menuScroll.getVerticalScrollBar().setUnitIncrement(16);
        menuSection.add(menuScroll, BorderLayout.CENTER);

        mainContent.add(menuSection, BorderLayout.WEST);

        // [右側] 監控區 (Monitors)
        JPanel monitorsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        monitorsPanel.setBackground(BG_DARK);

        waitPanel = new JPanel();
        waitPanel.setLayout(new BoxLayout(waitPanel, BoxLayout.Y_AXIS));
        waitPanel.setBackground(PANEL_BG);
        monitorsPanel.add(createScrollPanel(waitPanel, "【1】 等待中"));

        prodPanel = new JPanel();
        prodPanel.setLayout(new BoxLayout(prodPanel, BoxLayout.Y_AXIS));
        prodPanel.setBackground(PANEL_BG);
        monitorsPanel.add(createScrollPanel(prodPanel, "【2】 廚房製作"));

        JPanel dataPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        dataPanel.setBackground(BG_DARK);

        scheduleArea = new JTextArea();
        historyArea = new JTextArea();
        dataPanel.add(createScrollText(scheduleArea, "【3】 智慧排程"));
        dataPanel.add(createScrollText(historyArea, "【4】 完成紀錄"));

        monitorsPanel.add(dataPanel);
        mainContent.add(monitorsPanel, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(topBar, BorderLayout.NORTH);
        frame.add(mainContent, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        startAutoSaveTimer();
        startStatusChecker();
    }

    private JButton createFlatButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.putClientProperty("baseBg", bg);

        // 懸停特效
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                Color base = (Color) btn.getClientProperty("baseBg");
                if (base != null)
                    btn.setBackground(base.brighter());
            }

            public void mouseExited(MouseEvent e) {
                Color base = (Color) btn.getClientProperty("baseBg");
                if (base != null)
                    btn.setBackground(base);
            }
        });
        return btn;
    }

    private JPanel createSectionPanel(String title) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(PANEL_BG);
        section.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        header.setBackground(new Color(30, 30, 34));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        titleLabel.setForeground(ACCENT_GOLD);
        header.add(titleLabel);

        section.add(header, BorderLayout.NORTH);
        return section;
    }

    private void startStatusChecker() {
        Thread t = new Thread(() -> {
            while (true) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress("localhost", 9999), 500);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("● 系統狀態: 已連線");
                        statusLabel.setForeground(new Color(50, 255, 100)); // lighter green
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("● 系統狀態: 斷線中");
                        statusLabel.setForeground(new Color(255, 80, 80)); // lighter red
                    });
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // --- 網格按鈕卡片樣式 ---
    private void addMenuButton(JPanel p, String name, int time) {
        String subText = time > 0 ? time + "s" : "[🍱]";
        JButton b = new JButton("<html><center><b>" + name + "</b><br><font size='3' color='#A1A1AA'>" + subText
                + "</font></center></html>");
        b.setFocusPainted(false);
        b.setBackground(PANEL_BG);
        b.setForeground(TEXT_PRIMARY);
        b.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));

        // 自訂卡片邊框
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(15, 5, 15, 5)));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 卡片懸停效果
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                b.setBackground(BORDER_COLOR);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACCENT_GOLD, 1),
                        BorderFactory.createEmptyBorder(15, 5, 15, 5)));
            }

            public void mouseExited(MouseEvent evt) {
                b.setBackground(PANEL_BG);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1),
                        BorderFactory.createEmptyBorder(15, 5, 15, 5)));
            }
        });

        b.addActionListener(e -> {
            addOrderToBuffer(name, time, 1, takeoutBox.isSelected());
        });
        p.add(b);
    }

    private int getDefaultPrepTime(String item) {
        return mealPrepTimes.getOrDefault(item, 5);
    }

    private void loadMeals() {
        try {
            // 從資料庫讀取餐點資料
            JSONArray meals = DBHelper.queryMeals();
            System.out.println("=== 餐點資料調試 ===");
            for (int i = 0; i < meals.length(); i++) {
                JSONObject m = meals.getJSONObject(i);
                String mealName = m.getString("meal_name");
                int prepTime = m.getInt("prep_time");
                System.out.println("  餐點: " + mealName + " | 準備時間: " + prepTime);
                mealPrepTimes.put(mealName, prepTime);
            }
            System.out.println("從資料庫成功載入 " + meals.length() + " 個餐點\n");
        } catch (Exception e) {
            System.err.println("讀取資料庫失敗，改用預設資料: " + e.getMessage());
            e.printStackTrace();
            // 預設資料備用
            mealPrepTimes.put("大麥克", 8);
            mealPrepTimes.put("小麥克", 6);
            mealPrepTimes.put("麥克", 7);
            mealPrepTimes.put("薯條", 3);
            mealPrepTimes.put("雞塊", 5);
            mealPrepTimes.put("蘋果派", 4);
            mealPrepTimes.put("玉米湯", 2);
            mealPrepTimes.put("可樂", 1);
            mealPrepTimes.put("舊東洋熱狗", 5);
            mealPrepTimes.put("冰美式", 3);
            mealPrepTimes.put("大麥克預設", 0);
            mealPrepTimes.put("雞塊特餐", 0);
        }
    }

    private void loadCombos() {
        try {
            // 從資料庫讀取套餐資料
            JSONArray combosArray = DBHelper.queryCombos();
            System.out.println("=== 套餐資料調試 ===");
            for (int i = 0; i < combosArray.length(); i++) {
                JSONObject c = combosArray.getJSONObject(i);
                String comboName = c.getString("combo_name");
                String foodItems = c.getString("food_items");
                System.out.println("  套餐: " + comboName + " | 食材: " + foodItems);
                combos.put(comboName, foodItems);
            }
            System.out.println("從資料庫成功載入 " + combosArray.length() + " 個套餐\n");
        } catch (Exception e) {
            System.err.println("讀取套餐失敗: " + e.getMessage());
            // 不使用預設套餐，因為套餐應該從資料庫讀取
        }
    }

    private void addOrderToBuffer(String item, int prepTime, int count, boolean isTakeout) {
        if (prepTime <= 0) {
            prepTime = getDefaultPrepTime(item);
        }
        for (int i = 0; i < count; i++) {
            JSONObject obj = new JSONObject();
            obj.put("id", orderIdCounter++);
            obj.put("item", item);
            obj.put("prep_time", prepTime);
            obj.put("is_takeout", isTakeout);
            orderBuffer.add(obj);
        }
        updateWaitPanel();
    }

    private void updateWaitPanel() {
        waitPanel.removeAll();
        for (JSONObject o : orderBuffer) {
            boolean isTakeout = o.optBoolean("is_takeout", false);
            String tag = isTakeout ? "[外帶]" : "[內用]";
            JLabel label = new JLabel(" " + tag + " " + o.getString("item"));
            label.setForeground(isTakeout ? ACCENT_GOLD : TEXT_PRIMARY);
            label.setFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
            // 加一點間隔
            label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            waitPanel.add(label);
        }
        waitPanel.revalidate();
        waitPanel.repaint();
    }

    private void processOrders() {
        if (orderBuffer.isEmpty())
            return;
        
        // 解析套餐：將套餐拆成單項任務
        JSONArray expandedOrders = expandCombos(new JSONArray(orderBuffer));
        
        JSONObject payload = new JSONObject();
        payload.put("type", "ADD_ORDER");
        payload.put("data", expandedOrders);
        String response = sendToPython(payload.toString());
        orderBuffer.clear();
        updateWaitPanel();
        updateScheduleDisplay(new JSONArray(response));
        if (!isProductionRunning)
            startProductionLine();
    }
    
    /**
     * 將訂單中的套餐拆解成單項任務
     */
    private JSONArray expandCombos(JSONArray orders) {
        JSONArray expanded = new JSONArray();
        
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.getJSONObject(i);
            String itemName = order.getString("item");
            int orderId = order.getInt("id");
            boolean isTakeout = order.optBoolean("is_takeout", false);
            
            try {
                // 嘗試從資料庫查詢此名稱是否為套餐
                JSONArray comboItems = DBHelper.getComboItems(itemName);
                
                if (comboItems.length() > 0) {
                    // 是套餐，拆解成子項目
                    System.out.println("→ 拆解套餐: " + itemName);
                    for (int j = 0; j < comboItems.length(); j++) {
                        JSONObject subItem = comboItems.getJSONObject(j);
                        JSONObject task = new JSONObject();
                        task.put("id", orderId);
                        task.put("item", subItem.getString("item"));
                        task.put("prep_time", subItem.getInt("prep_time"));
                        task.put("is_takeout", isTakeout);
                        expanded.put(task);
                        System.out.println("  ├─ " + subItem.getString("item") + 
                                         " (" + subItem.getInt("prep_time") + "s)");
                    }
                } else {
                    // 不是套餐，直接加入
                    expanded.put(order);
                }
            } catch (Exception e) {
                System.out.println("查詢套餐失敗: " + e.getMessage() + ", 當作單項處理");
                // 查詢失敗，當作單項處理
                expanded.put(order);
            }
        }
        
        return expanded;
    }

    private void startProductionLine() {
        new Thread(() -> {
            isProductionRunning = true;
            while (true) {
                JSONObject request = new JSONObject();
                request.put("type", "GET_STATUS");
                String resp = sendToPython(request.toString());
                JSONArray currentQueue = new JSONArray(resp);
                if (currentQueue.length() == 0)
                    break;
                doWork(currentQueue.getJSONObject(0));
            }
            isProductionRunning = false;
        }).start();
    }

    private void doWork(JSONObject task) {
        String name = task.getString("item");
        int seconds = task.getInt("prep_time");
        int id = task.getInt("id");
        boolean isTakeout = task.optBoolean("is_takeout", false);

        JProgressBar bar = new JProgressBar(0, seconds);
        bar.setStringPainted(true);
        bar.setBackground(BG_DARK);
        bar.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        bar.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));

        if (isTakeout) {
            bar.setForeground(new Color(255, 140, 0));
            bar.setString("外帶: " + name + " (#" + id + ")");
        } else {
            bar.setForeground(new Color(34, 199, 100)); // lighter green flat
            bar.setString("內用: " + name + " (#" + id + ")");
        }

        SwingUtilities.invokeLater(() -> {
            JPanel barWrap = new JPanel(new BorderLayout());
            barWrap.setBackground(PANEL_BG);
            barWrap.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            barWrap.add(bar, BorderLayout.CENTER);

            prodPanel.add(barWrap);
            prodPanel.revalidate();
        });

        for (int i = 0; i <= seconds; i++) {
            final int current = i;
            SwingUtilities.invokeLater(() -> bar.setValue(current));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

        JSONObject finishReq = new JSONObject();
        finishReq.put("type", "FINISH_ORDER");
        finishReq.put("order_id", id);
        finishReq.put("item", name);
        String latestResp = sendToPython(finishReq.toString());

        SwingUtilities.invokeLater(() -> {
            // Remove the specific wrapper
            for (Component comp : prodPanel.getComponents()) {
                if (comp instanceof JPanel && ((JPanel) comp).getComponent(0) == bar) {
                    prodPanel.remove(comp);
                    break;
                }
            }
            prodPanel.revalidate();
            prodPanel.repaint();
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String record = "[" + time + "] OK " + name + " (#" + id + ") 完成";
            addHistoryRecord(record);
            updateScheduleDisplay(new JSONArray(latestResp));
        });
    }

    private void addHistoryRecord(String record) {
        historyList.add(record);
        if (historyList.size() > 10) {
            historyList.removeFirst();
        }
        StringBuilder sb = new StringBuilder();
        for (String s : historyList) {
            sb.append(s).append("\n");
        }
        historyArea.setText(sb.toString());
    }

    private void updateScheduleDisplay(JSONArray schedule) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder("=== 智慧排程 ===\n\n");
            sb.append(String.format("%-4s | %-12s | %-5s | %-4s\n", "ID", "項目", "預計", "類型"));
            sb.append("--------------------------------------\n");
            for (int i = 0; i < schedule.length(); i++) {
                JSONObject o = schedule.getJSONObject(i);
                String type = o.optBoolean("is_takeout") ? "[外帶]" : "[內用]";
                sb.append(String.format("[#%02d] %-12s | %3ds | %s\n",
                        o.getInt("id"), o.getString("item"), o.getInt("expected_at"), type));
            }
            scheduleArea.setText(sb.toString());
        });
    }

    // --- Modern Component Creators ---
    private JPanel createScrollPanel(JPanel contentPanel, String title) {
        JPanel section = createSectionPanel(title);
        JScrollPane sp = new JScrollPane(contentPanel);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        // hide scrollbar UI similar to menu
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = BORDER_COLOR;
                this.trackColor = PANEL_BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
        section.add(sp, BorderLayout.CENTER);
        return section;
    }

    private JPanel createScrollText(JTextArea ta, String title) {
        JPanel section = createSectionPanel(title);
        ta.setEditable(false);
        ta.setBackground(PANEL_BG);
        ta.setForeground(TEXT_PRIMARY);
        ta.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        ta.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = BORDER_COLOR;
                this.trackColor = PANEL_BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
        section.add(sp, BorderLayout.CENTER);
        return section;
    }

    private void openScriptFileChooser() {
        JFileChooser chooser = new JFileChooser(new File("./scripts"));
        chooser.setDialogTitle("選擇 JSON 測試腳本");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON 腳本檔", "json"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            executeScriptFile(file, this.scriptBtn);
        }
    }

    private void executeScriptFile(File file, JButton targetBtn) {
        if (isScriptRunning) {
            if (scriptThread != null) {
                scriptThread.interrupt();
                SwingUtilities.invokeLater(() -> showAutoCloseMsg("腳本已中斷", 2000));
            }
            return;
        }
        isScriptRunning = true;

        SwingUtilities.invokeLater(() -> {
            targetBtn.setText("🛑 中斷腳本");
            targetBtn.putClientProperty("baseBg", new Color(220, 53, 69));
            targetBtn.setBackground(new Color(220, 53, 69)); // 轉為紅色
        });

        scriptThread = new Thread(() -> {
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                JSONObject script = new JSONObject(content);
                JSONArray steps = script.getJSONArray("steps");

                SwingUtilities.invokeLater(() -> showAutoCloseMsg("腳本即將開始...", 2000));
                Thread.sleep(2000);

                SwingUtilities.invokeLater(() -> statusLabel.setText("● 系統狀態: 腳本執行中"));
                executeSteps(steps);

                SwingUtilities.invokeLater(() -> showAutoCloseMsg("腳本執行結束！", 2000));
                SwingUtilities.invokeLater(() -> statusLabel.setText("● 系統狀態: 腳本已完成"));
                Thread.sleep(2000);

            } catch (InterruptedException ex) {
                // 當 thread.interrupt() 被呼叫時，Thread.sleep 會拋出此異常
                SwingUtilities.invokeLater(() -> historyArea.append("⚠️ 腳本已被使用者強制中斷\n"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "腳本解析錯誤: " + ex.getMessage(),
                        "錯誤", JOptionPane.ERROR_MESSAGE));
            } finally {
                scriptThread = null;
                isScriptRunning = false;
                SwingUtilities.invokeLater(() -> {
                    targetBtn.setText("📂 載入腳本");
                    targetBtn.putClientProperty("baseBg", new Color(0, 120, 215));
                    targetBtn.setBackground(new Color(0, 120, 215)); // 恢復藍色
                    statusLabel.setText("● 系統狀態: 待機中");
                });
            }
        });
        scriptThread.start();
    }

    private void showAutoCloseMsg(String msg, int delayMs) {
        JDialog dialog = new JDialog(frame, "系統提示", false);
        JOptionPane opt = new JOptionPane(msg, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] {}, null);
        dialog.setContentPane(opt);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);

        javax.swing.Timer timer = new javax.swing.Timer(delayMs, e -> dialog.dispose());
        timer.setRepeats(false);
        timer.start();

        dialog.setVisible(true);
    }

    // 腳本系統
    private void executeSteps(JSONArray steps) throws Exception {
        for (int i = 0; i < steps.length(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("User requested stop");
            }

            JSONObject step = steps.getJSONObject(i);
            String action = step.optString("action", "");

            switch (action) {
                case "SET_MODE":
                    boolean isTakeout = step.optBoolean("takeout", false);
                    SwingUtilities.invokeAndWait(() -> takeoutBox.setSelected(isTakeout));
                    break;
                case "ADD_ORDER":
                    String item = step.getString("item");
                    int prepTime = step.optInt("prep_time", 0);
                    int count = step.optInt("count", 1);
                    SwingUtilities.invokeAndWait(() -> {
                        addOrderToBuffer(item, prepTime, count, takeoutBox.isSelected());
                    });
                    break;
                case "SEND_ORDERS":
                    SwingUtilities.invokeAndWait(this::processOrders);
                    break;
                case "CLEAR_BUFFER":
                    SwingUtilities.invokeAndWait(() -> {
                        orderBuffer.clear();
                        updateWaitPanel();
                    });
                    break;
                case "WAIT":
                    int seconds = step.optInt("seconds", 1);
                    Thread.sleep(seconds * 1000L);
                    break;
                case "REPEAT":
                    int times = step.optInt("times", 1);
                    JSONArray subSteps = step.optJSONArray("steps");
                    if (subSteps != null) {
                        for (int t = 0; t < times; t++) {
                            executeSteps(subSteps);
                        }
                    }
                    break;
                default:
                    System.out.println("未知腳本指令: " + action);
            }
            Thread.sleep(200); // 讓連續動畫不會太快閃現
        }
    }

    private String sendToPython(String payload) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 9999), 1000);
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write(payload);
            out.newLine();
            out.flush();
            return in.readLine();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void startAutoSaveTimer() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000L); // 每10秒存一次
                    String content = scheduleArea.getText();
                    if (content != null && content.contains("ID")) {
                        Path dir = Paths.get("./data");
                        if (!Files.exists(dir))
                            Files.createDirectories(dir);
                        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        Path filePath = dir.resolve("order_backup_" + ts + ".txt");
                        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    // 防止程式崩潰
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) {
        // 設定 Swing 去除預設樣式以達到扁平化
        UIManager.put("Button.select", BORDER_COLOR);
        SwingUtilities.invokeLater(KitchenGUI::new);
    }
}