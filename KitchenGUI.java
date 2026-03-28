import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class KitchenGUI {
    private JFrame frame;
    private DefaultListModel<String> listModel;

    public KitchenGUI() {
        frame = new JFrame("智慧廚房模擬系統 (Demo)");
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        listModel = new DefaultListModel<>();
        JList<String> orderList = new JList<>(listModel);
        orderList.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        
        JButton btnOrder = new JButton("發送訂單並執行 Python 排程");
        btnOrder.addActionListener(e -> sendOrdersToPython());

        frame.setLayout(new BorderLayout());
        frame.add(new JLabel(" 廚房排程隊列：", SwingConstants.LEFT), BorderLayout.NORTH);
        frame.add(new JScrollPane(orderList), BorderLayout.CENTER);
        frame.add(btnOrder, BorderLayout.SOUTH);
        
        frame.setLocationRelativeTo(null); // 視窗置中
        frame.setVisible(true);
    }

    private void sendOrdersToPython() {
        // 模擬訂單資料 (確保這行字串也是 UTF-8)
        String jsonOrders = "[{\"id\":1, \"item\":\"漢堡\", \"prep_time\":5}, {\"id\":2, \"item\":\"薯條\", \"prep_time\":2}, {\"id\":3, \"item\":\"可樂\", \"prep_time\":1}]";

        try (Socket socket = new Socket("localhost", 9999);
             OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw);
             InputStreamReader isr = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(isr)) {

            // 送出資料
            out.write(jsonOrders);
            out.newLine(); 
            out.flush(); 

            // 接收 Python 傳回的排程結果
            String response = in.readLine();
            
            // 更新 UI 介面
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                listModel.addElement("--- 來自 Python 的排程結果 ---");
                if (response != null) {
                    listModel.addElement(response);
                } else {
                    listModel.addElement("錯誤：Python 回傳空值");
                }
            });

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "連線失敗: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KitchenGUI::new);
    }
}