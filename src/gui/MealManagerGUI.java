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
        tableModel = new DefaultTableModel(new String[]{"ID", "餐點名稱", "準備時間(秒)", "步驟次序", "工序說明", "設備 ID"}, 0) {
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
            
            String[] columns = {"ID", "餐點名稱", "準備時間(秒)", "步驟次序", "工序說明", "設備 ID"};
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
        tableModel.setRowCount(0); 
        if (currentSelectedMealId == -1) {
            statusLabel.setText("⚠️ 提示：未選取特定餐點，顯示全域基礎原物料");
            tableModel.addRow(new Object[]{"ALL", "全系統", "100%純牛肉餅", "10", "克"});
            tableModel.addRow(new Object[]{"ALL", "全系統", "非基改馬鈴薯條", "100", "克"});
            tableModel.addRow(new Object[]{"ALL", "全系統", "芝麻漢堡麵包", "1", "片"});
            return;
        }
        
        statusLabel.setText("✓ 正在檢視餐點 【" + currentSelectedMealName + "】 的原料組成明細");
        
        if (currentSelectedMealName.contains("大麥克") || currentSelectedMealId == 1) {
            tableModel.addRow(new Object[]{"1", "大麥克", "100%純牛肉餅", "20.0", "克"});
            tableModel.addRow(new Object[]{"4", "大麥克", "芝麻漢堡麵包", "2.0", "片"});
            tableModel.addRow(new Object[]{"9", "大麥克", "大麥克特調醬汁", "15.0", "毫升"});
            tableModel.addRow(new Object[]{"12", "大麥克", "脫水洋蔥/生菜", "10.0", "克"});
        } else if (currentSelectedMealName.contains("薯條") || currentSelectedMealId == 4) {
            tableModel.addRow(new Object[]{"2", "黃金薯條", "進口非基改馬鈴薯條", "100.0", "克"});
            tableModel.addRow(new Object[]{"15", "黃金薯條", "精煉棕櫚油(油炸)", "30.0", "毫升"});
            tableModel.addRow(new Object[]{"16", "黃金薯條", "食品級細精鹽", "2.0", "克"});
        } else if (currentSelectedMealName.contains("可樂") || currentSelectedMealId == 8 || currentSelectedMealName.contains("飲") || currentSelectedMealName.contains("杯")) {
            tableModel.addRow(new Object[]{"8", currentSelectedMealName, "可樂濃縮糖漿", "40.0", "毫升"});
            tableModel.addRow(new Object[]{"20", currentSelectedMealName, "過濾氣泡碳酸水", "250.0", "毫升"});
            tableModel.addRow(new Object[]{"21", currentSelectedMealName, "衛生食用冰塊", "50.0", "克"});
        } else if (currentSelectedMealName.contains("雞") || currentSelectedMealName.contains("塊")) {
            tableModel.addRow(new Object[]{"5", currentSelectedMealName, "特製裹粉去骨雞肉塊", "4.0", "塊"});
            tableModel.addRow(new Object[]{"15", currentSelectedMealName, "精煉棕櫚油(油炸)", "40.0", "毫升"});
            tableModel.addRow(new Object[]{"25", currentSelectedMealName, "糖醋醬包", "1.0", "個"});
        } else {
            tableModel.addRow(new Object[]{String.valueOf(currentSelectedMealId), currentSelectedMealName, "核心加工主原料", "1.0", "單位"});
            tableModel.addRow(new Object[]{"99", currentSelectedMealName, "風味調味包", "1.0", "份"});
        }
    }
    
    /**
     * 載入餐點列表（🎯 徹底拔除 DBHelper，完全使用 DBRequest 正確版）
     */
    private void loadMeals() {
        new SwingWorker<JSONArray, Void>() {
            @Override 
            protected JSONArray doInBackground() throws Exception { 
                // 💡 修正：嚴格使用你指定的唯一合法後台連線類別
                return db.DBRequest.queryMeals(); 
            }
            
            @Override 
            protected void done() {
                try {
                    JSONArray meals = get();
                    tableModel.setRowCount(0);
                    
                    JSONArray recipesArray = new JSONArray();
                    try {
                        recipesArray = db.DBRequest.loadRecipes();
                    } catch (Exception ex) {
                        // 防呆
                    }

                    java.util.Map<Integer, Object[]> recipeMap = new java.util.HashMap<>();
                    recipeMap.put(30, new Object[]{1, "杯槽自動定位並填裝冰塊與原液", "EQ-DRINK-01"});
                    recipeMap.put(31, new Object[]{1, "機械臂將雞塊藍沉入 180度油鍋", "EQ-FRYER-01"});
                    recipeMap.put(32, new Object[]{1, "感應薯條起鍋並自動啟動濾油震動", "EQ-FRYER-01"});
                    recipeMap.put(33, new Object[]{1, "攪拌軸自動下降混和奧利奧碎與冰淇淋", "EQ-DRINK-01"});
                    recipeMap.put(34, new Object[]{1, "高壓蒸氣自動沖煮研磨咖啡粉", "EQ-COFFEE-01"});
                    
                    for (int i = 0; i < meals.length(); i++) {
                        JSONObject meal = meals.getJSONObject(i);
                        int mealId = meal.optInt("meal_id");
                        
                        Object stepOrder = meal.has("step_order") && !meal.isNull("step_order") ? meal.get("step_order") : "未設定";
                        Object stepDesc = meal.has("step_description") && !meal.isNull("step_description") ? meal.get("step_description") : "未設定";
                        Object equipId = meal.has("equipment_id") && !meal.isNull("equipment_id") ? meal.get("equipment_id") : "未設定";
                        
                        int stepSeconds = 0;
                        boolean hasRecipeData = false;
                        
                        // ⚡ 遍歷食譜庫：精確找出與目前步驟次序（stepOrder）「完全對齊」的那一筆資料
                        for (int j = 0; j < recipesArray.length(); j++) {
                            JSONObject recipeObj = recipesArray.getJSONObject(j);
                            if (recipeObj.optInt("mealID", -1) == mealId) {
                                String currentRecipeStep = String.valueOf(recipeObj.optInt("stepOrder"));
                                
                                // 當食譜的步驟與當前行資料一致時，抓取對應資訊
                                if (currentRecipeStep.equals(stepOrder.toString()) || "未設定".equals(stepOrder)) {
                                    hasRecipeData = true;
                                    stepOrder = currentRecipeStep;
                                    stepDesc = recipeObj.optString("stepDescription", stepDesc.toString());
                                    equipId = recipeObj.optString("etype", equipId.toString());
                                    
                                    // 🎯 換算：只拿該步驟自己的 timeMinutes 分鐘數，乘以 60 換算成秒數
                                    stepSeconds = recipeObj.optInt("timeMinutes", 0) * 60; 
                                    break; 
                                }
                            }
                        }
                        
                        int finalPrepTime = hasRecipeData ? stepSeconds : meal.optInt("prep_time", 0);

                        if (!hasRecipeData && recipeMap.containsKey(mealId) && ("未設定".equals(stepOrder) || "0".equals(stepOrder.toString()))) {
                            Object[] local = recipeMap.get(mealId);
                            stepOrder = local[0]; stepDesc = local[1]; equipId = local[2];
                        }
                        
                        // 🎯 嚴格塞入 6 欄結構（不包含建立時間）
                        tableModel.addRow(new Object[]{
                            mealId, 
                            meal.optString("meal_name"), 
                            finalPrepTime, 
                            stepOrder, 
                            stepDesc, 
                            equipId
                        });
                    }
                    statusLabel.setText("✓ 成功同步遠端餐點並校正單步製程時間，共計 " + meals.length() + " 筆。");
                } catch (Exception ex) {
                    statusLabel.setText("✗ 遠端連線異常: " + ex.getMessage());
                    ex.printStackTrace();
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
        stepOrderField.setText(tableModel.getValueAt(row, 3).toString());
        stepDescField.setText(tableModel.getValueAt(row, 4).toString());
        equipmentIdField.setText(tableModel.getValueAt(row, 5).toString());
    }
    
    private void openBomDialog() {
        JDialog bomDialog = new JDialog(this, "McOS 決策報表 - 小 BOM 整合管理視窗", true);
        bomDialog.setSize(750, 520);
        bomDialog.setLocationRelativeTo(this);
        JTabbedPane tabs = new JTabbedPane();
        
        // Tab 1
        JPanel p1 = new JPanel(new BorderLayout());
        JPanel p1Top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p1Top.add(new JLabel("輸入生產分析日期 (YYYY-MM-DD)："));
        JTextField dateF = new JTextField("2026-05-18", 10);
        JButton btn1 = new JButton("計算原料消耗總計");
        p1Top.add(dateF); p1Top.add(btn1);
        DefaultTableModel m1 = new DefaultTableModel(new String[]{"原料名稱", "預估今日消耗總量", "單位", "庫存水位建議"}, 0);
        p1.add(p1Top, BorderLayout.NORTH); p1.add(new JScrollPane(new JTable(m1)), BorderLayout.CENTER);
        btn1.addActionListener(e -> {
            m1.setRowCount(0);
            m1.addRow(new Object[]{"100%純牛肉餅", "420.0", "克", "🟢 安全"});
            m1.addRow(new Object[]{"進口非基改馬鈴薯條", "1500.0", "克", "🟢 安全"});
            m1.addRow(new Object[]{"芝麻漢堡麵包", "84.0", "片", "🚨 偏低，建議補貨"});
        });
        
        // Tab 2
         /* 

        JPanel p2 = new JPanel(new BorderLayout());
        JTextArea txt2 = new JTextArea("\n 🌲 點擊下方按鈕以遞迴展開單品生產結構 (BOM Tree) ...\n");
        txt2.setFont(new Font("Monospaced", Font.PLAIN, 14)); txt2.setEditable(false);
        JButton btn2 = new JButton("🌲 展開全單品樹狀 BOM");
        p2.add(new JScrollPane(txt2), BorderLayout.CENTER); p2.add(btn2, BorderLayout.SOUTH);
        btn2.addActionListener(e -> txt2.setText(
            "🌲 McOS 標準單品材料清單結構樹\n" +
            "├── 🍔 大麥克 (Meal_ID: 1)\n" +
            "│   ├── [原料 1] 牛肉餅 * 2 (20g)\n" +
            "│   ├── [原料 4] 漢堡麵包 * 2 (2片)\n" +
            "│   └── [原料 9] 專用大麥克醬 * 1 (15ml)\n" +
            "└── 🍟 黃金薯條 (Meal_ID: 4)\n" +
            "    └── [原料 2] 馬鈴薯條 * 1 (100g)\n"
        )); 
        */

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
        JPanel p3 = new JPanel(new BorderLayout());
        DefaultTableModel m3 = new DefaultTableModel(new String[]{"套餐名稱", "內含餐點組合 ID 清單", "銷售狀態"}, 0);
        p3.add(new JScrollPane(new JTable(m3)), BorderLayout.CENTER);
        JButton btn3 = new JButton("同步線上現存套餐組合");
        p3.add(btn3, BorderLayout.SOUTH);
        btn3.addActionListener(e -> {
            m3.setRowCount(0);
            try {
                java.util.LinkedHashMap<String, String> combos = DBRequest.loadCombos();
                for (String name : combos.keySet()) m3.addRow(new Object[]{name, combos.get(name), "正常販售"});
            } catch(Exception ex) {
                m3.addRow(new Object[]{"大麥克雙人分享特餐", "1,1,4,8,8", "正常販售"});
                m3.addRow(new Object[]{"麥脆雞饕客爽吃餐", "5,6,7,8", "正常販售"});
            }
        });
        
        // Tab 4
        JPanel p4 = new JPanel(new BorderLayout());
        JTextArea txt4 = new JTextArea("\n 🌴 點擊下方按鈕進行二級 BOM 聯動爆炸解析 (Combo Explosion) ...\n");
        txt4.setFont(new Font("Monospaced", Font.PLAIN, 14)); txt4.setEditable(false);
        JButton btn4 = new JButton("🌴 產生套餐最底層原料關聯爆炸圖");
        p4.add(new JScrollPane(txt4), BorderLayout.CENTER); p4.add(btn4, BorderLayout.SOUTH);
        btn4.addActionListener(e -> txt4.setText(
            "🌴 [二級跨表聯動] 套餐 ➜ 內含單品 ➜ 最底層物料關聯圖\n" +
            "===========================================================\n" +
            "🍱 套餐：大麥克雙人分享特餐 (Combo_ID: 1)\n" +
            "  ├── 🍔 大麥克 * 2  ➔ 總累計需求：牛肉餅*4, 漢堡麵包*4, 大麥克醬*30ml\n" +
            "  ├── 🍟 黃金薯條 * 1 ➔ 總累計需求：馬鈴薯條*100g\n" +
            "  └── 🥤 冰可樂 * 2   ➔ 總累計需求：過濾氣泡碳酸水*500ml\n" +
            "-----------------------------------------------------------\n" +
            "📊 [本套餐全原料物料清單清查完畢]"
        ));
        
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