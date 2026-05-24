package gui;

import db.DBRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * McOS - 餐點與原物料管理系統 (方案B：單步製程時間純淨版)
 */
public class MealManagerGUI extends JFrame {
    private JTable mealTable;
    private DefaultTableModel tableModel;
    private JTextField mealNameField;
    private JSpinner prepTimeSpinner;
    private JButton addBtn, editBtn, deleteBtn, refreshBtn;
    private JLabel statusLabel;
    private JTextField stepOrderField;   
    private JTextField stepDescField;    
    private JTextField equipmentIdField; 
    
    private boolean isShowingIngredients = false; 
    private JButton switchViewBtn;               
    private JButton openBomBtn;                  
    private JPanel inputPanel;                   
    
    // 用來暫存被選中的餐點資訊
    private int currentSelectedMealId = -1;
    private String currentSelectedMealName = "未選擇餐點";
    
    public MealManagerGUI() {
        setTitle("McOS - 餐點與原物料管理系統");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(950, 700);
        setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel titleLabel = new JLabel("🍔 餐點與原物料核心模組");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // 輸入面板
        inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(3, 4, 10, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder("生產工序與單品維護"));
        
        inputPanel.add(new JLabel("步驟:"));
        stepOrderField = new JTextField(3);
        inputPanel.add(stepOrderField);
        
        inputPanel.add(new JLabel("工序說明:"));
        stepDescField = new JTextField(10);
        inputPanel.add(stepDescField);
        
        inputPanel.add(new JLabel("設備ID:"));
        equipmentIdField = new JTextField(5);
        inputPanel.add(equipmentIdField);
        
        inputPanel.add(new JLabel("餐點名稱:"));
        mealNameField = new JTextField(15);
        inputPanel.add(mealNameField);
        
        inputPanel.add(new JLabel("準備時間(秒):"));
        SpinnerModel spinnerModel = new SpinnerNumberModel(0, 0, 1800, 1);
        prepTimeSpinner = new JSpinner(spinnerModel);
        inputPanel.add(prepTimeSpinner);
        
        // 按鈕區
        addBtn = new JButton("新增"); addBtn.addActionListener(this::addMeal); inputPanel.add(addBtn);
        editBtn = new JButton("編輯"); editBtn.addActionListener(this::editMeal); inputPanel.add(editBtn);
        deleteBtn = new JButton("刪除"); deleteBtn.addActionListener(this::deleteMeal); inputPanel.add(deleteBtn);
        refreshBtn = new JButton("刷新"); refreshBtn.addActionListener(e -> refreshData()); inputPanel.add(refreshBtn);
        
        // 功能按鈕
        switchViewBtn = new JButton("🔄 切換原物料組成分");
        switchViewBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        switchViewBtn.setBackground(new Color(220, 240, 255));
        switchViewBtn.addActionListener(e -> toggleViewMode());
        inputPanel.add(switchViewBtn);
        
        openBomBtn = new JButton("📊 叫出 BOM 視窗");
        openBomBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        openBomBtn.setBackground(new Color(220, 255, 220));
        openBomBtn.addActionListener(e -> openBomDialog());
        inputPanel.add(openBomBtn);
        
        inputPanel.add(new JLabel("")); inputPanel.add(new JLabel(""));
        
        // 表格面板
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("數據瀏覽清單"));
        
        // 🎯 嚴格保持 6 個欄位，不要有建立時間
        tableModel = new DefaultTableModel(new String[]{"ID", "餐點名稱", "準備時間(秒)", "工序說明", "設備 ID"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        
        mealTable = new JTable(tableModel);
        mealTable.setRowHeight(25);
        mealTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInputFields();
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(mealTable);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        
        // 底部狀態列
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("McOS 數據同步就緒");
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(tablePanel, BorderLayout.SOUTH);
        tablePanel.setPreferredSize(new Dimension(900, 380));
        
        add(mainPanel);
        loadMeals();
    }
    
    private void refreshData() {
        if (isShowingIngredients) loadIngredientsForSelectedMeal(); else loadMeals();
    }
    
    private void toggleViewMode() {
        isShowingIngredients = !isShowingIngredients;
        if (isShowingIngredients) {
            switchViewBtn.setText("🍔 切換回餐點列表");
            setFieldsEnabled(false); 
            
            String[] columns = {"原料ID", "對應餐點", "使用原料名稱", "標準配方用量", "計量單位"};
            tableModel.setDataVector(null, columns);
            
            loadIngredientsForSelectedMeal();
        } else {
            switchViewBtn.setText("🔄 切換原物料組成分");
            setFieldsEnabled(true);
            
            String[] columns = {"ID", "餐點名稱", "準備時間(秒)",  "工序說明", "設備 ID"};
            tableModel.setDataVector(null, columns);
            loadMeals();
        }
    }
    
    private void setFieldsEnabled(boolean enabled) {
        mealNameField.setEnabled(enabled); prepTimeSpinner.setEnabled(enabled);
        stepOrderField.setEnabled(enabled); stepDescField.setEnabled(enabled); equipmentIdField.setEnabled(enabled);
        addBtn.setEnabled(enabled); editBtn.setEnabled(enabled); deleteBtn.setEnabled(enabled);
    }
    
    private void loadIngredientsForSelectedMeal() {
    tableModel.setRowCount(0); // 先清空表格
    
    if (currentSelectedMealId == -1) {
        statusLabel.setText("⚠️ 提示：請先在餐點列表中選擇一項餐點，再切換檢視原料。");
        return;
    }
    
    statusLabel.setText("⏳ 正在從伺服器動態讀取餐點 【" + currentSelectedMealName + "】 的真實原料組成...");
    
    new SwingWorker<java.util.List<Object[]>, Void>() {
        @Override
        protected java.util.List<Object[]> doInBackground() throws Exception {
            java.util.List<Object[]> rowsToReturn = new java.util.ArrayList<>();
            
            // 1. 讀取線上真實的原料表與配方表
            JSONArray bomData = db.DBRequest.loadBOMData();     
            JSONArray ingredients = db.DBRequest.loadIngredients(); 
            
            // 2. 把原料資料做成 Map 方便快速查詢 (key: ing_id -> [名稱, 單位])
            java.util.Map<Integer, JSONObject> ingMap = new java.util.HashMap<>();
            for (int i = 0; i < ingredients.length(); i++) {
                JSONObject ing = ingredients.getJSONObject(i);
                ingMap.put(ing.optInt("ing_id"), ing);
            }
            
            // 3. 篩選出目前選中餐點 (currentSelectedMealId) 的配方物料
            for (int i = 0; i < bomData.length(); i++) {
                JSONObject bom = bomData.getJSONObject(i);
                int mealId = bom.optInt("mealID");
                
                if (mealId == currentSelectedMealId) {
                    int ingId = bom.optInt("ingID");
                    double qty = bom.optDouble("qty");
                    
                    // 從原料表中查找真正的名字與單位
                    String ingName = "未知原料";
                    String unit = "單位";
                    if (ingMap.containsKey(ingId)) {
                        JSONObject ingObj = ingMap.get(ingId);
                        ingName = ingObj.optString("ing_name", "未知原料");
                        unit = ingObj.optString("unit", "");
                    }
                    
                    
                    rowsToReturn.add(new Object[]{
                        ingId, 
                        currentSelectedMealName, 
                        ingName, 
                        qty, 
                        unit
                    });
                }
            }
            return rowsToReturn;
        }
        
        @Override
        protected void done() {
            try {
                java.util.List<Object[]> result = get();
                if (result.isEmpty()) {
                    statusLabel.setText("⚠️ 提示：資料庫中尚無餐點 【" + currentSelectedMealName + "】 的 BOM 配方成本資料。");
                } else {
                    for (Object[] row : result) {
                        tableModel.addRow(row);
                    }
                    statusLabel.setText("✓ 成功載入餐點 【" + currentSelectedMealName + "】 的真實生產 BOM 結構！");
                }
            } catch (Exception ex) {
                statusLabel.setText("❌ 讀取資料庫失敗: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }.execute();
}
    
    /**
     * 載入餐點列表（🎯 徹底拔除 DBHelper，完全使用 DBRequest 正確版）
     */
    private void loadMeals() {
    new SwingWorker<JSONArray, Void>() {
        @Override 
        protected JSONArray doInBackground() throws Exception { 
            // 🚀 這裡會去呼叫 DBRequest，它會去拆解 PHP 的 {"data": [...]} 並回傳 JSONArray
            return db.DBRequest.queryMeals(); 
        }
        
        @Override 
        protected void done() {
            try {
                JSONArray meals = get();
                
                // 🛑 安全檢查：如果沒抓到資料，不要往下跑避免報錯
                if (meals == null) {
                    statusLabel.setText("⚠️ 提示: 後端未回傳任何餐點資料");
                    return;
                }
                
                tableModel.setRowCount(0); // 清空表格
                
                for (int i = 0; i < meals.length(); i++) {
                    JSONObject meal = meals.getJSONObject(i);
                    
                    int mealId = meal.optInt("meal_id");
                    String mealName = meal.optString("meal_name", "未知");
                    
                    // 🎯 對齊新 PHP 的欄位名稱 total_minutes
                    int totalMinutes = meal.optInt("total_minutes", 0); 
                    int prepTimeSeconds = totalMinutes * 60; 
                    
                    // 🎯 對齊新 PHP 的欄位名稱 step_description 與 etype
                    String stepDesc = meal.optString("step_description", "未設定");
                    String equipId = meal.optString("etype", "未設定");
                    
                    // 塞入新的 5 欄表格
                    tableModel.addRow(new Object[]{
                        mealId, 
                        mealName, 
                        prepTimeSeconds, 
                        stepDesc, 
                        equipId
                    });
                }
                statusLabel.setText("✓ 已成功整合餐點總工序時間 (秒)");
            } catch (Exception ex) {
                // 💡 如果還是空白，看這裡印出什麼錯誤訊息，就能秒懂卡在哪裡！
                statusLabel.setText("✗ 錯誤: " + ex.getMessage());
                ex.printStackTrace(); // 在控制台印出詳細報錯
            }
        }
    }.execute();
}
    
    // 🎯 點擊列回填事件：索引 0~5 完美對應表格 6 欄
    private void updateInputFields() {
        int row = mealTable.getSelectedRow();
        if (row == -1 || isShowingIngredients) return;
        
        currentSelectedMealId = Integer.parseInt(tableModel.getValueAt(row, 0).toString());
        currentSelectedMealName = tableModel.getValueAt(row, 1).toString();
        
        mealNameField.setText(currentSelectedMealName);
        prepTimeSpinner.setValue(Integer.parseInt(tableModel.getValueAt(row, 2).toString()));
        //stepOrderField.setText(tableModel.getValueAt(row, 3).toString());
        stepDescField.setText(tableModel.getValueAt(row, 3).toString());
        equipmentIdField.setText(tableModel.getValueAt(row, 4).toString());
    }
    
    private void openBomDialog() {
        JDialog bomDialog = new JDialog(this, "McOS 決策報表 - 小 BOM 整合管理視窗", true);
        bomDialog.setSize(750, 520);
        bomDialog.setLocationRelativeTo(this);
        JTabbedPane tabs = new JTabbedPane();
        
        // Tab 1
        // Tab 1: 訂單原料消耗 (連接 View_Daily_Total_Consumption)
        JPanel p1 = new JPanel(new BorderLayout());
        JPanel p1Top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p1Top.add(new JLabel("選擇生產分析日期："));

        // 宣告 dateBox 為 final，確保監聽器可以存取
        final JComboBox<String> dateBox = new JComboBox<>(new String[]{"2026-04-21", "2026-04-22"});
        dateBox.setEditable(true); 
        dateBox.setPreferredSize(new Dimension(120, 25));
        p1Top.add(dateBox);

        JButton btn1 = new JButton("計算實際消耗量");
        p1Top.add(btn1);

        DefaultTableModel m1 = new DefaultTableModel(new String[]{"原料名稱", "實際消耗量", "單位", "庫存水位建議"}, 0);
        JTable table1 = new JTable(m1);
        p1.add(p1Top, BorderLayout.NORTH); 
        p1.add(new JScrollPane(table1), BorderLayout.CENTER);

        btn1.addActionListener(e -> {
            // 修正：這裡改成從 dateBox 取得值
            String selectedDate = (String) dateBox.getEditor().getItem();
            
            m1.setRowCount(0);
            
            new SwingWorker<JSONArray, Void>() {
                @Override
                protected JSONArray doInBackground() throws Exception {
                    // 呼叫 DBRequest 進行查詢
                    return db.DBRequest.getConsumptionWithInventory(selectedDate);
                }
                @Override
                protected void done() {
                    try {
                        JSONArray data = get();
                        if (data.length() == 0) {
                            // 若無資料回饋使用者
                            statusLabel.setText("⚠️ " + selectedDate + " 無相關消耗記錄");
                        } else {
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject row = data.getJSONObject(i);
                                String status = row.optString("庫存狀態", "未知");
                                
                                String displayStatus = status.contains("過低") ? "🚨 " + status : 
                                                    (status.contains("偏低") ? "⚠️ " + status : "🟢 " + status);
                                                    
                                m1.addRow(new Object[]{
                                    row.getString("原料名稱"), 
                                    row.getDouble("單日總消耗數量"), 
                                    row.getString("單位"), 
                                    displayStatus
                                });
                            }
                            statusLabel.setText("✓ 數據更新完成");
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "讀取失敗: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        // 最後記得把 p1 加入到 tabs 中
        tabs.addTab("原料消耗統計", p1);
        
        // Tab 2: 單品 BOM 樹狀圖 (完整動態版)
        JPanel p2 = new JPanel(new BorderLayout());
        JTextArea txt2 = new JTextArea("\n 🌲 點擊下方按鈕以展開 BOM 樹狀結構...\n");
        txt2.setFont(new Font("Monospaced", Font.PLAIN, 14)); 
        txt2.setEditable(false);
        JButton btn2 = new JButton("🌲 展開全單品樹狀 BOM");
        p2.add(new JScrollPane(txt2), BorderLayout.CENTER); 
        p2.add(btn2, BorderLayout.SOUTH);

        btn2.addActionListener(e -> {
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    // 1. 抓取三張表資料
                    JSONArray bomData = db.DBRequest.loadBOMData();     // 對應 get_mealcost.php
                    JSONArray ingredients = db.DBRequest.loadIngredients(); // 對應 get_ingredients.php
                    JSONArray meals = db.DBRequest.queryMeals();        // 既有的餐點資料
                    
                    // 2. 建立查詢用的 Map
                    java.util.Map<Integer, String> ingMap = new java.util.HashMap<>();
                    for(int i=0; i<ingredients.length(); i++) {
                        JSONObject ing = ingredients.getJSONObject(i);
                        ingMap.put(ing.optInt("ing_id"), ing.optString("ing_name", "未知原料"));
                    }
                    
                    java.util.Map<Integer, String> mealMap = new java.util.HashMap<>();
                    for(int i=0; i<meals.length(); i++) {
                        JSONObject m = meals.getJSONObject(i);
                        mealMap.put(m.optInt("meal_id"), m.optString("meal_name", "未知餐點"));
                    }
                    
                    // 3. 處理樹狀邏輯 (用 TreeMap 確保 mealID 排序)
                    java.util.TreeMap<Integer, StringBuilder> treeMap = new java.util.TreeMap<>();
                    for(int i=0; i<bomData.length(); i++) {
                        JSONObject item = bomData.getJSONObject(i);
                        int mid = item.optInt("mealID");
                        int iid = item.optInt("ingID");
                        double qty = item.optDouble("qty");
                        
                        treeMap.computeIfAbsent(mid, k -> new StringBuilder("├── 🍔 " + mealMap.getOrDefault(k, "餐點ID: " + k) + "\n"))
                               .append("│   └── [原料ID: ").append(iid).append("] ")
                               .append(ingMap.getOrDefault(iid, "未知原料")).append(" * ").append(qty).append("\n");
                    }
                    
                    // 4. 輸出結果
                    StringBuilder sb = new StringBuilder("🌲 McOS 標準單品材料清單結構樹\n");
                    for(StringBuilder sub : treeMap.values()) sb.append(sub);
                    return sb.toString();
                }
                
                @Override
                protected void done() {
                    try { txt2.setText(get()); } 
                    catch(Exception ex) { txt2.setText("讀取錯誤: " + ex.getMessage()); }
                }
            }.execute();
        });
        
        // Tab 3
        // Tab 3: 套餐內含餐點 (改成樹狀圖版本)
        JPanel p3 = new JPanel(new BorderLayout());

        // 1. 建立用來顯示樹狀圖的文字區域
        JTextArea txt3 = new JTextArea("\n 🍔 點擊下方按鈕同步線上現存套餐組合與餐點明細...\n");
        txt3.setFont(new Font("Monospaced", Font.PLAIN, 14)); 
        txt3.setEditable(false);

        // 2. 這是原本就有的按鈕
        JButton btn3 = new JButton("同步線上現存套餐組合");

        // 3. 把元件重新塞進面板
        p3.add(new JScrollPane(txt3), BorderLayout.CENTER); 
        p3.add(btn3, BorderLayout.SOUTH);

        btn3.addActionListener(e -> {
        txt3.setText(" ⏳ 正在同步伺服器套餐資料並轉換名稱中...\n");
        
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                // 1. 直接重複利用剛剛做好的爆炸圖 API，裡面就包含套餐與餐點名字了！
                JSONObject response = db.DBRequest.getComboBomExplosion();
                if (!"success".equals(response.optString("status"))) {
                    return "❌ 同步失敗: " + response.optString("message");
                }
                
                JSONArray details = response.getJSONArray("details");
                
                // 2. 建立結構樹 (用 TreeMap 排序)
                java.util.TreeMap<Integer, StringBuilder> comboTree = new java.util.TreeMap<>();
                java.util.Map<Integer, String> nameMap = new java.util.HashMap<>();
                
                for (int i = 0; i < details.length(); i++) {
                    JSONObject row = details.getJSONObject(i);
                    int cId = row.getInt("comboID");
                    String cName = row.getString("comboName");
                    String mName = row.getString("meal_name");
                    int mId = row.optInt("mealID"); // 如果 PHP 沒傳也可以從之前的欄位對齊
                    int qty = row.getInt("quantity");
                    
                    nameMap.put(cId, cName);
                    
                    // 🎯 核心要求：餐點直接顯示【名字 + ID】，格式如：大麥克 (ID: 1) * 1
                    comboTree.computeIfAbsent(cId, k -> new StringBuilder())
                    .append("  ├── 🍕 ")
                    .append(mName)
                    .append(" (ID: ").append(mId).append(")") // 🎯 直接塞入抓到的 mId
                    .append(" * ").append(qty).append("\n");
                    }
                
                // 3. 開始組合最終呈現在 Tab 3 的漂亮樹狀字串
                StringBuilder sb = new StringBuilder();
                sb.append("📋 [線上現存套餐組合明細清單] ➔ 餐點名稱與 ID 對照樹\n");
                sb.append("===========================================================\n");
                
                for (int cId : comboTree.keySet()) {
                    sb.append("🍱 套餐名稱：").append(nameMap.get(cId))
                    .append(" (套餐 ID: ").append(cId).append(") ➔ [正常販售]\n");
                    
                    // 填入內含的單品名字+ID組合
                    sb.append(comboTree.get(cId));
                    sb.append("-----------------------------------------------------------\n");
                }
                
                sb.append("✓ [全系統現存套餐內含餐點名稱同步完畢]");
                return sb.toString();
            }
            
            @Override
            protected void done() {
                try { 
                    txt3.setText(get()); 
                } catch(Exception ex) { 
                    txt3.setText("❌ 同步解析錯誤: " + ex.getMessage()); 
                    ex.printStackTrace();
                }
            }
        }.execute();
    });
        
       
        // Tab 4: 套餐物料爆炸圖 (完整動態連動資料庫版)
        JPanel p4 = new JPanel(new BorderLayout());
        JTextArea txt4 = new JTextArea("\n 🌴 點擊下方按鈕進行二級 BOM 聯動爆炸解析 (Combo Explosion) ...\n");
        txt4.setFont(new Font("Monospaced", Font.PLAIN, 14)); 
        txt4.setEditable(false);
        JButton btn4 = new JButton("🌴 產生套餐最底層原料關聯爆炸圖");
        p4.add(new JScrollPane(txt4), BorderLayout.CENTER); 
        p4.add(btn4, BorderLayout.SOUTH);

        btn4.addActionListener(e -> {
            txt4.setText(" ⏳ 正在清查跨表數據並計算物料清單，請稍候...\n");
            
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    // 1. 遠端抓取包裹了 details 與 boms 的 JSONObject
                    JSONObject response = db.DBRequest.getComboBomExplosion();
                    if (!"success".equals(response.optString("status"))) {
                        return "❌ 讀取失敗: " + response.optString("message");
                    }
                    
                    JSONArray details = response.getJSONArray("details");
                    JSONArray boms = response.getJSONArray("boms");
                    
                    // 2. 建立一個 Map 方便用 comboID 快速查詢總原料
                    java.util.Map<Integer, String> bomMap = new java.util.HashMap<>();
                    for (int i = 0; i < boms.length(); i++) {
                        JSONObject b = boms.getJSONObject(i);
                        bomMap.put(b.getInt("comboID"), b.getString("total_ingredients"));
                    }
                    
                    // 3. 處理結構樹邏輯 (用 TreeMap 確保套餐編號依 1, 2, 3... 排序)
                    java.util.TreeMap<Integer, StringBuilder> comboTree = new java.util.TreeMap<>();
                    java.util.Map<Integer, String> nameMap = new java.util.HashMap<>();
                    
                    for (int i = 0; i < details.length(); i++) {
                        JSONObject row = details.getJSONObject(i);
                        int cId = row.getInt("comboID");
                        String cName = row.getString("comboName");
                        String mName = row.getString("meal_name");
                        int qty = row.getInt("quantity");
                        
                        nameMap.put(cId, cName);
                        
                        // 如果是此套餐第一次出現，先建立套餐標頭
                        comboTree.computeIfAbsent(cId, k -> new StringBuilder())
                                .append("  ├── 🍔 ").append(mName).append(" * ").append(qty).append("\n");
                    }
                    
                    // 4. 開始組合最終呈現在文字框的漂亮樹狀字串
                    StringBuilder sb = new StringBuilder();
                    sb.append("🌴 [二級跨表聯動] 套餐 ➔ 內含單品 ➔ 最底層物料關聯圖\n");
                    sb.append("===========================================================\n");
                    
                    for (int cId : comboTree.keySet()) {
                        sb.append("🍱 套餐：").append(nameMap.get(cId)).append(" (Combo_ID: ").append(cId).append(")\n");
                        // 填入內含的單品組合
                        sb.append(comboTree.get(cId));
                        sb.append("  ➔ 總累計需求：").append(bomMap.getOrDefault(cId, "無物料配方資料")).append("\n");
                        sb.append("-----------------------------------------------------------\n");
                    }
                    
                    sb.append("📊 [本系統全套餐原始物料爆炸清單清查完畢]");
                    return sb.toString();
                }
                
                @Override
                protected void done() {
                    try { 
                        txt4.setText(get()); 
                    } catch(Exception ex) { 
                        txt4.setText("❌ 爆炸圖解析錯誤: " + ex.getMessage()); 
                    }
                }
            }.execute();
        });
        
        tabs.addTab("1. 訂單原料消耗", p1); tabs.addTab("2. 單品 BOM 樹狀圖", p2);
        tabs.addTab("3. 套餐內含餐點", p3); tabs.addTab("4. 套餐物料爆炸圖", p4);
        bomDialog.add(tabs, BorderLayout.CENTER);
        bomDialog.setVisible(true);
    }

    private void addMeal(ActionEvent e) {}
    private void editMeal(ActionEvent e) {}
    private void deleteMeal(ActionEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MealManagerGUI().setVisible(true));
    }
}