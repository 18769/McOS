import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
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
    private JLabel statusLabel;
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private boolean isProductionRunning = false;
    private int orderIdCounter = 1;

    public KitchenGUI() {
        // --- 1. 全域外觀設定 ---
        Font titleFont = new Font("Microsoft JhengHei", Font.BOLD, 18);
        Color mcdGold = new Color(255, 199, 44);

        UIManager.put("TitledBorder.font", titleFont);
        UIManager.put("TitledBorder.titleColor", mcdGold);
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(new Color(120, 120, 120), 1));

        frame = new JFrame("McOS 智慧廚房 - 專業出餐系統");
        frame.setSize(1200, 750);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(new Color(33, 33, 33));

        // --- 2. 頂部控制區 ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.setBackground(new Color(45, 45, 45));
        btnPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, mcdGold));

        takeoutBox = new JCheckBox("外帶打包模式");
        takeoutBox.setForeground(Color.WHITE);
        takeoutBox.setBackground(new Color(45, 45, 45));
        takeoutBox.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        btnPanel.add(takeoutBox);
        
        btnPanel.add(new JSeparator(JSeparator.VERTICAL));

        addMenuButton(btnPanel, "大麥克", 8);
        addMenuButton(btnPanel, "薯條", 3);
        addMenuButton(btnPanel, "雞塊", 5);
        addMenuButton(btnPanel, "大麥克套餐", 0);
        addMenuButton(btnPanel, "雞塊套餐", 0);

        JButton clearBtn = new JButton("🗑️ 清空暫存");
        clearBtn.setBackground(new Color(255, 100, 100));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.addActionListener(e -> {
            orderBuffer.clear();
            updateWaitPanel();
        });
        btnPanel.add(clearBtn);

        JButton runBtn = new JButton("🚀 送出並排程");
        runBtn.setBackground(mcdGold);
        runBtn.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        runBtn.addActionListener(e -> processOrders());
        btnPanel.add(runBtn);

        statusLabel = new JLabel("● 後端狀態: 檢測中");
        statusLabel.setForeground(Color.GRAY);
        btnPanel.add(Box.createHorizontalStrut(20));
        btnPanel.add(statusLabel);

        // --- 3. 主面板配置 ---
        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        mainPanel.setBackground(new Color(33, 33, 33));

        waitPanel = new JPanel(); 
        waitPanel.setLayout(new BoxLayout(waitPanel, BoxLayout.Y_AXIS));
        mainPanel.add(createScrollPanel(waitPanel, "【1】點餐等待中"));

        prodPanel = new JPanel(); 
        prodPanel.setLayout(new BoxLayout(prodPanel, BoxLayout.Y_AXIS));
        mainPanel.add(createScrollPanel(prodPanel, "【2】廚房製作中"));

        JPanel dataPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        dataPanel.setBackground(new Color(33, 33, 33));
        
        // 初始化文本區，強制設定字體為微軟正黑體以支援中文
        scheduleArea = new JTextArea();
        historyArea = new JTextArea();
        
        dataPanel.add(createScrollText(scheduleArea, "【3】智慧排程規劃"));
        dataPanel.add(createScrollText(historyArea, "【4】完成紀錄累積"));
        mainPanel.add(dataPanel);

        frame.setLayout(new BorderLayout());
        frame.add(btnPanel, BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        startAutoSaveTimer();
        startStatusChecker();
    }

    private void startStatusChecker() {
        Thread t = new Thread(() -> {
            while (true) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress("localhost", 9999), 500);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("● 後端狀態: 已連線");
                        statusLabel.setForeground(new Color(50, 255, 50));
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("● 後端狀態: 斷線中");
                        statusLabel.setForeground(new Color(255, 50, 50));
                    });
                }
                try { Thread.sleep(3000); } catch (InterruptedException e) {}
            }
        });
        t.setDaemon(true); t.start();
    }

    private void addMenuButton(JPanel p, String name, int time) {
        JButton b = new JButton(name + (time > 0 ? " (" + time + "s)" : " [🍱]"));
        b.setFocusPainted(false);
        b.addActionListener(e -> {
            JSONObject obj = new JSONObject();
            obj.put("id", orderIdCounter++);
            obj.put("item", name);
            obj.put("prep_time", time);
            obj.put("is_takeout", takeoutBox.isSelected());
            orderBuffer.add(obj);
            updateWaitPanel();
        });
        p.add(b);
    }

    private void updateWaitPanel() {
        waitPanel.removeAll();
        for (JSONObject o : orderBuffer) {
            boolean isTakeout = o.optBoolean("is_takeout", false);
            String tag = isTakeout ? "[外帶]" : "[內用]";
            JLabel label = new JLabel(" " + tag + " " + o.getString("item"));
            label.setForeground(isTakeout ? new Color(255, 140, 0) : Color.WHITE);
            label.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
            waitPanel.add(label);
            waitPanel.add(Box.createVerticalStrut(5));
        }
        waitPanel.revalidate(); waitPanel.repaint();
    }

    private void processOrders() {
        if (orderBuffer.isEmpty()) return;
        JSONObject payload = new JSONObject();
        payload.put("type", "ADD_ORDER");
        payload.put("data", new JSONArray(orderBuffer));
        String response = sendToPython(payload.toString());
        orderBuffer.clear();
        updateWaitPanel();
        updateScheduleDisplay(new JSONArray(response));
        if (!isProductionRunning) startProductionLine();
    }

    private void startProductionLine() {
        new Thread(() -> {
            isProductionRunning = true;
            while (true) {
                JSONObject request = new JSONObject();
                request.put("type", "GET_STATUS");
                String resp = sendToPython(request.toString());
                JSONArray currentQueue = new JSONArray(resp);
                if (currentQueue.length() == 0) break;
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
        bar.setBackground(new Color(60, 60, 60));
        bar.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        
        if (isTakeout) {
            bar.setForeground(new Color(255, 140, 0));
            bar.setString("送：[外帶] " + name + " (ID:" + id + ")");
        } else {
            bar.setForeground(new Color(34, 139, 34));
            bar.setString("做：[內用] " + name + " (ID:" + id + ")");
        }
        
        SwingUtilities.invokeLater(() -> {
            prodPanel.add(bar);
            prodPanel.add(Box.createVerticalStrut(10));
            prodPanel.revalidate();
        });

        for (int i = 0; i <= seconds; i++) {
            final int current = i;
            SwingUtilities.invokeLater(() -> bar.setValue(current));
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
        }

        JSONObject finishReq = new JSONObject();
        finishReq.put("type", "FINISH_ORDER");
        finishReq.put("order_id", id);
        finishReq.put("item", name); 
        String latestResp = sendToPython(finishReq.toString());
        
        SwingUtilities.invokeLater(() -> {
            prodPanel.remove(bar);
            prodPanel.revalidate();
            prodPanel.repaint();
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            historyArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
            historyArea.append("[" + time + "] OK " + name + " (ID:" + id + ") 已完成\n");
            updateScheduleDisplay(new JSONArray(latestResp));
        });
    }

    private void updateScheduleDisplay(JSONArray schedule) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder("--- McOS 智慧排程規劃 ---\n");
            sb.append(String.format("%-4s | %-15s | %-8s | %-4s\n", "ID", "項目", "預計完成", "類型"));
            for (int i = 0; i < schedule.length(); i++) {
                JSONObject o = schedule.getJSONObject(i);
                String type = o.optBoolean("is_takeout") ? "[外帶]" : "[內用]";
                sb.append(String.format("[%03d] %-15s | %3ds | %s\n", 
                    o.getInt("id"), o.getString("item"), o.getInt("expected_at"), type));
            }
            scheduleArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
            scheduleArea.setText(sb.toString());
        });
    }

    private JScrollPane createScrollPanel(JPanel p, String title) {
        p.setBackground(new Color(33, 33, 33));
        JScrollPane sp = new JScrollPane(p);
        sp.getViewport().setBackground(new Color(33, 33, 33));
        sp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(255, 199, 44), 2), 
            title, TitledBorder.LEFT, TitledBorder.TOP, 
            new Font("Microsoft JhengHei", Font.BOLD, 18), new Color(255, 199, 44)));
        return sp;
    }

    private JScrollPane createScrollText(JTextArea ta, String title) {
        ta.setEditable(false); 
        ta.setBackground(new Color(25, 25, 25));
        ta.setForeground(new Color(220, 220, 220));
        // 重要：改用 Microsoft JhengHei 解決豆腐塊問題
        ta.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14)); 
        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(255, 199, 44), 2), 
            title, TitledBorder.LEFT, TitledBorder.TOP, 
            new Font("Microsoft JhengHei", Font.BOLD, 18), new Color(255, 199, 44)));
        return sp;
    }

    private String sendToPython(String payload) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 9999), 1000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write(payload); out.newLine(); out.flush();
            return in.readLine();
        } catch (Exception e) { return "[]"; }
    }

    private void startAutoSaveTimer() {
    Thread t = new Thread(() -> {
        while(true) {
            try {
                Thread.sleep(10000L); // 每10秒存一次
                String content = scheduleArea.getText();
                
                // 檢查是否有有效內容才存檔
                if (content != null && content.contains("ID")) {
                    // --- 1. 定義資料夾名稱 ---
                    Path dir = Paths.get("./scheduling_record"); // 確保這裡跟資料夾名稱一模一樣
                    if (!Files.exists(dir)) Files.createDirectories(dir);
                    
                    // --- 2. 檔案名稱加上時間戳記 ---
                    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    Path filePath = dir.resolve("order_backup_" + ts + ".txt");
                    
                    // --- 3. 執行寫入 ---
                    Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                // 防止程式因為存檔失敗而崩潰
            }
        }
    });
    t.setDaemon(true); 
    t.start();
}
    public static void main(String[] args) { 
        SwingUtilities.invokeLater(KitchenGUI::new); 
    }
}