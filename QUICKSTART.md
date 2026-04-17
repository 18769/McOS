# McOS 快速開始

## 1️⃣ 前置要求

- ✅ Java JDK 11+ 已安裝
- ✅ PHP 檔案已上傳到伺服器 (`http://120.107.152.110/~a0303/DB/get_meals.php`)
- ✅ 學校 MySQL 可正常存取

## 2️⃣ 執行程式

**方式 A: 雙擊批次檔（最簡單）**
```
build_and_run.bat
```

**方式 B: 命令列執行**
```cmd
javac -encoding UTF-8 -cp "lib\mysql-connector-j-9.6.0.jar;lib\json-20240303.jar" -d bin src\KitchenGUI.java
java -cp "bin;lib\mysql-connector-j-9.6.0.jar;lib\json-20240303.jar" KitchenGUI
```

## 3️⃣ 驗證資料庫連線

執行程式後，檢查狀態欄應該顯示：`● 系統狀態: 已連線資料庫`

## 4️⃣ 開始開發

編輯以下檔案：
- `src/KitchenGUI.java` - UI 邏輯
- `src/DBHelper.java` - 資料庫操作

使用 `DBHelper` 的方法查詢/存儲資料：
```java
// 查詢
JSONArray products = DBHelper.queryProducts();

// 插入
JSONObject order = new JSONObject();
order.put("customer", "Name");
DBHelper.insertOrder(order);

// 更新
DBHelper.updateOrderStatus(orderId, "completed");

// 刪除
DBHelper.deleteOrder(orderId);
```

## 5️⃣ 完整文檔

參考 `docs/DEVELOPMENT.md` 獲取詳細資訊。

---

**有問題？** 查看 docs/DEVELOPMENT.md 的「常見問題」部分。
