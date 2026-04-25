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
import java.nio.file.Files;
import java.nio.file.Paths;

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

    public static LinkedHashMap<String, Integer> loadMeals() {
        LinkedHashMap<String, Integer> mealPrepTimes = new LinkedHashMap<>();
        try {
            JSONArray meals = queryMeals();
            System.out.println("=== 餐點資料調試 ===");
            for (int i = 0; i < meals.length(); i++) {
                JSONObject m = meals.getJSONObject(i);
                String mealName = m.getString("meal_name");
                int prepTime = m.getInt("prep_time");
                System.out.println("  餐點: " + mealName + " | 準備時間: " + prepTime);
                mealPrepTimes.put(mealName, prepTime);
            }
            System.out.println("從資料庫成功載入 " + meals.length() + " 個餐點\n");
        } catch (Exception e) {
            System.err.println("讀取資料庫失敗，改用預設資料: " + e.getMessage());
            e.printStackTrace();
            mealPrepTimes.put("大麥克", 8);
            mealPrepTimes.put("小麥克", 6);
            mealPrepTimes.put("麥克", 7);
            mealPrepTimes.put("薯條", 3);
            mealPrepTimes.put("雞塊", 5);
            mealPrepTimes.put("蘋果派", 4);
            mealPrepTimes.put("玉米湯", 2);
            mealPrepTimes.put("可樂", 1);
            mealPrepTimes.put("舊東洋熱狗", 5);
            mealPrepTimes.put("冰美式", 3);
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
            for (int i = 0; i < combosArray.length(); i++) {
                JSONObject c = combosArray.getJSONObject(i);
                String comboName = c.getString("combo_name");
                String foodItems = c.getString("food_items");
                System.out.println("  套餐: " + comboName + " | 食材: " + foodItems);
                combos.put(comboName, foodItems);
            }
            System.out.println("從資料庫成功載入 " + combosArray.length() + " 個套餐\n");
        } catch (Exception e) {
            System.err.println("讀取套餐失敗: " + e.getMessage());
        }
        return combos;
    }

    public static JSONArray loadWorkers() {
        String path = "DB/worker.json";
        try {
            String content = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
            return new JSONArray(content);
        } catch (Exception e) {
            System.err.println("讀取員工資料失敗: " + e.getMessage());
            return new JSONArray();
        }
    }

    public static WorkerRoster loadWorkerRoster() {
        JSONArray workersJson = loadWorkers();
        ArrayList<JSONObject> workers = new ArrayList<>();
        ArrayList<Integer> workerIds = new ArrayList<>();
        LinkedHashMap<Integer, String> workerNames = new LinkedHashMap<>();

        for (int i = 0; i < workersJson.length(); i++) {
            JSONObject worker = workersJson.getJSONObject(i);
            workers.add(worker);
            int workerId = worker.optInt("worker_id", 0);
            if (workerId <= 0) {
                continue;
            }
            String name = worker.optString("name", "Worker " + workerId);
            workerNames.put(workerId, name);
            workerIds.add(workerId);
        }

        if (workerIds.isEmpty()) {
            workerIds.add(1);
            workerNames.put(1, "Worker 1");
        }

        System.out.println("從 worker.json 載入 " + workerIds.size() + " 位員工");
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
     * 根據套餐名稱查詢套餐食材
     * food_items 是逗號分隔的 meal_id，需要查詢 McOS_meal 取得 prep_time
     * @param comboName 套餐名稱
     * @return JSONArray of food items with {item, prep_time}
     */
    public static JSONArray getComboItems(String comboName) throws Exception {
        JSONArray combos = queryCombos();
        JSONArray allMeals = queryMeals();

        Map<Integer, JSONObject> mealMap = new HashMap<>();
        for (int i = 0; i < allMeals.length(); i++) {
            JSONObject meal = allMeals.getJSONObject(i);
            mealMap.put(meal.getInt("meal_id"), meal);
        }

        for (int i = 0; i < combos.length(); i++) {
            JSONObject combo = combos.getJSONObject(i);
            if (comboName.equals(combo.getString("combo_name"))) {
                String foodItemsStr = combo.getString("food_items");
                String[] mealIds = foodItemsStr.split(",");

                JSONArray items = new JSONArray();
                for (String mealIdStr : mealIds) {
                    int mealId = Integer.parseInt(mealIdStr.trim());
                    if (mealMap.containsKey(mealId)) {
                        JSONObject meal = mealMap.get(mealId);
                        JSONObject item = new JSONObject();
                        item.put("item", meal.getString("meal_name"));
                        item.put("prep_time", meal.getInt("prep_time"));
                        items.put(item);
                    }
                }
                return items;
            }
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

    // CRUD_URL is retained for compatibility with other modules that may build URLs directly.
    public static String getCrudUrl() {
        return CRUD_URL;
    }
}
