package gui;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.json.*;

public class KitchenGUI {
    private JFrame frame;
    private JPanel waitPanel, prodPanel;
    private JTextArea scheduleArea, historyArea;
    private JCheckBox takeoutBox;
    private JLabel statusLabel, modeLabel; 
    private JButton scriptBtn;
    
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private LinkedList<String> historyList = new LinkedList<>();
    private LinkedHashMap<String, Integer> mealPrepTimes = new LinkedHashMap<>();
    private Thread scriptThread = null;
    private int orderIdCounter = 1;
    private boolean isProductionRunning = false;
    private boolean isScriptRunning = false;
    private String currentModeName = "標準 (FCFS)"; 

    private static final Color BG_DARK = new Color(24, 24, 27);
    private static final Color PANEL_BG = new Color(39, 39, 42);
    private static final Color ACCENT_GOLD = new Color(255, 199, 44);
    private static final Color TEXT_PRIMARY = new Color(250, 250, 250);
    private static final Color TEXT_SECONDARY = new Color(161, 161, 170);

    public KitchenGUI() {
        loadMeals();
        frame = new JFrame("McOS 智慧廚房 - 專業出餐系統");
        frame.setSize(1200, 750);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG_DARK);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(18, 18, 20));
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

        modeLabel = new JLabel(" | 模式: " + currentModeName);
        modeLabel.setForeground(ACCENT_GOLD);
        modeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        actionPanel.add(modeLabel);

        takeoutBox = new JCheckBox("外帶模式");
        takeoutBox.setForeground(TEXT_PRIMARY);
        takeoutBox.setBackground(new Color(18, 18, 20));
        takeoutBox.setFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
        actionPanel.add(takeoutBox);

        scriptBtn = createFlatButton("📂 載入腳本", new Color(0, 120, 215), Color.WHITE);
        scriptBtn.addActionListener(e -> {
            if (!isScriptRunning) openScriptFileChooser();
            else if (scriptThread != null) scriptThread.interrupt();
        });
        actionPanel.add(scriptBtn);

        JButton clearBtn = createFlatButton("🗑️ 清空暫存", new Color(220, 53, 69), Color.WHITE);
        clearBtn.addActionListener(e -> { orderBuffer.clear(); updateWaitPanel(); });
        actionPanel.add(clearBtn);

        JButton runBtn = createFlatButton("🚀 送出排程", ACCENT_GOLD, Color.BLACK);
        runBtn.addActionListener(e -> processOrders());
        actionPanel.add(runBtn);
        topBar.add(actionPanel, BorderLayout.EAST);

        JPanel mainContent = new JPanel(new BorderLayout(15, 0));
        mainContent.setBackground(BG_DARK);
        mainContent.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel menuSection = createSectionPanel("點餐選項");
        menuSection.setPreferredSize(new Dimension(300, 0));
        JPanel gridPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        gridPanel.setBackground(PANEL_BG);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (Map.Entry<String, Integer> entry : mealPrepTimes.entrySet()) { addMenuButton(gridPanel, entry.getKey(), entry.getValue()); }
        menuSection.add(createModernScrollPane(gridPanel), BorderLayout.CENTER);
        mainContent.add(menuSection, BorderLayout.WEST);

        JPanel monitorsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        monitorsPanel.setBackground(BG_DARK);
        waitPanel = new JPanel(); waitPanel.setLayout(new BoxLayout(waitPanel, BoxLayout.Y_AXIS)); waitPanel.setBackground(PANEL_BG);
        monitorsPanel.add(createScrollPanel(waitPanel, "【1】 等待中"));
        prodPanel = new JPanel(); prodPanel.setLayout(new BoxLayout(prodPanel, BoxLayout.Y_AXIS)); prodPanel.setBackground(PANEL_BG);
        monitorsPanel.add(createScrollPanel(prodPanel, "【2】 廚房製作"));
        JPanel dataPanel = new JPanel(new GridLayout(2, 1, 0, 15)); dataPanel.setBackground(BG_DARK);
        scheduleArea = new JTextArea(); historyArea = new JTextArea();
        dataPanel.add(createScrollText(scheduleArea, "【3】 智慧排程")); dataPanel.add(createScrollText(historyArea, "【4】 完成紀錄"));
        monitorsPanel.add(dataPanel);
        mainContent.add(monitorsPanel, BorderLayout.CENTER);

        frame.add(topBar, BorderLayout.NORTH);
        frame.add(mainContent, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        startStatusChecker();
    }

    private void handlePythonResponse(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("[]")) return;
        try {
            JSONObject resp = new JSONObject(raw);
            String mode = resp.optString("mode", "NORMAL");
            currentModeName = mode.equals("NORMAL") ? "標準 (FCFS)" : "進階 (SJF+Aging)";
            SwingUtilities.invokeLater(() -> {
                modeLabel.setText(" | 模式: " + currentModeName);
                updateScheduleDisplay(resp.getJSONArray("orders"));
            });
        } catch (Exception e) {
            try { updateScheduleDisplay(new JSONArray(raw)); } catch (Exception ignored) {}
        }
    }

    private void processOrders() {
        if (orderBuffer.isEmpty()) return;
        JSONObject payload = new JSONObject().put("type", "ADD_ORDER").put("data", new JSONArray(orderBuffer));
        handlePythonResponse(sendToPython(payload.toString()));
        orderBuffer.clear(); updateWaitPanel();
        if (!isProductionRunning) startProductionLine();
    }

    private void startProductionLine() {
        new Thread(() -> {
            isProductionRunning = true;
            while (true) {
                String resp = sendToPython(new JSONObject().put("type", "GET_STATUS").toString());
                try {
                    JSONObject r = new JSONObject(resp);
                    JSONArray q = r.getJSONArray("orders");
                    if (q.length() == 0) break;
                    doWork(q.getJSONObject(0));
                } catch (Exception e) { 
                    try {
                        JSONArray q = new JSONArray(resp);
                        if (q.length() == 0) break;
                        doWork(q.getJSONObject(0));
                    } catch (Exception ex) { break; }
                }
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
        bar.setString((isTakeout ? "外帶: " : "內用: ") + name + " (#" + id + ")");
        bar.setForeground(isTakeout ? new Color(255, 140, 0) : new Color(34, 199, 100));
        
        SwingUtilities.invokeLater(() -> {
            JPanel p = new JPanel(new BorderLayout()); p.setBackground(PANEL_BG);
            p.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            p.add(bar, BorderLayout.CENTER);
            prodPanel.add(p); prodPanel.revalidate();
        });

        for (int i = 0; i <= seconds; i++) {
            final int cur = i; SwingUtilities.invokeLater(() -> bar.setValue(cur));
            try { Thread.sleep(1000); } catch (Exception ignored) {}
        }

        JSONObject fin = new JSONObject().put("type", "FINISH_ORDER").put("order_id", id);
        handlePythonResponse(sendToPython(fin.toString()));

        SwingUtilities.invokeLater(() -> {
            for (Component c : prodPanel.getComponents()) {
                if (c instanceof JPanel && ((JPanel)c).getComponent(0) == bar) { prodPanel.remove(c); break; }
            }
            prodPanel.revalidate(); prodPanel.repaint();
            addHistoryRecord("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] OK " + name + " (#" + id + ") 完成");
        });
    }

    private String sendToPython(String msg) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 9999), 1000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write(msg); out.newLine(); out.flush();
            String response = in.readLine();
            return (response == null) ? "[]" : response;
        } catch (Exception e) {
            return "[]";
        }
    }

    private void updateScheduleDisplay(JSONArray arr) {
        StringBuilder sb = new StringBuilder("=== 智慧排程 ===\n\n");
        sb.append(String.format("%-4s | %-12s | %-5s | %-4s\n", "ID", "項目", "預計", "類型"));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            sb.append(String.format("[#%02d] %-12s | %3ds | %s\n", o.getInt("id"), o.getString("item"), o.getInt("expected_at"), o.optBoolean("is_takeout") ? "[外帶]" : "[內用]"));
        }
        scheduleArea.setText(sb.toString());
    }

    private void addMenuButton(JPanel p, String name, int time) {
        JButton b = new JButton("<html>" + name + "<br>" + time + "s</html>");
        b.setBackground(PANEL_BG); b.setForeground(TEXT_PRIMARY); b.setFocusPainted(false);
        b.addActionListener(e -> addOrderToBuffer(name, time, 1, takeoutBox.isSelected()));
        p.add(b);
    }

    private void addOrderToBuffer(String item, int t, int count, boolean to) {
        for (int i = 0; i < count; i++) {
            orderBuffer.add(new JSONObject().put("id", orderIdCounter++).put("item", item).put("prep_time", t).put("is_takeout", to));
        }
        updateWaitPanel();
    }

    private void updateWaitPanel() {
        waitPanel.removeAll();
        for (JSONObject o : orderBuffer) {
            JLabel l = new JLabel((o.optBoolean("is_takeout") ? "[外帶] " : "[內用] ") + o.getString("item"));
            l.setForeground(o.optBoolean("is_takeout") ? ACCENT_GOLD : TEXT_PRIMARY);
            l.setFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
            waitPanel.add(l);
        }
        waitPanel.revalidate(); waitPanel.repaint();
    }

    private void addHistoryRecord(String r) {
        historyList.add(r); if (historyList.size() > 10) historyList.removeFirst();
        StringBuilder sb = new StringBuilder(); for (String s : historyList) sb.append(s).append("\n");
        historyArea.setText(sb.toString());
    }

    private void startStatusChecker() {
        new Thread(() -> {
            while (true) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress("localhost", 9999), 500);
                    SwingUtilities.invokeLater(() -> { statusLabel.setText("● 系統狀態: 已連線"); statusLabel.setForeground(new Color(50, 255, 100)); });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> { statusLabel.setText("● 系統狀態: 斷線中"); statusLabel.setForeground(new Color(255, 80, 80)); });
                }
                try { Thread.sleep(3000); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void loadMeals() { 
        mealPrepTimes.put("大麥克", 8); mealPrepTimes.put("薯條", 3); 
        mealPrepTimes.put("可樂", 1); mealPrepTimes.put("雞塊", 5); 
        mealPrepTimes.put("玉米湯", 2); mealPrepTimes.put("蘋果派", 4); 
    }
    private JButton createFlatButton(String t, Color b, Color f) { JButton btn = new JButton(t); btn.setBackground(b); btn.setForeground(f); return btn; }
    private JPanel createSectionPanel(String t) { JPanel p = new JPanel(new BorderLayout()); p.setBackground(PANEL_BG); JLabel l = new JLabel(t); l.setForeground(ACCENT_GOLD); p.add(l, BorderLayout.NORTH); return p; }
    private JScrollPane createModernScrollPane(Component c) { return new JScrollPane(c); }
    private JPanel createScrollPanel(JPanel p, String t) { JPanel s = createSectionPanel(t); s.add(new JScrollPane(p)); return s; }
    private JPanel createScrollText(JTextArea a, String t) { JPanel s = createSectionPanel(t); s.add(new JScrollPane(a)); return s; }
    private void openScriptFileChooser() { 
        JFileChooser jfc = new JFileChooser(new File("./scripts")); 
        if (jfc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            executeScriptFile(jfc.getSelectedFile()); 
        }
    }
    private void executeScriptFile(File file) {
        isScriptRunning = true;
        scriptThread = new Thread(() -> {
            try { 
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                executeSteps(new JSONObject(content).getJSONArray("steps")); 
            } catch (Exception e) { e.printStackTrace(); }
            finally { isScriptRunning = false; }
        });
        scriptThread.start();
    }
    private void executeSteps(JSONArray steps) throws Exception {
        for (int i = 0; i < steps.length(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            JSONObject step = steps.getJSONObject(i);
            String action = step.optString("action", "");
            switch (action) {
                case "SWITCH_MODE":
                    sendToPython(step.toString());
                    handlePythonResponse(sendToPython(new JSONObject().put("type", "GET_STATUS").toString()));
                    break;
                case "SET_MODE":
                    SwingUtilities.invokeLater(() -> takeoutBox.setSelected(step.optBoolean("takeout", false)));
                    break;
                case "ADD_ORDER":
                    SwingUtilities.invokeLater(() -> addOrderToBuffer(step.getString("item"), step.optInt("prep_time", 5), step.optInt("count", 1), takeoutBox.isSelected()));
                    break;
                case "SEND_ORDERS":
                    SwingUtilities.invokeLater(this::processOrders);
                    break;
                case "WAIT":
                    Thread.sleep(step.optInt("seconds", 1) * 1000L);
                    break;
            }
            Thread.sleep(200);
        }
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(KitchenGUI::new); }
}