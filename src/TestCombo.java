import db.DBHelper;
import org.json.JSONArray;
import org.json.JSONObject;

public class TestCombo {
    public static void main(String[] args) {
        try {
            System.out.println("=== 測試套餐系統 ===\n");
            
            // 1. 查詢所有套餐
            System.out.println("1. 查詢所有套餐:");
            JSONArray combos = DBHelper.queryCombos();
            System.out.println("   回傳: " + combos.toString(2));
            
            // 2. 如果有套餐，測試拆解
            if (combos.length() > 0) {
                System.out.println("\n2. 測試拆解第一個套餐:");
                JSONObject firstCombo = combos.getJSONObject(0);
                String comboName = firstCombo.getString("combo_name");
                System.out.println("   套餐名: " + comboName);
                
                JSONArray items = DBHelper.getComboItems(comboName);
                System.out.println("   拆解後食材:");
                System.out.println(items.toString(2));
            } else {
                System.out.println("\n   [提示] 資料庫中還沒有套餐，請先建立");
            }
            
        } catch (Exception e) {
            System.out.println("[錯誤] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
