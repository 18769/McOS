# McOS 智慧廚房系統 - 開發指南

## 專案結構

```
McOS/
├── src/
│   ├── KitchenGUI.java          ← 主程式（Swing UI）
│   ├── DBHelper.java            ← 資料庫操作介面
│   └── algo/
│       └── scheduler.py         ← 排程演算法
├── php/
│   └── test_db.php              ← 後端 API（MySQL 中介層）
├── DB/
│   └── meal.json                ← 本地餐點資料
├── bin/                         ← 編譯輸出
├── lib/
│   ├── mysql-connector-j-9.6.0.jar
│   └── json-20240303.jar
└── data/                        ← 訂單備份
```

## 系統架構

```
【本地開發機】
┌──────────────────┐
│   KitchenGUI     │ ←── 使用者介面
│   (Java Swing)   │
└─────────┬────────┘
          │ HTTP 呼叫
          ▼
【學校伺服器】
┌──────────────────┐
│   PHP API        │ ← test_db.php
│ (~/a0303/DB/)    │
└─────────┬────────┘
          │ SQL 查詢
          ▼
┌──────────────────┐
│   MySQL 資料庫   │
│   (localhost)    │
└──────────────────┘
```

## 編譯和執行

### 1. 編譯

```bash
javac -encoding UTF-8 -cp "lib\mysql-connector-j-9.6.0.jar;lib\json-20240303.jar" -d bin src\KitchenGUI.java
```

### 2. 執行

```bash
java -cp "bin;lib\mysql-connector-j-9.6.0.jar;lib\json-20240303.jar" KitchenGUI
```

或使用批次檔：`run.bat` 或 `run_demo.bat`

## 資料庫連線

### PHP API 位置
- URL: `http://120.107.152.110/~a0303/DB/test_db.php`
- 檔案: `/public_html/DB/test_db.php`（FTP 路徑）

### 支持的操作

| 操作 | URL 參數 | 說明 |
|------|---------|------|
| 測試連線 | `?action=test` | 驗證資料庫連線 |
| 查詢資料表 | `?action=query&table=Product` | 列出所有資料 |
| 查詢單筆 | `?action=query_by_id&table=Product&id_column=id&id=1` | 按 ID 查詢 |
| 插入資料 | `?action=insert&table=Order&data={"..."}` | 插入新記錄 |
| 更新資料 | `?action=update&table=Order&id_column=order_id&id=1&data={"..."}` | 更新記錄 |
| 刪除資料 | `?action=delete&table=Order&id_column=order_id&id=1` | 刪除記錄 |

### 在 Java 中使用 DBHelper

```java
// 查詢產品
JSONArray products = DBHelper.queryProducts();

// 查詢指定資料表
JSONArray orders = DBHelper.queryTable("Order");

// 插入訂單
JSONObject newOrder = new JSONObject();
newOrder.put("customer", "客戶名稱");
newOrder.put("total", 500);
DBHelper.insertOrder(newOrder);

// 更新訂單狀態
DBHelper.updateOrderStatus("order_id_123", "completed");

// 刪除訂單
DBHelper.deleteOrder("order_id_123");

// 測試連線
DBHelper.testConnection();
```

## 開發工作流

1. **修改 Java 代碼** → `src/KitchenGUI.java` 或 `src/DBHelper.java`
2. **編譯** → `javac ...`
3. **測試執行** → `java KitchenGUI`
4. **提交代碼** → Git commit

## 常見問題

### Q: 資料庫連不上？
A: 確認：
1. PHP 檔案已上傳到 `http://120.107.152.110/~a0303/DB/test_db.php`
2. 伺服器可正常存取（瀏覽器測試）
3. 帳號密碼正確（a0303 / pwd0303）

### Q: 編譯失敗？
A: 確認：
1. Java JDK 已安裝
2. 依賴 jar 檔在 `lib/` 目錄
3. 使用 UTF-8 編碼：`-encoding UTF-8`

### Q: 想用直接 JDBC 連線？
A: 當學校 IT 開放埠 3306 後，可改用 `MySQLDirectConnection.java`（參考備用方案）

## 資料庫結構

學校資料庫（a0303）包含以下資料表：
- Author（作者）
- Browse（瀏覽記錄）
- Cart（購物車）
- Member（會員）
- Order（訂單）
- Product（產品）
- Transaction（交易記錄）

## 備註

- 本地 `DB/meal.json` 用於開發測試
- 生產環境應連接遠端 MySQL 資料庫
- 訂單自動備份到 `data/` 資料夾
