package gui;

import db.DBRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * 餐點管理 GUI
 * 
 * 功能：查看、新增、編輯、刪除餐點
 * 連接方式：Java → HTTP → PHP → MySQL
 * 
 * 使用方法：
 *   MealManagerGUI frame = new MealManagerGUI();
 *   frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 *   frame.setVisible(true);
 */
public class MealManagerGUI extends JFrame {
    private JTable mealTable;
    private DefaultTableModel tableModel;
    private JTextField mealNameField;
    private JSpinner prepTimeSpinner;
    private JButton addBtn, editBtn, deleteBtn, refreshBtn;
    private JLabel statusLabel;
    
    public MealManagerGUI() {
        setTitle("McOS - 餐點管理系統");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        
        // 主容器
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // ===== 標題 =====
        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("🍔 餐點管理系統");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
        titlePanel.add(titleLabel);
        
        // ===== 輸入面板 =====
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder("新增/編輯餐點"));
        
        inputPanel.add(new JLabel("餐點名稱:"));
        mealNameField = new JTextField(15);
        inputPanel.add(mealNameField);
        
        inputPanel.add(new JLabel("準備時間(秒):"));
        SpinnerModel spinnerModel = new SpinnerNumberModel(5, 0, 300, 1);
        prepTimeSpinner = new JSpinner(spinnerModel);
        prepTimeSpinner.setPreferredSize(new Dimension(80, 25));
        inputPanel.add(prepTimeSpinner);
        
        addBtn = new JButton("新增");
        addBtn.addActionListener(e -> addMeal());
        inputPanel.add(addBtn);
        
        editBtn = new JButton("編輯");
        editBtn.addActionListener(e -> editMeal());
        inputPanel.add(editBtn);
        
        deleteBtn = new JButton("刪除");
        deleteBtn.addActionListener(e -> deleteMeal());
        inputPanel.add(deleteBtn);
        
        refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> loadMeals());
        inputPanel.add(refreshBtn);
        
        // ===== 表格面板 =====
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("餐點列表"));
        
        String[] columnNames = {"ID", "餐點名稱", "準備時間(秒)", "建立時間", "更新時間"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;  // 表格不可直接編輯，只能透過按鈕
            }
        };
        
        mealTable = new JTable(tableModel);
        mealTable.setRowHeight(25);
        mealTable.getSelectionModel().addListSelectionListener(e -> updateInputFields());
        
        JScrollPane scrollPane = new JScrollPane(mealTable);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        
        // ===== 狀態面板 =====
        JPanel statusPanel = new JPanel(new BorderLayout());
        JPanel leftStatus = new JPanel();
        statusLabel = new JLabel("就緒");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        leftStatus.add(statusLabel);
        
        JPanel rightStatus = new JPanel();
        JButton maintenanceBtn = new JButton("🔧 維護");
        maintenanceBtn.addActionListener(e -> showMaintenanceDialog());
        rightStatus.add(maintenanceBtn);
        
        statusPanel.add(leftStatus, BorderLayout.WEST);
        statusPanel.add(rightStatus, BorderLayout.EAST);
        
        // ===== 組合布局 =====
        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(inputPanel, BorderLayout.BEFORE_FIRST_LINE);
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // 初始化
        loadMeals();
    }
    
    /**
     * 載入所有餐點
     */
    private void loadMeals() {
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() throws Exception {
                return DBRequest.queryMeals();
            }
            
            @Override
            protected void done() {
                try {
                    JSONArray meals = get();
                    tableModel.setRowCount(0);
                    
                    for (int i = 0; i < meals.length(); i++) {
                        JSONObject meal = meals.getJSONObject(i);
                        tableModel.addRow(new Object[]{
                            meal.getInt("meal_id"),
                            meal.getString("meal_name"),
                            meal.getInt("prep_time"),
                            meal.optString("created_at", "-"),
                            meal.optString("updated_at", "-")
                        });
                    }
                    
                    statusLabel.setText("✓ 成功載入 " + meals.length() + " 個餐點");
                } catch (Exception ex) {
                    statusLabel.setText("✗ 載入失敗: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "載入失敗: " + ex.getMessage(), 
                        "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    /**
     * 新增餐點
     */
    private void addMeal() {
        String name = mealNameField.getText().trim();
        int prepTime = (Integer) prepTimeSpinner.getValue();
        
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入餐點名稱", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 使用 crud.php 新增
                String url = "http://120.107.152.110/~a0303/DB/crud.php" +
                    "?action=insert&table=McOS_meal" +
                    "&meal_name=" + java.net.URLEncoder.encode(name, "UTF-8") +
                    "&prep_time=" + prepTime;
                
                JSONObject response = DBRequest.httpGet(url);
                
                if (!"success".equals(response.getString("status"))) {
                    throw new Exception(response.getString("message"));
                }
                
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    mealNameField.setText("");
                    prepTimeSpinner.setValue(5);
                    statusLabel.setText("✓ 餐點 '" + name + "' 新增成功");
                    loadMeals();
                    
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "餐點 '" + name + "' 已新增", 
                        "成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("✗ 新增失敗: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "新增失敗: " + ex.getMessage(), 
                        "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    /**
     * 編輯選中的餐點
     */
    private void editMeal() {
        int selectedRow = mealTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選擇要編輯的餐點", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int mealId = (Integer) tableModel.getValueAt(selectedRow, 0);
        String name = mealNameField.getText().trim();
        int prepTime = (Integer) prepTimeSpinner.getValue();
        
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入餐點名稱", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String url = "http://120.107.152.110/~a0303/DB/crud.php" +
                    "?action=update&table=McOS_meal&id=" + mealId +
                    "&meal_name=" + java.net.URLEncoder.encode(name, "UTF-8") +
                    "&prep_time=" + prepTime;
                
                JSONObject response = DBRequest.httpGet(url);
                
                if (!"success".equals(response.getString("status"))) {
                    throw new Exception(response.getString("message"));
                }
                
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    mealNameField.setText("");
                    prepTimeSpinner.setValue(5);
                    statusLabel.setText("✓ 餐點已更新");
                    loadMeals();
                    
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "餐點已成功更新", 
                        "成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("✗ 編輯失敗: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "編輯失敗: " + ex.getMessage(), 
                        "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    /**
     * 刪除選中的餐點
     */
    private void deleteMeal() {
        int selectedRow = mealTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "請先選擇要刪除的餐點", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int mealId = (Integer) tableModel.getValueAt(selectedRow, 0);
        String mealName = (String) tableModel.getValueAt(selectedRow, 1);
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "確定要刪除餐點 '" + mealName + "' 嗎？", 
            "確認刪除", JOptionPane.YES_NO_OPTION);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String url = "http://120.107.152.110/~a0303/DB/crud.php" +
                    "?action=delete&table=McOS_meal&id=" + mealId;
                
                JSONObject response = DBRequest.httpGet(url);
                
                if (!"success".equals(response.getString("status"))) {
                    throw new Exception(response.getString("message"));
                }
                
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("✓ 餐點已刪除");
                    loadMeals();
                    
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "餐點已成功刪除", 
                        "成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("✗ 刪除失敗: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "刪除失敗: " + ex.getMessage(), 
                        "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    /**
     * 當表格選擇改變時，更新輸入欄位
     */
    private void updateInputFields() {
        int selectedRow = mealTable.getSelectedRow();
        if (selectedRow == -1) {
            mealNameField.setText("");
            prepTimeSpinner.setValue(5);
            return;
        }
        
        String mealName = (String) tableModel.getValueAt(selectedRow, 1);
        int prepTime = (Integer) tableModel.getValueAt(selectedRow, 2);
        
        mealNameField.setText(mealName);
        prepTimeSpinner.setValue(prepTime);
    }
    
    /**
     * 顯示維護對話框
     */
    private void showMaintenanceDialog() {
        JDialog dialog = new JDialog(this, "資料庫維護", true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("🔧 資料庫維護工具");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10));
        
        JButton statusBtn = new JButton("查看 AUTO_INCREMENT 狀態");
        statusBtn.addActionListener(e -> checkAutoIncrementStatus());
        panel.add(statusBtn);
        
        panel.add(Box.createVerticalStrut(10));
        
        JButton resetBtn = new JButton("重置 AUTO_INCREMENT");
        resetBtn.addActionListener(e -> resetAutoIncrement());
        panel.add(resetBtn);
        
        panel.add(Box.createVerticalStrut(10));
        
        JLabel infoLabel = new JLabel("<html>" +
            "當新增資料時 ID 跳號，可能原因：<br>" +
            "• 之前插入過更多資料後又刪除<br>" +
            "• AUTO_INCREMENT 沒有重置<br>" +
            "<br>" +
            "點擊「重置」可自動修復此問題" +
            "</html>");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        panel.add(infoLabel);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    /**
     * 查看 AUTO_INCREMENT 狀態
     */
    private void checkAutoIncrementStatus() {
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                String url = "http://120.107.152.110/~a0303/DB/crud.php?action=status&table=McOS_meal";
                return DBRequest.httpGet(url);
            }
            
            @Override
            protected void done() {
                try {
                    JSONObject response = get();
                    
                    int currentAutoIncrement = response.getInt("current_auto_increment");
                    int totalRecords = response.getInt("total_records");
                    int maxId = response.getInt("max_id");
                    int recommended = response.getInt("recommended_auto_increment");
                    
                    String message = String.format(
                        "當前 AUTO_INCREMENT: %d\n" +
                        "總記錄數: %d\n" +
                        "最大 ID: %d\n" +
                        "建議的下一個 ID: %d\n\n" +
                        "如果下一個 ID 不連續，點擊「重置」修復",
                        currentAutoIncrement, totalRecords, maxId, recommended
                    );
                    
                    JOptionPane.showMessageDialog(MealManagerGUI.this, message, 
                        "AUTO_INCREMENT 狀態", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "查詢失敗: " + ex.getMessage(), 
                        "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    /**
     * 重置 AUTO_INCREMENT
     */
    private void resetAutoIncrement() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "確定要重置 AUTO_INCREMENT 嗎？\n這將使下一個新增的 ID 連續", 
            "確認重置", JOptionPane.YES_NO_OPTION);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                String url = "http://120.107.152.110/~a0303/DB/crud.php?action=reset&table=McOS_meal";
                return DBRequest.httpGet(url);
            }
            
            @Override
            protected void done() {
                try {
                    JSONObject response = get();
                    
                    if ("success".equals(response.getString("status"))) {
                        int newAutoIncrement = response.getInt("new_auto_increment");
                        statusLabel.setText("✓ AUTO_INCREMENT 已重置為 " + newAutoIncrement);
                        
                        JOptionPane.showMessageDialog(MealManagerGUI.this, 
                            "✓ AUTO_INCREMENT 已重置為 " + newAutoIncrement + "\n" +
                            "下次新增時會使用連續的 ID", 
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        throw new Exception(response.getString("message"));
                    }
                } catch (Exception ex) {
                    statusLabel.setText("✗ 重置失敗: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MealManagerGUI.this, 
                        "重置失敗: " + ex.getMessage(), 
                        "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    // ===== 主程式入口 =====
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MealManagerGUI frame = new MealManagerGUI();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}
