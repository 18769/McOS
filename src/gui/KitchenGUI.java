package gui;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.json.*;
import db.DBHelper; // 引入你提供的 DBHelper

public class KitchenGUI {
    private JFrame frame;
    private JPanel waitPanel, prodPanel, menuPanel;
    private JTextArea scheduleArea, historyArea;
    private JCheckBox takeoutBox;
    private JLabel statusLabel;
    private JComboBox<String> algoSelector;
    
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private LinkedList<String> historyList = new LinkedList<>();
    // 儲存從資料庫讀取的單品：名稱 -> 製作時間
    private LinkedHashMap<String, Integer> mealPrepTimes = new LinkedHashMap<>();
    private int orderIdCounter = 1;
    private boolean isProductionRunning = false;

    private static final Color BG_DARK = new Color(24, 24, 27);
    private static final Color PANEL_BG = new Color(39, 39, 42);
    private static final Color ACCENT_GOLD = new Color(255, 199, 44);
    private static final Color COMBO_COLOR = new Color(255, 140, 0); 
    private static final Color TEXT_PRIMARY = new Color(250, 250, 250);

    public KitchenGUI() {
        loadDataFromDB(); // 改從資料庫讀取 [cite: 9]
        setupMainFrame();
        startStatusChecker();
    }

    /**
     * 從資料庫讀取所有單品資料 [cite: 69]
     */
    private void loadDataFromDB() {
        try {
            JSONArray meals = DBHelper.queryMeals();
            mealPrepTimes.clear();
            for (int i = 0; i < meals.length(); i++) {
                JSONObject meal = meals.getJSONObject(i);
                mealPrepTimes.put(meal.getString("meal_name"), meal.getInt("prep_time"));
            }
        } catch (Exception e) {
            System.err.println("無法從資料庫讀取單品: " + e.getMessage());
            // 備用數據，防止斷網時無法開啟介面
            mealPrepTimes.put("備用大麥克", 8);
        }
    }

    private void setupMainFrame() {
        frame = new JFrame("McOS 智慧廚房 - 演算指標實驗系統");
        frame.setSize(1240, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG_DARK);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(18, 18, 20));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JLabel appTitle = new JLabel("McOS 智慧廚房實驗室");
        appTitle.setFont(new Font("Microsoft JhengHei", Font.BOLD, 22));
        appTitle.setForeground(ACCENT_GOLD);
        topBar.add(appTitle, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        actionPanel.setBackground(new Color(18, 18, 20));
        
        String[] algos = { "FCFS (先來先服務)", "SJF (最短任務優先)", "AGING (老化技術)" };
        algoSelector = new JComboBox<>(algos);
        algoSelector.addActionListener(e -> switchAlgorithm());
        actionPanel.add(new JLabel("<html><font color='white'>排程算法:</font></html>"));
        actionPanel.add(algoSelector);

        takeoutBox = new JCheckBox("外帶模式");
        takeoutBox.setForeground(Color.WHITE);
        takeoutBox.setBackground(new Color(18, 18, 20));
        actionPanel.add(takeoutBox);

        JButton runBtn = createFlatButton("🚀 送出訂單", ACCENT_GOLD, Color.BLACK);
        runBtn.addActionListener(e -> processOrders());
        actionPanel.add(runBtn);

        statusLabel = new JLabel("● 系統狀態: 檢測中");
        statusLabel.setForeground(Color.GRAY);
        actionPanel.add(statusLabel);
        topBar.add(actionPanel, BorderLayout.EAST);

        JPanel mainContent = new JPanel(new GridLayout(1, 3, 15, 0));
        mainContent.setBackground(BG_DARK);
        mainContent.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        waitPanel = new JPanel();
        waitPanel.setLayout(new BoxLayout(waitPanel, BoxLayout.Y_AXIS));
        waitPanel.setBackground(PANEL_BG);
        mainContent.add(createScrollPanel(waitPanel, "【1】 訂單暫存區"));

        prodPanel = new JPanel();
        prodPanel.setLayout(new BoxLayout(prodPanel, BoxLayout.Y_AXIS));
        prodPanel.setBackground(PANEL_BG);
        mainContent.add(createScrollPanel(prodPanel, "【2】 廚房製作進度"));

        JPanel dataPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        dataPanel.setBackground(BG_DARK);
        scheduleArea = new JTextArea();
        scheduleArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        scheduleArea.setEditable(false);
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        dataPanel.add(createScrollText(scheduleArea, "【3】 智慧排程分析 (SJF/Aging)"));
        dataPanel.add(createScrollText(historyArea, "【4】 完成紀錄 (實驗數據)"));
        mainContent.add(dataPanel);

        menuPanel = createMenuPanel();
        frame.add(topBar, BorderLayout.NORTH);
        frame.add(menuPanel, BorderLayout.WEST);
        frame.add(mainContent, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createMenuPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL_BG);
        p.setPreferredSize(new Dimension(200, 0));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel singleLabel = new JLabel("--- 單點項目 ---");
        singleLabel.setForeground(Color.GRAY);
        p.add(singleLabel);
        p.add(Box.createVerticalStrut(10));

        // 動態生成資料庫單品按鈕 [cite: 26]
        for (String meal : mealPrepTimes.keySet()) {
            JButton b = new JButton(meal);
            b.setMaximumSize(new Dimension(180, 40));
            b.addActionListener(e -> addOrder(meal, mealPrepTimes.get(meal)));
            p.add(b);
            p.add(Box.createVerticalStrut(5));
        }

        p.add(Box.createVerticalStrut(20));
        JLabel comboLabel = new JLabel("--- 經典套餐 ---");
        comboLabel.setForeground(Color.GRAY);
        p.add(comboLabel);
        p.add(Box.createVerticalStrut(10));
        
        // 動態生成資料庫套餐按鈕 [cite: 28]
        try {
            JSONArray combos = DBHelper.queryCombos();
            for (int i = 0; i < combos.length(); i++) {
                String comboName = combos.getJSONObject(i).getString("combo_name");
                JButton comboBtn = new JButton("<html><center>" + comboName + "</center></html>");
                comboBtn.setMaximumSize(new Dimension(180, 50));
                comboBtn.setBackground(COMBO_COLOR);
                // 點擊後從資料庫抓取套餐細節項目
                comboBtn.addActionListener(e -> {
                    try {
                        JSONArray items = DBHelper.getComboItems(comboName);
                        addComboOrderFromDB(comboName, items);
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
                p.add(comboBtn);
                p.add(Box.createVerticalStrut(5));
            }
        } catch (Exception e) {
            System.err.println("套餐按鈕加載失敗");
        }

        return p;
    }

    private void addComboOrderFromDB(String comboName, JSONArray items) {
        int id = orderIdCounter++;
        JSONObject comboOrder = new JSONObject();
        comboOrder.put("id", id);
        comboOrder.put("item", comboName);
        comboOrder.put("is_takeout", takeoutBox.isSelected());
        comboOrder.put("items", items); // 包含從資料庫解析出來的 item 和 prep_time [cite: 33]

        orderBuffer.add(comboOrder);
        JLabel l = new JLabel("<html><font color='#FFC72C'>[套餐]</font> " + comboName + " #" + id + "</html>");
        l.setForeground(TEXT_PRIMARY);
        waitPanel.add(l);
        waitPanel.revalidate();
    }

    private void addOrder(String item, int time) {
        int id = orderIdCounter++;
        JSONObject o = new JSONObject().put("id", id).put("item", item)
                                     .put("prep_time", time).put("is_takeout", takeoutBox.isSelected());
        orderBuffer.add(o);
        JLabel l = new JLabel((takeoutBox.isSelected()?"[外] ":"[內] ") + item + " #" + id);
        l.setForeground(TEXT_PRIMARY);
        waitPanel.add(l); waitPanel.revalidate();
    }

    private void switchAlgorithm() {
        String selected = (String) algoSelector.getSelectedItem();
        String mode = "FCFS";
        if (selected.contains("SJF")) mode = "SJF";
        if (selected.contains("AGING")) mode = "AGING";
        JSONObject cmd = new JSONObject().put("type", "SWITCH_MODE").put("mode", mode);
        String resp = sendToPython(cmd.toString());
        updateScheduleDisplay(new JSONArray(resp));
    }

    private void processOrders() {
        if (orderBuffer.isEmpty()) return;
        JSONObject payload = new JSONObject().put("type", "ADD_ORDER").put("data", new JSONArray(orderBuffer));
        String resp = sendToPython(payload.toString());
        updateScheduleDisplay(new JSONArray(resp));
        orderBuffer.clear();
        waitPanel.removeAll();
        waitPanel.revalidate();
        waitPanel.repaint();
        if (!isProductionRunning) startProductionLine();
    }

    private String sendToPython(String msg) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 9999), 1000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write(msg); out.newLine(); out.flush();
            String res = in.readLine();
            return (res == null) ? "[]" : res;
        } catch (Exception e) { return "[]"; }
    }

    private void updateScheduleDisplay(JSONArray arr) {
        StringBuilder sb = new StringBuilder("=== 智慧排程分析數據 ===\n\n");
        sb.append(String.format("%-5s | %-12s | %-6s | %-4s\n", "ID", "子項目", "預計", "模式"));
        sb.append("--------------------------------------------\n");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            sb.append(String.format("[#%02d] %-12s | %4ds | %s\n", 
                o.getInt("id"), 
                o.getString("item"), 
                o.getInt("expected_at"), 
                o.optBoolean("is_takeout") ? "外帶" : "內用"));
        }
        scheduleArea.setText(sb.toString());
    }

    private void startProductionLine() {
        new Thread(() -> {
            isProductionRunning = true;
            while (true) {
                String resp = sendToPython(new JSONObject().put("type", "GET_STATUS").toString());
                try {
                    JSONArray q = new JSONArray(resp);
                    if (q.length() == 0) break;
                    doWork(q.getJSONObject(0));
                } catch (Exception e) { break; }
            }
            isProductionRunning = false;
        }).start();
    }

    private void doWork(JSONObject task) {
        String name = task.getString("item");
        int seconds = task.getInt("prep_time");
        int id = task.getInt("id");

        JProgressBar bar = new JProgressBar(0, seconds);
        bar.setStringPainted(true);
        bar.setString(name + " (#" + id + ") 製作中...");
        SwingUtilities.invokeLater(() -> {
            prodPanel.removeAll();
            JPanel p = new JPanel(new BorderLayout()); 
            p.setBackground(PANEL_BG);
            p.add(bar, BorderLayout.CENTER);
            prodPanel.add(p); 
            prodPanel.revalidate();
        });

        for (int i = 0; i <= seconds; i++) {
            final int cur = i;
            SwingUtilities.invokeLater(() -> bar.setValue(cur));
            try { Thread.sleep(1000); } catch (Exception ignored) {}
        }

        String response = sendToPython(new JSONObject()
            .put("type", "FINISH_ORDER")
            .put("order_id", id)
            .put("item", name).toString());

        try {
            JSONObject status = new JSONObject(response);
            if (status.has("all_items_completed") && status.getBoolean("all_items_completed")) {
                String allNames = status.getString("order_content");
                final String timeTag = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                SwingUtilities.invokeLater(() -> {
                    addHistoryRecord("[" + timeTag + "] ✅ 訂單 #" + id + " 完成: " + allNames);
                    prodPanel.removeAll();
                    prodPanel.revalidate();
                    prodPanel.repaint();
                });
            }
        } catch (Exception e) { }
    }

    private void startStatusChecker() {
        new Thread(() -> {
            while (true) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress("localhost", 9999), 500);
                    statusLabel.setText("● 系統狀態: 已連線");
                    statusLabel.setForeground(new Color(50, 255, 100));
                } catch (Exception e) {
                    statusLabel.setText("● 系統狀態: 斷線");
                    statusLabel.setForeground(Color.RED);
                }
                try { Thread.sleep(2000); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void addHistoryRecord(String r) {
        historyList.add(r);
        if (historyList.size() > 15) historyList.removeFirst();
        StringBuilder sb = new StringBuilder(); for (String s : historyList) sb.append(s).append("\n");
        historyArea.setText(sb.toString());
    }

    private JButton createFlatButton(String t, Color b, Color f) { JButton btn = new JButton(t); btn.setBackground(b); btn.setForeground(f); return btn; }
    private JPanel createScrollPanel(JPanel p, String t) { 
        JPanel s = new JPanel(new BorderLayout());
        s.setBackground(PANEL_BG); 
        JLabel l = new JLabel(t); l.setForeground(ACCENT_GOLD); 
        s.add(l, BorderLayout.NORTH); s.add(new JScrollPane(p), BorderLayout.CENTER); return s;
    }
    private JPanel createScrollText(JTextArea a, String t) { 
        JPanel s = new JPanel(new BorderLayout());
        s.setBackground(PANEL_BG); 
        JLabel l = new JLabel(t); l.setForeground(ACCENT_GOLD); 
        s.add(l, BorderLayout.NORTH); s.add(new JScrollPane(a), BorderLayout.CENTER); return s;
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(KitchenGUI::new); }
}