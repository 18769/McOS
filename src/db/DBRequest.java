package db;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class DBRequest {

    public static class WorkerRoster {
        public final ArrayList<JSONObject> workers;
        public final ArrayList<Integer> workerIds;
        public final LinkedHashMap<Integer, String> workerNames;

        private WorkerRoster(ArrayList<JSONObject> workers,
                             ArrayList<Integer> workerIds,
                             LinkedHashMap<Integer, String> workerNames) {
            this.workers = workers;
            this.workerIds = workerIds;
            this.workerNames = workerNames;
        }
    }

    private static final String API_BASE = "http://120.107.152.110/~a0303/DB";
    private static final String MEALS_URL = API_BASE + "/get_meals.php";
    private static final String CRUD_URL = API_BASE + "/crud.php";
    private static final String COMBOS_URL = API_BASE + "/get_combos.php";
    private static final String COMBO_CRUD_URL = API_BASE + "/combo_crud.php";
    private static final String WORKERS_URL = API_BASE + "/get_workers.php";
    private static final String EQUIPMENT_URL = API_BASE + "/get_equipment.php";
    private static final String RECIPE_URL = API_BASE + "/get_recipes.php";
    // Additional APIs (ensure backend provides these endpoints)
    private static final String CUSTOMERS_URL = API_BASE + "/get_customers.php";
    private static final String CONSUME_INVENTORY_URL = API_BASE + "/consume_inventory.php";
    private static final String PURCHASE_INGREDIENT_URL = API_BASE + "/purchase_ingredient.php";
    private static final String ORDER_HISTORY_URL = API_BASE + "/record_order.php";

private static int normalizePrepTimeSeconds(int prepTime) {
        if (prepTime <= 0) {
            return prepTime;
        }
        // API prep_time is typically minutes; treat small values as minutes.
        if (prepTime <= 30) {
            return prepTime * 60;
        }
        return prepTime;
    }

    private static LinkedHashMap<String, ArrayList<Integer>> parseComboMeals(JSONArray combosArray) {
        LinkedHashMap<String, ArrayList<Integer>> comboMeals = new LinkedHashMap<>();
        for (int i = 0; i < combosArray.length(); i++) {
            JSONObject combo = combosArray.getJSONObject(i);
            String comboName = combo.optString("combo_name", combo.optString("comboName", ""));
            if (comboName.isEmpty()) {
                comboName = combo.optString("combo_name", "");
            }
            if (comboName.isEmpty()) {
                continue;
            }
            ArrayList<Integer> mealIds = comboMeals.getOrDefault(comboName, new ArrayList<>());

            if (combo.has("food_items")) {
                String foodItemsStr = combo.optString("food_items", "");
                if (!foodItemsStr.isEmpty()) {
                    String[] items = foodItemsStr.split(",");
                    for (String mealIdStr : items) {
                        try {
                            int mealId = Integer.parseInt(mealIdStr.trim());
                            if (mealId > 0 && !mealIds.contains(mealId)) {
                                mealIds.add(mealId);
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            } else {
                int mealId = combo.optInt("mealID", combo.optInt("meal_id", -1));
                if (mealId > 0 && !mealIds.contains(mealId)) {
                    mealIds.add(mealId);
                }
            }

            comboMeals.put(comboName, mealIds);
        }
        return comboMeals;
    }

    private static LinkedHashMap<String, ArrayList<JSONObject>> buildMealCostMap() {
        LinkedHashMap<String, ArrayList<JSONObject>> mealCostMap = new LinkedHashMap<>();
        try {
            JSONArray bomData = loadBOMData();
            JSONArray meals = queryMeals();

            Map<Integer, String> mealNameMap = new HashMap<>();
            for (int i = 0; i < meals.length(); i++) {
                JSONObject m = meals.getJSONObject(i);
                int mealId = m.optInt("meal_id", -1);
                if (mealId > 0) {
                    mealNameMap.put(mealId, m.optString("meal_name", ""));
                }
            }

            for (int i = 0; i < bomData.length(); i++) {
                JSONObject row = bomData.getJSONObject(i);
                int mealId = row.optInt("mealID", row.optInt("meal_id", -1));
                int ingId = row.optInt("ingID", row.optInt("ing_id", -1));
                double qty = row.optDouble("qty", 0);
                if (mealId <= 0 || ingId <= 0 || qty <= 0) {
                    continue;
                }
                String mealName = mealNameMap.getOrDefault(mealId, "");
                if (mealName.isEmpty()) {
                    continue;
                }
                ArrayList<JSONObject> list = mealCostMap.getOrDefault(mealName, new ArrayList<>());
                JSONObject item = new JSONObject();
                item.put("ing_id", ingId);
                item.put("qty", qty);
                list.add(item);
                mealCostMap.put(mealName, list);
            }
        } catch (Exception e) {
            System.err.println("buildMealCostMap error: " + e.getMessage());
        }
        return mealCostMap;
    }

    private static JSONArray buildConsumptionPayload(JSONArray itemNames) {
        LinkedHashMap<String, ArrayList<JSONObject>> mealCostMap = buildMealCostMap();
        LinkedHashMap<Integer, Double> totals = new LinkedHashMap<>();

        for (int i = 0; i < itemNames.length(); i++) {
            String mealName = itemNames.optString(i, "");
            if (mealName.isEmpty() || !mealCostMap.containsKey(mealName)) {
                continue;
            }
            for (JSONObject item : mealCostMap.get(mealName)) {
                int ingId = item.optInt("ing_id", -1);
                double qty = item.optDouble("qty", 0);
                if (ingId <= 0 || qty <= 0) {
                    continue;
                }
                totals.put(ingId, totals.getOrDefault(ingId, 0.0) + qty);
            }
        }

        JSONArray payload = new JSONArray();
        for (Map.Entry<Integer, Double> entry : totals.entrySet()) {
            JSONObject item = new JSONObject();
            item.put("ing_id", entry.getKey());
            item.put("qty", entry.getValue());
            payload.put(item);
        }
        return payload;
    }

    private static LinkedHashMap<String, Integer> buildRecipePrepTimeMap() {
        LinkedHashMap<String, Integer> recipePrepTimes = new LinkedHashMap<>();
        JSONArray recipes = loadRecipes();
        for (int i = 0; i < recipes.length(); i++) {
            JSONObject recipe = recipes.getJSONObject(i);
            String mealName = recipe.optString("meal_name", "");
            if (mealName.isEmpty()) {
                mealName = recipe.optString("recipe_name", "");
            }
            if (mealName.isEmpty()) {
                continue;
            }
            JSONArray steps = recipe.optJSONArray("steps");
            if (steps == null || steps.length() == 0) {
                continue;
            }
            int total = 0;
            for (int s = 0; s < steps.length(); s++) {
                total += steps.getJSONObject(s).optInt("duration_sec", 0);
            }
            if (total > 0) {
                recipePrepTimes.put(mealName, total);
            }
        }
        return recipePrepTimes;
    }

    public static LinkedHashMap<String, Integer> loadMeals() {
        LinkedHashMap<String, Integer> mealPrepTimes = new LinkedHashMap<>();
        try {
            LinkedHashMap<String, Integer> recipePrepTimes = buildRecipePrepTimeMap();
            JSONArray meals = queryMeals();
            System.out.println("=== 餐點資料調試 ===");
            for (int i = 0; i < meals.length(); i++) {
                JSONObject m = meals.getJSONObject(i);
                String mealName = m.getString("meal_name");
                int prepTime = recipePrepTimes.getOrDefault(mealName, 0);
                if (prepTime <= 0) {
                    int rawPrepTime = m.optInt("prep_time", -1);
                    if (rawPrepTime > 0) {
                        prepTime = normalizePrepTimeSeconds(rawPrepTime);
                    }
                }
                if (prepTime <= 0) {
                    System.err.println("餐點缺少食譜步驟時間，改用預設值 5 分鐘: " + mealName);
                    prepTime = normalizePrepTimeSeconds(5);
                }
                System.out.println("  餐點: " + mealName + " | 準備時間(秒): " + prepTime);
                mealPrepTimes.put(mealName, prepTime);
            }
            System.out.println("從資料庫成功載入 " + meals.length() + " 個餐點\n");
        } catch (Exception e) {
            System.err.println("讀取資料庫失敗，改用預設資料: " + e.getMessage());
            e.printStackTrace();
            mealPrepTimes.put("大麥克", 8 * 60);
            mealPrepTimes.put("小麥克", 6 * 60);
            mealPrepTimes.put("麥克", 7 * 60);
            mealPrepTimes.put("薯條", 3 * 60);
            mealPrepTimes.put("雞塊", 5 * 60);
            mealPrepTimes.put("蘋果派", 4 * 60);
            mealPrepTimes.put("玉米湯", 2 * 60);
            mealPrepTimes.put("可樂", 1 * 60);
            mealPrepTimes.put("舊東洋熱狗", 5 * 60);
            mealPrepTimes.put("冰美式", 3 * 60);
            mealPrepTimes.put("大麥克預設", 0);
            mealPrepTimes.put("雞塊特餐", 0);
        }
        return mealPrepTimes;
    }

    public static LinkedHashMap<String, String> loadCombos() {
        LinkedHashMap<String, String> combos = new LinkedHashMap<>();
        try {
            JSONArray combosArray = queryCombos();
            System.out.println("=== 套餐資料調試 ===");
            LinkedHashMap<String, ArrayList<Integer>> comboMeals = parseComboMeals(combosArray);
            for (Map.Entry<String, ArrayList<Integer>> entry : comboMeals.entrySet()) {
                String comboName = entry.getKey();
                ArrayList<Integer> mealIds = entry.getValue();
                StringBuilder foodItems = new StringBuilder();
                for (int i = 0; i < mealIds.size(); i++) {
                    if (i > 0) foodItems.append(",");
                    foodItems.append(mealIds.get(i));
                }
                System.out.println("  套餐: " + comboName + " | 食材: " + foodItems);
                combos.put(comboName, foodItems.toString());
            }
            System.out.println("從資料庫成功載入 " + comboMeals.size() + " 個套餐\n");
        } catch (Exception e) {
            System.err.println("讀取套餐失敗: " + e.getMessage());
        }
        return combos;
    }

    public static JSONArray loadWorkers() {
        try {
            JSONObject response = httpGet(WORKERS_URL);
            JSONArray data = response.getJSONArray("data");
            System.out.println("成功從資料庫載入 " + data.length() + " 位員工");
            return data;
        } catch (Exception e) {
            System.err.println("讀取資料庫員工失敗: " + e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * 載入廚房設備，完全比照 loadWorkers 模式，直接走線上 API 讀取
     * 回傳 JSONArray of equipment objects
     */
     public static JSONArray loadKitchenEquipment() {
        try {
            JSONObject response = httpGet(EQUIPMENT_URL);
            JSONArray data = response.getJSONArray("data");

            // local equipment.json is used as time-slice status overlay only
            java.nio.file.Path localPath = java.nio.file.Paths.get("DB", "equipment.json");
            if (java.nio.file.Files.exists(localPath)) {
                String content = new String(java.nio.file.Files.readAllBytes(localPath), StandardCharsets.UTF_8);
                JSONArray localData = new JSONArray(content);

                java.util.HashMap<String, String> statusMap = new java.util.HashMap<>();
                for (int i = 0; i < localData.length(); i++) {
                    JSONObject o = localData.getJSONObject(i);
                    String id = o.optString("equipmentID", o.optString("equipmentId", ""));
                    if (!id.isEmpty()) {
                        statusMap.put(id, o.optString("status", ""));
                    }
                }

                for (int i = 0; i < data.length(); i++) {
                    JSONObject o = data.getJSONObject(i);
                    String id = o.optString("equipmentID", o.optString("equipmentId", ""));
                    if (statusMap.containsKey(id)) {
                        o.put("status", statusMap.get(id));
                    }
                }
            }

            System.out.println("成功從資料庫載入 " + data.length() + " 條設備資料");
            return data;
        } catch (Exception e) {
            System.err.println("讀取資料庫設備失敗: " + e.getMessage());
            return new JSONArray();
        }
    }


    /**
     * 載入食譜，完全比照 loadWorkers 模式，直接走線上 API 讀取
     * 回傳 JSONArray of recipe objects
     */
    public static JSONArray loadRecipes() {
        try {
            // 取得原始 recipes 與 meals，將 recipes 正規化成系統期待的欄位
            JSONObject response = httpGet(RECIPE_URL);
            JSONArray data = response.getJSONArray("data");

            // build mealId -> mealName map to allow lookup by meal name
            Map<Integer, String> mealMap = new HashMap<>();
            try {
                JSONArray meals = queryMeals();
                for (int i = 0; i < meals.length(); i++) {
                    JSONObject m = meals.getJSONObject(i);
                    if (m.has("meal_id") && m.has("meal_name")) {
                        mealMap.put(m.getInt("meal_id"), m.getString("meal_name"));
                    }
                }
            } catch (Exception ignore) {
                // if meals cannot be loaded, proceed without mapping
            }

            // Group steps into recipes: { recipe_name, meal_id, version, steps: [ ... ] }
            java.util.LinkedHashMap<String, JSONObject> recipeMap = new java.util.LinkedHashMap<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject r = data.getJSONObject(i);

                int mealId = -1;
                String mealIdStr = null;
                if (r.has("mealID")) mealIdStr = r.optString("mealID", null);
                if (mealIdStr == null && r.has("meal_id")) mealIdStr = r.optString("meal_id", null);
                if (mealIdStr != null && !mealIdStr.isEmpty()) {
                    try { mealId = Integer.parseInt(mealIdStr); } catch (NumberFormatException ex) { }
                }
                if (mealId == -1) mealId = r.optInt("mealID", r.optInt("meal_id", -1));

                // build step object
                JSONObject step = new JSONObject();
                int stepOrder = r.optInt("stepOrder", r.optInt("step_order", -1));
                if (stepOrder != -1) step.put("step_order", stepOrder);

                // step name candidates
                String stepName = null;
                if (r.has("stepDescription")) stepName = r.optString("stepDescription", null);
                if ((stepName == null || stepName.isEmpty()) && r.has("step_description")) stepName = r.optString("step_description", null);
                if ((stepName == null || stepName.isEmpty()) && r.has("step_name")) stepName = r.optString("step_name", null);
                if ((stepName == null || stepName.isEmpty()) && r.has("description")) stepName = r.optString("description", null);
                if ((stepName == null || stepName.isEmpty()) && r.has("stepName")) stepName = r.optString("stepName", null);
                if (stepName != null && !stepName.isEmpty()) step.put("step_name", stepName);

                // duration
                if (r.has("timeMinutes")) {
                    try { int minutes = Integer.parseInt(r.optString("timeMinutes", "0")); step.put("duration_sec", minutes*3); }
                    catch (NumberFormatException ex) { step.put("duration_sec", r.optInt("timeMinutes", 0)); }
                } else if (r.has("duration_sec")) {
                    step.put("duration_sec", r.optInt("duration_sec", 0));
                }

                // equipment type
                if (r.has("etype")) step.put("equipment_type", r.optString("etype").trim().toLowerCase());
                else if (r.has("equipment_type")) step.put("equipment_type", r.optString("equipment_type"));

                // now determine which recipe object to append to (prefer meal_id to avoid step splitting)
                String mapKey = null;
                if (mealId != -1) {
                    mapKey = String.valueOf(mealId);
                } else if (r.has("recipeID")) {
                    mapKey = r.optString("recipeID", null);
                } else if (r.has("id")) {
                    mapKey = r.optString("id", null);
                }
                if (mapKey == null || mapKey.isEmpty()) {
                    // fallback to meal name or index
                    String nameFallback = r.optString("meal_name", r.optString("mealName", "recipe_" + i));
                    mapKey = nameFallback + "_" + i;
                }

                if (!recipeMap.containsKey(mapKey)) {
                    JSONObject recipeObj = new JSONObject();
                    // recipe-level name
                    String recipeName = r.optString("recipe_name", null);
                    if (recipeName == null || recipeName.isEmpty()) {
                        if (mealId != -1 && mealMap.containsKey(mealId)) recipeName = mealMap.get(mealId);
                        else recipeName = r.optString("meal_name", r.optString("mealName", "Recipe " + mapKey));
                    }
                    recipeObj.put("recipe_name", recipeName);
                    if (mealId != -1) recipeObj.put("meal_id", mealId);
                    recipeObj.put("version", r.optInt("version", 1));
                    recipeObj.put("steps", new JSONArray());
                    recipeMap.put(mapKey, recipeObj);
                }

                // append step
                recipeMap.get(mapKey).getJSONArray("steps").put(step);
            }

            // build output array
            JSONArray recipesOut = new JSONArray();
            for (Map.Entry<String, JSONObject> e : recipeMap.entrySet()) recipesOut.put(e.getValue());

            System.out.println("成功從資料庫載入 " + data.length() + " 條食譜步驟（分組為 " + recipesOut.length() + " 個 recipe）");
            return recipesOut;
        } catch (Exception e) {
            System.err.println("讀取資料庫食譜失敗: " + e.getMessage());
            return new JSONArray();
        }
    }


    // 在 DBRequest.java 中新增
    public static JSONArray loadIngredients() {
        try {
            // 請確保你的伺服器上有 get_ingredients.php
            JSONObject response = httpGet(API_BASE + "/get_ingredients.php"); 
            return response.getJSONArray("data");
        } catch (Exception e) {
            System.err.println("讀取原料表失敗: " + e.getMessage());
            return new JSONArray();
        }
    }

    // --- 【新增】BOM 自動解析功能 ---
    // 直接沿用 loadRecipes() 的資料源，供 GUI 進行解析與顯示
    public static JSONArray loadAllBOMs() {
        return loadRecipes();
    }
    // ----------------------------

    /**
     * 專門讀取 McOS_mealCost 表作為 BOM 資料源
     */
    public static JSONArray loadBOMData() {
        try {
            // 注意：請確保你有對應的 get_mealcost.php 檔案在伺服器上
            JSONObject response = httpGet(API_BASE + "/get_mealcost.php"); 
            return response.getJSONArray("data");
        } catch (Exception e) {
            System.err.println("讀取 BOM (mealCost) 失敗: " + e.getMessage());
            return new JSONArray();
        }
    }


    /**
     * 根據餐點名稱尋找食譜，若找不到回傳 null
     */
    public static JSONObject getRecipeByMealName(String mealName) {
        try {
            JSONArray recipes = loadRecipes();
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject r = recipes.getJSONObject(i);
                if (r.has("meal_name") && mealName.equals(r.getString("meal_name"))) {
                    return r;
                }
            }
        } catch (Exception e) {
            System.err.println("getRecipeByMealName error: " + e.getMessage());
        }
        return null;
    }

     public static WorkerRoster loadWorkerRoster() {
        JSONArray workersJson = loadWorkers();
        ArrayList<JSONObject> workers = new ArrayList<>();
        ArrayList<Integer> workerIds = new ArrayList<>();
        LinkedHashMap<Integer, String> workerNames = new LinkedHashMap<>();

        for (int i = 0; i < workersJson.length(); i++) {
            JSONObject worker = workersJson.getJSONObject(i);
            workers.add(worker);
            int workerId = worker.optInt("workerID", 0);
            if (workerId <= 0) continue;
            String name = worker.optString("name", "Worker " + workerId);
            workerNames.put(workerId, name);
            workerIds.add(workerId);
        }

        if (workerIds.isEmpty()) {
            workerIds.add(1);
            workerNames.put(1, "Worker 1");
        }

        System.out.println("從資料庫 API 載入 " + workerIds.size() + " 位員工");
        return new WorkerRoster(workers, workerIds, workerNames);
    }

    public static JSONArray queryMeals() throws Exception {
        JSONObject response = httpGet(MEALS_URL);
        return response.getJSONArray("data");
    }

    public static JSONArray queryCombos() throws Exception {
        JSONObject response = httpGet(COMBOS_URL);
        if (!response.has("data")) {
            return new JSONArray();
        }
        return response.getJSONArray("data");
    }

        /**
     * 查詢指定日期消耗量，並合併當前庫存狀態
     */
    public static JSONArray getConsumptionWithInventory(String date) throws Exception {
        // 這裡假設後端有寫一個 PHP 整合 API (例如 get_consumption_report.php)
        // 該 API 內部會執行 SQL JOIN 或將兩張 View 的結果在 PHP 端合併後回傳
        String url = API_BASE + "/get_consumption_report.php?date=" + date;
        JSONObject response = httpGet(url);
        return response.getJSONArray("data");
    }

        /**
     * 查詢指定日期的原料消耗統計
     * @param date 日期格式 YYYY-MM-DD
     */
    public static JSONArray getDailyConsumption(String date) throws Exception {
        // 假設您的後端有一個 API 可以根據日期篩選 View_Daily_Total_Consumption
        // 例如: get_daily_consumption.php?date=2026-04-22
        String url = API_BASE + "/get_daily_consumption.php?date=" + date;
        JSONObject response = httpGet(url);
        if (response.has("data")) {
            return response.getJSONArray("data");
        }
        return new JSONArray();
    }

    public static JSONArray loadCustomers() {
        try {
            JSONObject response = httpGet(CUSTOMERS_URL);
            if (response.has("data")) {
                return response.getJSONArray("data");
            }
        } catch (Exception e) {
            System.err.println("讀取顧客資料失敗: " + e.getMessage());
        }
        return new JSONArray();
    }

    public static JSONObject consumeInventory(JSONArray itemNames, String orderId, String completedAt) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("order_id", orderId);
        payload.put("completed_at", completedAt);
        payload.put("items", itemNames);
        payload.put("consumptions", buildConsumptionPayload(itemNames));
        return httpPost(CONSUME_INVENTORY_URL, payload.toString());
    }

    public static JSONObject recordOrderHistory(JSONObject orderInfo) throws Exception {
        JSONObject payload = new JSONObject(orderInfo.toString());
        return httpPost(ORDER_HISTORY_URL, payload.toString());
    }

    public static JSONObject purchaseIngredient(String ingId, double qty) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("ing_id", ingId);
        payload.put("qty", qty);
        return httpPost(PURCHASE_INGREDIENT_URL, payload.toString());
    }

    /**
     * 根據套餐名稱查詢套餐食材
     * food_items 是逗號分隔的 meal_id，prep_time 來自食譜步驟加總
     * @param comboName 套餐名稱
     * @return JSONArray of food items with {item, prep_time}
     */
    public static JSONArray getComboItems(String comboName) throws Exception {
        LinkedHashMap<String, Integer> recipePrepTimes = buildRecipePrepTimeMap();
        JSONArray combos = queryCombos();
        JSONArray allMeals = queryMeals();

        Map<Integer, JSONObject> mealMap = new HashMap<>();
        for (int i = 0; i < allMeals.length(); i++) {
            JSONObject meal = allMeals.getJSONObject(i);
            mealMap.put(meal.getInt("meal_id"), meal);
        }

        LinkedHashMap<String, ArrayList<Integer>> comboMeals = parseComboMeals(combos);
        if (comboMeals.containsKey(comboName)) {
            ArrayList<Integer> mealIds = comboMeals.get(comboName);
            JSONArray items = new JSONArray();
            for (int mealId : mealIds) {
                if (mealMap.containsKey(mealId)) {
                    JSONObject meal = mealMap.get(mealId);
                    JSONObject item = new JSONObject();
                    String mealName = meal.getString("meal_name");
                    item.put("item", mealName);
                    int prepTime = recipePrepTimes.getOrDefault(mealName, 0);
                    if (prepTime <= 0) {
                        int rawPrepTime = meal.optInt("prep_time", -1);
                        if (rawPrepTime > 0) {
                            prepTime = normalizePrepTimeSeconds(rawPrepTime);
                        }
                    }
                    if (prepTime <= 0) {
                        prepTime = normalizePrepTimeSeconds(5);
                    }
                    item.put("prep_time", prepTime);
                    items.put(item);
                }
            }
            return items;
        }

        return new JSONArray();
    }

    
    /**
     * 新增套餐
     * @param comboName 套餐名稱
     * @param foodItems JSONArray of {item, prep_time}
     * @return combo_id of new combo
     */
    public static int addCombo(String comboName, JSONArray foodItems) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("combo_name", comboName);
        payload.put("food_items", foodItems);

        JSONObject response = httpPost(COMBO_CRUD_URL + "?action=insert", payload.toString());
        return response.getInt("combo_id");
    }

    /**
     * 刪除套餐
     * @param comboId 套餐ID
     * @return affected rows
     */
    public static int deleteCombo(int comboId) throws Exception {
        JSONObject response = httpGet(COMBO_CRUD_URL + "?action=delete&id=" + comboId);
        return response.optInt("affected_rows", 0);
    }

    public static JSONObject httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return new JSONObject(response.toString().trim());
        }
    }

    public static JSONObject httpPost(String urlString, String payload) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return new JSONObject(response.toString().trim());
        }
    }


    public static JSONObject getComboBomExplosion() throws Exception {
    // 🎯 直接複製這行！使用你們專案統一的 API_BASE 變數，就不會走錯門牌了
    String url = API_BASE + "/get_combo_bom_explosion.php"; 
    
    JSONObject response = httpGet(url);
    return response;
}

    // CRUD_URL is retained for compatibility with other modules that may build URLs directly.
    public static String getCrudUrl() {
        return CRUD_URL;
    }
}