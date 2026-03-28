import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class KitchenGUI {
    private JFrame frame;
    private JPanel waitPanel, prodPanel;
    private JTextArea scheduleArea, historyArea;
    private ArrayList<JSONObject> orderBuffer = new ArrayList<>();
    private boolean isProductionRunning = false;
    private int orderIdCounter = 1; // 給每筆訂單唯一的 ID

    public KitchenGUI() {
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
        frame.setVisible(true);
    }

    private void addMenuButton(JPanel p, String name, int time) {
        JButton b = new JButton(name + " (" + time + "s)");
        b.addActionListener(e -> {
            JSONObject obj = new JSONObject();
            obj.put("id", orderIdCounter++); // 增加唯一 ID
            obj.put("item", name);
            obj.put("prep_time", time);
            orderBuffer.add(obj);
            updateWaitPanel();
        });
        p.add(b);
    }

    private void processOrders() {
        if (orderBuffer.isEmpty()) return;

        // 封裝協議：告訴 Python 我要新增訂單
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

    // 啟動生產線：循環向 Python 拿最新第一筆來做
    private void startProductionLine() {
        new Thread(() -> {
            isProductionRunning = true;
            while (true) {
                // 向 Python 詢問目前排在第一位的是誰
                JSONObject request = new JSONObject();
                request.put("type", "GET_STATUS");
                String resp = sendToPython(request.toString());
                JSONArray currentQueue = new JSONArray(resp);

                if (currentQueue.length() == 0) break;

                // 取得目前排第一的任務進行製作
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
        bar.setString("Cooking: " + name);
        
        SwingUtilities.invokeLater(() -> {
            prodPanel.add(bar);
            prodPanel.revalidate();
        });

        for (int i = 0; i <= seconds; i++) {
            final int current = i;
            SwingUtilities.invokeLater(() -> bar.setValue(current));
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
        }

        // 製作完成：通知 Python 移除此 ID
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
            scheduleArea.setText("--- 全域生產線即時規劃 ---\n");
            for (int i = 0; i < schedule.length(); i++) {
                JSONObject o = schedule.getJSONObject(i);
                scheduleArea.append(String.format("[%d] %s | 預計 %ds\n", 
                    (i+1), o.getString("item"), o.optInt("expected_at", 0)));
            }
        });
    }

    // --- 以下為 UI 輔助與 Socket 方法 (保持不變) ---
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
    public static void main(String[] args) { SwingUtilities.invokeLater(KitchenGUI::new); }
}