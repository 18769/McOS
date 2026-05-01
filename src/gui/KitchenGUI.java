package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.json.*;
import db.DBRequest;

public class KitchenGUI {
    private JButton scriptBtn;
    private scripting scriptingManager;

    private JFrame frame;
    private JPanel waitPanel, prodPanel;
    private JTextArea scheduleArea, historyArea;
    private JCheckBox takeoutBox;
    private JComboBox<String> algoSelector;
    private JLabel statusLabel;
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private LinkedList<String> historyList = new LinkedList<>();
    private LinkedHashMap<String, Integer> mealPrepTimes = new LinkedHashMap<>();
    private LinkedHashMap<String, String> combos = new LinkedHashMap<>();  // <combo_name, food_items>
    private LinkedHashMap<String, JSONObject> recipes = new LinkedHashMap<>();  // <meal_name, recipe_object>
    private ArrayList<JSONObject> workers = new ArrayList<>();
    private LinkedHashMap<Integer, String> workerNames = new LinkedHashMap<>();
    private ArrayList<Integer> workerIds = new ArrayList<>();
    private final HashSet<String> inProgressTasks = new HashSet<>();
    private final Object inProgressLock = new Object();
    private final LinkedHashMap<Integer, WorkerTaskState> activeWorkersState = new LinkedHashMap<>();
    private LinkedHashMap<Integer, JSONObject> orderRegistry = new LinkedHashMap<>();  // 訂單登記簿：order_id -> {name, total_tasks, remaining_tasks}
    private boolean isProductionRunning = false;

    private static class WorkerTaskState {
        JSONObject task;
        int remaining;
        JProgressBar bar;
        JPanel wrapper;
        String taskKey;
    }
    private int orderIdCounter = 1;

    // --- Modern Theme Colors ---
    private static final Color BG_DARK = new Color(24, 24, 27); // zinc-900
    private static final Color PANEL_BG = new Color(39, 39, 42); // zinc-800
    private static final Color BORDER_COLOR = new Color(63, 63, 70); // zinc-700
    private static final Color ACCENT_GOLD = new Color(255, 199, 44);
    private static final Color TEXT_PRIMARY = new Color(250, 250, 250); // zinc-50
    private static final Color TEXT_SECONDARY = new Color(161, 161, 170); // zinc-400

    public KitchenGUI() {
    mealPrepTimes = DBRequest.loadMeals(); // 載入餐點資料庫
    combos = DBRequest.loadCombos(); // 載入套餐資料庫
    recipes = loadRecipesLocal(); // 載入食譜
    applyWorkerRoster(DBRequest.loadWorkerRoster()); // 載入員工資料

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

    JButton equipBtn = createFlatButton("設備", PANEL_BG, TEXT_PRIMARY);
    equipBtn.addActionListener(e -> {
        EquipmentDialog dlg = new EquipmentDialog(frame);
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    });
    actionPanel.add(equipBtn);

    JButton recipeBtn = createFlatButton("查看食譜", PANEL_BG, TEXT_PRIMARY);
    recipeBtn.addActionListener(e -> {
        RecipeDialog rd = new RecipeDialog(frame);
        rd.setLocationRelativeTo(frame);
        rd.setVisible(true);
    });
    actionPanel.add(recipeBtn);

    JLabel algoLabel = new JLabel("排程策略:");
    algoLabel.setForeground(TEXT_SECONDARY);
    algoLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
    actionPanel.add(algoLabel);

    algoSelector = new JComboBox<>(new String[]{"FCFS", "SJF", "AGING"});
    algoSelector.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
    algoSelector.setBackground(PANEL_BG);
    algoSelector.setForeground(TEXT_PRIMARY);
    algoSelector.setFocusable(false);
    algoSelector.addActionListener(e -> switchSchedulerMode((String) algoSelector.getSelectedItem()));
    actionPanel.add(algoSelector);

    takeoutBox = new JCheckBox("外帶模式");
        takeoutBox.setForeground(TEXT_PRIMARY);
        takeoutBox.setBackground(new Color(18, 18, 20));
        takeoutBox.setFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
        takeoutBox.setFocusPainted(false);
        actionPanel.add(takeoutBox);

        scriptBtn = createFlatButton("📂 載入腳本", new Color(0, 120, 215), Color.WHITE);
        scriptingManager = new scripting(this, frame, scriptBtn, statusLabel, takeoutBox);
        scriptBtn.addActionListener(e -> scriptingManager.handleScriptButton());
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
                String mealName = entry.getKey();
                int prepTime = getRecipeOrDefaultPrepTime(mealName);
                addMenuButton(mealGridPanel, mealName, prepTime);
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
        switchSchedulerMode((String) algoSelector.getSelectedItem());
    }

    private void applyWorkerRoster(DBRequest.WorkerRoster roster) {
        workers.clear();
        workerNames.clear();
        workerIds.clear();

        if (roster != null) {
            workers.addAll(roster.workers);
            workerIds.addAll(roster.workerIds);
            workerNames.putAll(roster.workerNames);
        }

        if (workerIds.isEmpty()) {
            workerIds.add(1);
            workerNames.put(1, "Worker 1");
        }
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

    private LinkedHashMap<String, JSONObject> loadRecipesLocal() {
        LinkedHashMap<String, JSONObject> recipeMap = new LinkedHashMap<>();
        try {
            String recipePath = "DB/recipe.json";
            java.nio.file.Path path = java.nio.file.Paths.get(recipePath);
            if (!java.nio.file.Files.exists(path)) {
                return recipeMap;
            }
            String content = new String(java.nio.file.Files.readAllBytes(path), "UTF-8");
            JSONArray recipes = new JSONArray(content);
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject recipe = recipes.getJSONObject(i);
                String mealName = recipe.getString("meal_name");
                recipeMap.put(mealName, recipe);
            }
        } catch (Exception e) {
            System.err.println("Failed to load recipes: " + e.getMessage());
        }
        return recipeMap;
    }

    private int getRecipeOrDefaultPrepTime(String mealName) {
        if (recipes.containsKey(mealName)) {
            JSONObject recipe = recipes.get(mealName);
            JSONArray steps = recipe.optJSONArray("steps");
            if (steps != null && steps.length() > 0) {
                int total = 0;
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    total += step.optInt("duration_sec", 0);
                }
                return total;
            }
        }
        return getDefaultPrepTime(mealName);
    }


    void addOrderToBuffer(String item, int prepTime, int count, boolean isTakeout) {
        if (prepTime <= 0) {
            prepTime = getDefaultPrepTime(item);
        }
        for (int i = 0; i < count; i++) {
            JSONObject obj = new JSONObject();
            // ✓ 暫時不分配 id，等到發送時才一起分配
            obj.put("item", item);
            obj.put("prep_time", prepTime);
            obj.put("is_takeout", isTakeout);
            orderBuffer.add(obj);
        }
        updateWaitPanel();
    }

    void updateWaitPanel() {
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

    void processOrders() {
        if (orderBuffer.isEmpty())
            return;
        
        JSONArray ordersToSend = new JSONArray();
        
        // 累計所有餐點名稱和任務數
        JSONArray allItems = new JSONArray();
        int totalTasks = 0;
        StringBuilder orderDisplayName = new StringBuilder();
        
        System.out.println("📋 提交訂單組合：");
        
        for (Object item : orderBuffer) {
            JSONObject order = (JSONObject) item;
            String itemName = order.getString("item");
            boolean isTakeout = order.optBoolean("is_takeout", false);
            int prepTime = order.optInt("prep_time", 0);
            
            // 每份餐點分配獨立 ID（即使在同一個提交中也不同）
            int itemOrderId = orderIdCounter++;
            
            // 累計名稱
            if (orderDisplayName.length() > 0) {
                orderDisplayName.append("、");
            }
            orderDisplayName.append(itemName);
            
            try {
                // 嘗試查詢是否為套餐
                JSONArray comboItems = DBRequest.getComboItems(itemName);
                
                if (comboItems.length() > 0) {
                    // 是套餐
                    System.out.println("  📦 套餐: " + itemName + " (" + comboItems.length() + " 項) - 訂單 #" + itemOrderId);
                    JSONObject orderObj = new JSONObject();
                    orderObj.put("id", itemOrderId);
                    orderObj.put("item", itemName);
                    orderObj.put("is_takeout", isTakeout);
                    
                    JSONArray items = new JSONArray();
                    int totalTime = 0;
                    for (int j = 0; j < comboItems.length(); j++) {
                        JSONObject subItem = comboItems.getJSONObject(j);
                        items.put(subItem);
                        totalTime += subItem.getInt("prep_time");
                    }
                    orderObj.put("items", items);
                    orderObj.put("total_prep_time", totalTime);
                    ordersToSend.put(orderObj);
                    
                    totalTasks += comboItems.length();
                } else {
                    // 不是套餐：直接送出單項，recipe expand 交給 Python engine 處理
                    System.out.println("  🍔 單項: " + itemName + " - 訂單 #" + itemOrderId);
                    JSONObject orderObj = new JSONObject();
                    orderObj.put("id", itemOrderId);
                    orderObj.put("item", itemName);
                    orderObj.put("is_takeout", isTakeout);
                    orderObj.put("prep_time", prepTime);
                    ordersToSend.put(orderObj);

                    totalTasks += 1;
                }
            } catch (Exception e) {
                System.out.println("查詢套餐失敗，當作單項: " + itemName);
                JSONObject orderObj = new JSONObject();
                orderObj.put("id", itemOrderId);
                orderObj.put("item", itemName);
                orderObj.put("is_takeout", order.optBoolean("is_takeout", false));
                orderObj.put("prep_time", prepTime);
                ordersToSend.put(orderObj);
                
                totalTasks += 1;
            }
            
            // 為每份菜分別登記到 orderRegistry
            JSONObject itemInfo = new JSONObject();
            itemInfo.put("name", itemName);
            itemInfo.put("total_tasks", 1); // 初始值，會在 Python expand recipe 後更新
            itemInfo.put("remaining_tasks", 1);
            itemInfo.put("is_takeout", isTakeout);
            orderRegistry.put(itemOrderId, itemInfo);
        }
        
        System.out.println("  → 包含項目數: " + orderBuffer.size());
        System.out.println("  → 總任務數 (初始): " + totalTasks);
        
        JSONObject payload = new JSONObject();
        payload.put("type", "ADD_ORDER");
        payload.put("data", ordersToSend);
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
    // ✗ expandCombos() 已廢棄 - 改由 processOrders() 和 scheduler 負責

    private void startProductionLine() {
        if (isProductionRunning) {
            return;
        }
        isProductionRunning = true;
        new Thread(this::runProductionLoop).start();
    }

    private void runProductionLoop() {
        while (true) {
            JSONArray currentQueue = fetchCurrentQueue();
            for (int workerId : workerIds) {
                WorkerTaskState state = activeWorkersState.get(workerId);
                if (state == null) {
                    JSONObject nextTask = pickTaskForWorker(workerId, currentQueue);
                    if (nextTask != null) {
                        state = startWorkerTask(workerId, nextTask);
                        activeWorkersState.put(workerId, state);
                    }
                }
            }

            for (int workerId : new ArrayList<>(activeWorkersState.keySet())) {
                WorkerTaskState state = activeWorkersState.get(workerId);
                if (state == null) {
                    continue;
                }
                state.remaining -= 1;
                int progress = Math.max(0, state.task.getInt("prep_time") - state.remaining);
                SwingUtilities.invokeLater(() -> state.bar.setValue(progress));

                if (state.remaining <= 0) {
                    finishWorkerTask(workerId, state);
                    activeWorkersState.remove(workerId);
                }
            }

            if (currentQueue.length() == 0 && activeWorkersState.isEmpty()) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }

        isProductionRunning = false;
    }

    private JSONArray fetchCurrentQueue() {
        JSONObject request = new JSONObject();
        request.put("type", "GET_STATUS");
        String resp = sendToPython(request.toString());
        return new JSONArray(resp);
    }

    private JSONObject pickTaskForWorker(int workerId, JSONArray currentQueue) {

        JSONObject selected = null;
        int bestExpected = Integer.MAX_VALUE;
        for (int i = 0; i < currentQueue.length(); i++) {
            JSONObject task = currentQueue.getJSONObject(i);
            int taskWorker = task.optInt("worker_id", 1);
            if (taskWorker != workerId) {
                continue;
            }
            String key = buildTaskKey(task);
            synchronized (inProgressLock) {
                if (inProgressTasks.contains(key)) {
                    continue;
                }
            }
            int expected = task.optInt("expected_at", Integer.MAX_VALUE);
            if (selected == null || expected < bestExpected) {
                selected = task;
                bestExpected = expected;
            }
        }

        if (selected != null) {
            String key = buildTaskKey(selected);
            synchronized (inProgressLock) {
                inProgressTasks.add(key);
            }
            selected.put("_task_key", key);
        }
        return selected;
    }

    private String buildTaskKey(JSONObject task) {
        int id = task.optInt("id", -1);
        String item = task.optString("item", "");
        int index = task.optInt("task_index", -1);
        boolean isPack = task.optBoolean("is_pack_task", false);
        return id + "::" + item + "::" + index + "::" + isPack;
    }

    private WorkerTaskState startWorkerTask(int workerId, JSONObject task) {
        String name = task.optString("meal_name", task.getString("item"));
        int seconds = task.getInt("prep_time");
        int id = task.getInt("id");
        boolean isTakeout = task.optBoolean("is_takeout", false);
        String description = task.optString("description", "");

        JProgressBar bar = new JProgressBar(0, seconds);
        bar.setStringPainted(true);
        bar.setBackground(BG_DARK);
        bar.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        bar.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));

        String typeLabel = isTakeout ? "外帶" : "內用";
        String displayWorker = workerNames.getOrDefault(workerId, "Worker " + workerId);
        if (isTakeout) {
            bar.setForeground(new Color(255, 140, 0));
        } else {
            bar.setForeground(new Color(34, 199, 100)); // lighter green flat
        }
        bar.setString("員工: " + displayWorker + " | " + typeLabel + ": " + name + " | 步驟: " + description + " (#" + id + ")");

        JPanel barWrap = new JPanel(new BorderLayout());
        barWrap.setBackground(PANEL_BG);
        barWrap.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        barWrap.add(bar, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            prodPanel.add(barWrap);
            prodPanel.revalidate();
        });

        WorkerTaskState state = new WorkerTaskState();
        state.task = task;
        state.remaining = seconds;
        state.bar = bar;
        state.wrapper = barWrap;
        state.taskKey = task.optString("_task_key", "");
        return state;
    }

    private void finishWorkerTask(int workerId, WorkerTaskState state) {
        JSONObject task = state.task;
        String name = task.getString("item");
        int id = task.getInt("id");

        JSONObject finishReq = new JSONObject();
        finishReq.put("type", "FINISH_ORDER");
        finishReq.put("order_id", id);
        finishReq.put("item", name);
        String latestResp = sendToPython(finishReq.toString());

        SwingUtilities.invokeLater(() -> {
            prodPanel.remove(state.wrapper);
            prodPanel.revalidate();
            prodPanel.repaint();

            // Parse the response: {"queue": [...], "all_items_completed": bool, "order_content": "..."}
            boolean orderCompleted = false;
            JSONArray updatedQueue = null;
            
            try {
                if (latestResp != null && !latestResp.trim().isEmpty()) {
                    if (latestResp.trim().startsWith("{")) {
                        // New format: dict with queue + flags
                        JSONObject response = new JSONObject(latestResp);
                        orderCompleted = response.optBoolean("all_items_completed", false);
                        updatedQueue = response.optJSONArray("queue");
                    } else if (latestResp.trim().startsWith("[")) {
                        // Fallback: old format is just array
                        updatedQueue = new JSONArray(latestResp);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing FINISH_ORDER response: " + e.getMessage());
            }

            if (orderCompleted) {
                String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String orderName = orderRegistry.get(id).getString("name");
                String record = "[" + time + "] ✅ 訂單 #" + id + " 完成: " + orderName;
                addHistoryRecord(record);
                orderRegistry.remove(id);
            }

            // Update schedule display with the queue
            if (updatedQueue != null) {
                updateScheduleDisplay(updatedQueue);
            } else {
                // Fallback: fetch status if response parsing failed
                JSONObject request = new JSONObject();
                request.put("type", "GET_STATUS");
                String statusResp = sendToPython(request.toString());
                if (statusResp != null && statusResp.trim().startsWith("[")) {
                    updateScheduleDisplay(new JSONArray(statusResp));
                }
            }
        });

        if (state.taskKey != null && !state.taskKey.isBlank()) {
            synchronized (inProgressLock) {
                inProgressTasks.remove(state.taskKey);
            }
        }
    }
    
    /**
     * 檢查並標記任務完成。
     * 當訂單的所有任務都完成時，返回 true
     */
    private boolean checkAndMarkTaskComplete(int orderId, String taskName) {
        if (!orderRegistry.containsKey(orderId)) {
            return false;
        }
        
        JSONObject orderInfo = orderRegistry.get(orderId);
        int remaining = orderInfo.getInt("remaining_tasks") - 1;
        orderInfo.put("remaining_tasks", remaining);
        
        System.out.println("  ✓ 訂單 #" + orderId + " 完成任務 '" + taskName + "' (剩餘 " + remaining + "/" + orderInfo.getInt("total_tasks") + ")");
        
        return remaining <= 0;
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
            sb.append(String.format("%-4s | %-12s | %-5s | %-4s | %-6s\n", "ID", "項目", "預計", "類型", "員工"));
            sb.append("------------------------------------------------\n");
            
            // Group by group_id if available, otherwise by (id, meal_name)
            java.util.LinkedHashMap<String, JSONObject> seen = new java.util.LinkedHashMap<>();
            
            for (int i = 0; i < schedule.length(); i++) {
                JSONObject o = schedule.getJSONObject(i);
                int orderId = o.getInt("id");
                String mealName = o.optString("meal_name", o.getString("item"));
                
                // 優先使用 group_id，如果沒有則用 (id, meal_name)
                String groupId = o.optString("group_id", null);
                String key;
                if (groupId != null && !groupId.isEmpty()) {
                    key = groupId;
                } else {
                    key = orderId + "::" + mealName;
                }
                
                // Only add if we haven't seen this group yet
                if (!seen.containsKey(key)) {
                    seen.put(key, o);
                }
            }
            
            // Now display the unique combinations
            for (JSONObject o : seen.values()) {
                String type = o.optBoolean("is_takeout") ? "[外帶]" : "[內用]";
                int workerId = o.optInt("worker_id", 0);
                String workerLabel = workerId > 0 ? workerNames.getOrDefault(workerId, "W" + workerId) : "-";
                String displayName = o.optString("meal_name", o.getString("item"));
                int prepTime = o.optInt("prep_time", 0);
                sb.append(String.format("[#%02d] %-12s | %3ds | %s | %-6s\n",
                        o.getInt("id"), displayName, prepTime, type, workerLabel));
            }
            scheduleArea.setText(sb.toString());
        });
    }

    private void switchSchedulerMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return;
        }
        JSONObject payload = new JSONObject();
        payload.put("type", "SWITCH_MODE");
        payload.put("mode", mode);
        String response = sendToPython(payload.toString());
        if (response != null && response.trim().startsWith("[")) {
            updateScheduleDisplay(new JSONArray(response));
        }
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

    // 腳本系統已搬到 gui.scripting

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

    void clearOrderBuffer() {
        orderBuffer.clear();
        updateWaitPanel();
    }

    void appendHistoryMessage(String msg) {
        historyArea.append(msg);
    }
}