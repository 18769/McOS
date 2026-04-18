package db;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

public class DBHelper {
    private static final String API_BASE = "http://120.107.152.110/~a0303/DB";
    private static final String MEALS_URL = API_BASE + "/get_meals.php";
    private static final String CRUD_URL = API_BASE + "/crud.php";
    private static final String COMBOS_URL = API_BASE + "/get_combos.php";
    
    public static JSONArray queryMeals() throws Exception {
        JSONObject response = httpGet(MEALS_URL);
        return response.getJSONArray("data");
    }
    
    public static JSONArray queryCombos() throws Exception {
        JSONObject response = httpGet(COMBOS_URL);
        return response.getJSONArray("data");
    }
    
    public static JSONArray getComboItems(String comboName) throws Exception {
        JSONArray combos = queryCombos();
        JSONArray allMeals = queryMeals();
        java.util.Map<Integer, JSONObject> mealMap = new java.util.HashMap<>();
        for (int i = 0; i < allMeals.length(); i++) {
            JSONObject meal = allMeals.getJSONObject(i);
            mealMap.put(meal.getInt("meal_id"), meal);
        }
        for (int i = 0; i < combos.length(); i++) {
            JSONObject combo = combos.getJSONObject(i);
            if (combo.getString("combo_name").equals(comboName)) {
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
    
    public static JSONObject httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return new JSONObject(response.toString().trim());
    }
}
