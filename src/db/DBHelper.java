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
    
    public static JSONArray queryMeals() throws Exception {
        JSONObject response = httpGet(MEALS_URL);
        return response.getJSONArray("data");
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
