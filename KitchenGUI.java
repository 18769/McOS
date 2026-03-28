import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.json.*;

public class KitchenGUI {
    private JFrame frame;
    private JPanel waitPanel, prodPanel;
    private JTextArea scheduleArea, historyArea;
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private boolean isProductionRunning = false;
    private int orderIdCounter = 1;

    public KitchenGUI() {
        // 1. 先初始化組件
        frame = new JFrame("McOS 智慧廚房核心系統");
        frame.setSize(1100, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addMenuButton(btnPanel, "大麥克", 8);
        addMenuButton(btnPanel, "薯條", 3);
        addMenuButton(btnPanel, "雞塊", 5);
        
        JButton runBtn = new JButton("🚀 送出並排程");
        runBtn.setBackground(new Color(255, 199, 44));
        runBtn.addActionListener(e -> processOrders());
        btnPanel.add(runBtn);

        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        waitPanel = new JPanel(); waitPanel.setLayout(new BoxLayout(waitPanel, BoxLayout.Y_AXIS));
        mainPanel.add(createScrollPanel(waitPanel, "【1】點餐等待中 (尚未排程)"));

        prodPanel = new JPanel(); prodPanel.setLayout(new BoxLayout(prodPanel, BoxLayout.Y_AXIS));
        mainPanel.add(createScrollPanel(prodPanel, "【2】廚房製作中 (動態進度)"));

        JPanel dataPanel = new JPanel(new GridLayout(2, 1));
        scheduleArea = new JTextArea(); historyArea = new JTextArea();
        dataPanel.add(createScrollText(scheduleArea, "【3】即時排程規劃表 (來自 Python)"));
        dataPanel.add(createScrollText(historyArea, "【4】完成紀錄累積"));
        mainPanel.add(dataPanel);

        frame.setLayout(new BorderLayout());
        frame.add(btnPanel, BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);

        // 2. 顯示視窗
        frame.setVisible(true);

        // 3. 最後啟動背景執行緒
        startAutoSaveTimer();
    }

    // --- 背景存檔執行緒 ---
    private void startAutoSaveTimer() {
    Thread saveThread = new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(10000); // 每 10 秒檢查一次

                // 1. 取得目前的排程文字
                String currentSchedule = scheduleArea.getText();

                // 2. 核心判斷：確保清單有內容才紀錄
                // 我們檢查文字是否包含 "預計" 或是行數是否大於標題行（通常標題佔 3 行）
                if (currentSchedule != null && currentSchedule.contains("預計")) {
                    
                    // 3. 準備目錄
                    Path dir = Paths.get("./scheduling_record");
                    if (!Files.exists(dir)) {
                        Files.createDirectories(dir);
                    }

                    // 4. 執行存檔
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String fileName = "scheduling_" + timestamp + ".txt";
                    Path filePath = dir.resolve(fileName);

                    Files.write(filePath, currentSchedule.getBytes(StandardCharsets.UTF_8));
                    System.out.println("系統：偵測到排程項目，自動存檔成功 -> " + fileName);
                } else {
                    // 如果是空的，就不執行任何動作，等下一個 10 秒
                    // System.out.println("系統：排程清單為空，跳過本次存檔。");
                }

                } catch (InterruptedException e) {
                    break; 
                } catch (IOException e) {
                    System.err.println("存檔失敗: " + e.getMessage());
                }
            }
        });
        saveThread.setDaemon(true);
        saveThread.start();
    }

    // --- 業務邏輯方法 ---
    private void addMenuButton(JPanel p, String name, int time) {
        JButton b = new JButton(name + " (" + time + "s)");
        b.addActionListener(e -> {
            JSONObject obj = new JSONObject();
            obj.put("id", orderIdCounter++);
            obj.put("item", name);
            obj.put("prep_time", time);
            orderBuffer.add(obj);
            updateWaitPanel();
        });
        p.add(b);
    }

    private void processOrders() {
        if (orderBuffer.isEmpty()) return;
        JSONObject payload = new JSONObject();
        payload.put("type", "ADD_ORDER");
        payload.put("data", new JSONArray(orderBuffer));

        String response = sendToPython(payload.toString());
        orderBuffer.clear();
        updateWaitPanel();

        JSONArray fullSchedule = new JSONArray(response);
        updateScheduleDisplay(fullSchedule);

        if (!isProductionRunning) {
            startProductionLine();
        }
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

                JSONObject nextTask = currentQueue.getJSONObject(0);
                doWork(nextTask);
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
        bar.setString("Cooking: " + name + " (ID:" + id + ")");
        
        SwingUtilities.invokeLater(() -> {
            prodPanel.add(bar);
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
        String latestResp = sendToPython(finishReq.toString());

        SwingUtilities.invokeLater(() -> {
            prodPanel.remove(bar);
            prodPanel.revalidate();
            prodPanel.repaint();
            historyArea.append("✅ " + name + " (ID:" + id + ") 已出餐\n");
            updateScheduleDisplay(new JSONArray(latestResp));
        });
    }

    private void updateScheduleDisplay(JSONArray schedule) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder("--- 全域生產線即時規劃 (含ID) ---\n");
            sb.append(String.format("%-5s | %-10s | %-10s\n", "順序", "餐點項目", "預計等待"));
            sb.append("------------------------------------------\n");

            for (int i = 0; i < schedule.length(); i++) {
                JSONObject o = schedule.getJSONObject(i);
                int id = o.optInt("id", 0);
                String item = o.optString("item", "未知");
                int wait = o.optInt("expected_at", 0);
                sb.append(String.format("[%03d]  %-10s | 預計 %2ds 完成\n", id, item, wait));
            }
            scheduleArea.setText(sb.toString());
        });
    }

    // --- UI 輔助方法 ---
    private JScrollPane createScrollPanel(JPanel p, String title) {
        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(BorderFactory.createTitledBorder(title));
        return sp;
    }
    private JScrollPane createScrollText(JTextArea ta, String title) {
        ta.setEditable(false); ta.setBackground(new Color(245, 245, 245));
        JScrollPane sp = new JScrollPane(ta); sp.setBorder(BorderFactory.createTitledBorder(title));
        return sp;
    }
    private void updateWaitPanel() {
        waitPanel.removeAll();
        for (JSONObject o : orderBuffer) waitPanel.add(new JLabel("⏳ " + o.getString("item")));
        waitPanel.revalidate(); waitPanel.repaint();
    }
    private String sendToPython(String payload) {
        try (Socket s = new Socket("localhost", 9999);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.write(payload); out.newLine(); out.flush();
            return in.readLine();
        } catch (Exception e) { return "[]"; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KitchenGUI::new);
    }
}