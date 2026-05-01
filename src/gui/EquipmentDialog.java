package gui;

import db.DBRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class EquipmentDialog extends JDialog {
    private JTable table;
    private DefaultTableModel model;
    private Timer refreshTimer;

    public EquipmentDialog(JFrame owner) {
        super(owner, "設備總覽", false); // 非模態
        initUI();
        loadData();
        startAutoRefresh();
    }

    private void initUI() {
        setSize(600, 360);
        setLayout(new BorderLayout());

        String[] cols = {"equipmentID", "name", "歷史成本", "累積折舊", "Etype", "狀態"};
        model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        // 固定欄位順序與寬度
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(180);
        add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadData());
        bottom.add(refreshBtn);

        add(bottom, BorderLayout.SOUTH);

        // 停止 timer 當視窗關閉
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAutoRefresh();
                dispose();
            }
        });
    }

    public void loadData() {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            try {
                JSONArray arr = DBRequest.loadKitchenEquipment();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String id = o.optString("equipmentID", o.optString("equipmentId", ""));
                    String name = o.optString("name", "");
                    int cost = o.optInt("歷史成本", o.optInt("cost", 0));
                    int dep = o.optInt("累積折舊", o.optInt("depreciation", 0));
                    String etype = o.optString("Etype", o.optString("etype", ""));
                    String status = o.optString("status", "");
                    model.addRow(new Object[]{id, name, cost, dep, etype, status});
                }
            } catch (Exception ex) {
                System.err.println("載入設備失敗: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "載入設備失敗: " + ex.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private void startAutoRefresh() {
        if (refreshTimer != null) return;
        refreshTimer = new Timer(8000, e -> loadData());
        refreshTimer.setRepeats(true);
        refreshTimer.start();
    }

    private void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }
}
