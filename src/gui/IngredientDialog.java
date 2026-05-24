package gui;

import db.DBRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class IngredientDialog extends JDialog {
    private JTable table;
    private DefaultTableModel model;
    private Timer refreshTimer;

    public IngredientDialog(JFrame owner) {
        super(owner, "庫存與採買", false); // 非模態
        initUI();
        loadData();
        startAutoRefresh();
    }

    private void initUI() {
        setSize(600, 360);
        setLayout(new BorderLayout());

        String[] cols = {"ing_id", "ing_name", "stock_qty", "unit"};
        model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton purchaseBtn = new JButton("採買");
    purchaseBtn.addActionListener(e -> handlePurchase());
    bottom.add(purchaseBtn);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadData());
        bottom.add(refreshBtn);
        add(bottom, BorderLayout.SOUTH);

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
                JSONArray arr = DBRequest.loadIngredients();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String id = o.optString("ing_id", o.optString("ingId", ""));
                    String name = o.optString("ing_name", o.optString("ingName", ""));
                    String qty = o.optString("stock_qty", o.optString("stockQty", ""));
                    String unit = o.optString("unit", "");
                    model.addRow(new Object[]{id, name, qty, unit});
                }
            } catch (Exception ex) {
                System.err.println("載入原料失敗: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "載入原料失敗: " + ex.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE));
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

    private void handlePurchase() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "請先選擇原料", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String ingId = String.valueOf(model.getValueAt(row, 0));
        String ingName = String.valueOf(model.getValueAt(row, 1));
        String qtyInput = JOptionPane.showInputDialog(this, "採買數量 (" + ingName + "):", "0");
        if (qtyInput == null || qtyInput.trim().isEmpty()) {
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(qtyInput.trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "數量格式錯誤", "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "數量必須大於 0", "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    DBRequest.purchaseIngredient(ingId, qty);
                } catch (Exception ex) {
                    System.err.println("採買失敗: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            IngredientDialog.this,
                            "採買失敗: " + ex.getMessage(),
                            "錯誤",
                            JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                loadData();
            }
        }.execute();
    }
}