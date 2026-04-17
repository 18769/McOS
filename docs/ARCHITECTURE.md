# McOS - 項目架構

## 📁 項目結構

```
McOS/
├── src/                          # Java 源代碼
│   ├── gui/                      # GUI 層
│   │   └── KitchenGUI.java       # 主程式
│   └── db/                       # 數據訪問層
│       └── DBHelper.java         # 數據庫助手類
│
├── php/                          # PHP 後端 API
│   └── api/                      # 數據庫 API
│       ├── get_meals.php         # 查詢餐點 API
│       ├── test_db.php           # 通用數據庫 API
│       └── setup_meal_table.php  # 表初始化腳本
│
├── lib/                          # Java 依賴庫
│   └── json-20240303.jar         # JSON 處理庫
│
├── bin/                          # 編譯輸出目錄（運行時生成）
│
├── build_and_run.bat             # Windows 編譯和運行腳本
│
├── DB/                           # 舊的本地數據文件（已棄用）
│   └── meal.json                 # 本地餐點數據（不再使用）
│
├── QUICKSTART.md                 # 快速開始指南
├── DEVELOPMENT.md                # 開發文檔
├── ARCHITECTURE.md               # 本文件
└── README.md                     # 項目簡介
```

## 🏗️ 系統架構

### 核心架構圖

```
┌─────────────────────────────────────────────┐
│  KitchenGUI (Java Swing)                    │
│  Package: gui.KitchenGUI                    │
│  責任: 用戶界面、訂單管理、時間計時        │
└──────────────┬──────────────────────────────┘
               │
               │ 調用
               ▼
┌─────────────────────────────────────────────┐
│  DBHelper (數據訪問層)                      │
│  Package: db.DBHelper                       │
│  責任: 統一的數據庫訪問接口                │
└──────────────┬──────────────────────────────┘
               │
               │ HTTP GET
               ▼
┌─────────────────────────────────────────────┐
│  PHP API (Backend)                          │
│  - get_meals.php: 查詢所有餐點             │
│  - test_db.php: 通用數據庫操作             │
│  位置: http://120.107.152.110/~a0303/DB/  │
└──────────────┬──────────────────────────────┘
               │
               │ SQL Query
               ▼
┌─────────────────────────────────────────────┐
│  MySQL Database (a0303)                     │
│  表: McOS_meal (13個餐點)                  │
│  欄位: meal_id, meal_name, prep_time       │
└─────────────────────────────────────────────┘
```

### 數據流向

1. **應用啟動**
   - `KitchenGUI.main()` → `loadMeals()` 
   - 調用 `DBHelper.queryMeals()`

2. **查詢餐點**
   - `DBHelper.queryMeals()` 
   → HTTP GET `get_meals.php`
   → PHP 執行 `SELECT * FROM McOS_meal`
   → MySQL 返回結果
   → JSON 返回給 Java

3. **顯示 UI**
   - `mealPrepTimes` 裡填充 (餐點名, 準備時間)
   - 生成菜單按鈕，用戶可點擊下單

4. **訂單處理**
   - 用戶選擇餐點和數量
   - `addOrderToBuffer()` 添加到訂單隊列
   - 開始倒計時顯示

## 🔧 配置文件

### PHP API URL

所有 API 位置（伺服器側）：
- `http://120.107.152.110/~a0303/DB/get_meals.php`
- `http://120.107.152.110/~a0303/DB/test_db.php`
- `http://120.107.152.110/~a0303/DB/setup_meal_table.php`

在 `src/db/DBHelper.java` 中配置：
```java
private static final String PHP_API_URL = "http://120.107.152.110/~a0303/DB/test_db.php";
private static final String MEALS_API_URL = "http://120.107.152.110/~a0303/DB/get_meals.php";
```

### 數據庫連接

在 PHP 文件中配置（`php/api/*.php`）：
```php
$db_host = 'localhost';
$db_user = 'a0303';
$db_pass = 'pwd0303';
$db_name = 'a0303';
```

## 📦 依賴

### Java 依賴
- **json-20240303.jar** - JSON 數據處理
  - 位置: `lib/json-20240303.jar`
  - 用於: Java 與 PHP API 的 JSON 通信

### PHP 依賴
- MySQL (學校伺服器已提供)
- Apache (學校伺服器已提供)

## 🚀 編譯和運行

### Windows

**方式 1：雙擊批文件**
```
build_and_run.bat
```

**方式 2：命令行**
```cmd
cd C:\Users\ken7y\OneDrive\文件\GitHub\McOS
build_and_run.bat
```

### Linux/Mac

```bash
cd src
javac -encoding UTF-8 -cp ../lib/json-20240303.jar -d ../bin db/DBHelper.java gui/KitchenGUI.java
cd ..
java -cp bin:lib/json-20240303.jar gui.KitchenGUI
```

## 📝 文件說明

### Java 文件

| 文件 | Package | 功能 |
|------|---------|------|
| KitchenGUI.java | gui | 主 GUI 界面，訂單管理 |
| DBHelper.java | db | 數據訪問層，數據庫操作 |

### PHP 文件

| 文件 | 功能 |
|------|------|
| get_meals.php | 專用 API：查詢 McOS_meal 表 |
| test_db.php | 通用 API：支持 query, insert, update, delete |
| setup_meal_table.php | 初始化腳本：建立 McOS_meal 表並插入 13 個餐點 |

## 🔄 數據庫表結構

### McOS_meal

```sql
CREATE TABLE McOS_meal (
    meal_id INT AUTO_INCREMENT PRIMARY KEY,
    meal_name VARCHAR(100) NOT NULL UNIQUE,
    prep_time INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**示例數據 (13 個餐點)**

| meal_id | meal_name | prep_time |
|---------|-----------|-----------|
| 1 | 大麥克 | 8 |
| 2 | 小麥克 | 6 |
| 3 | 麥克 | 7 |
| 4 | 薯條 | 3 |
| 5 | 雞塊 | 5 |
| 6 | 蘋果派 | 4 |
| 7 | 玉米湯 | 2 |
| 8 | 可樂 | 1 |
| 9 | 舊東洋熱狗 | 5 |
| 10 | 冰美式 | 3 |
| 11 | 大麥克預設 | 0 |
| 12 | 雞塊特餐 | 0 |
| 13 | (user-added) | (custom) |

## 🔑 關鍵設計決策

1. **使用 Package 組織代碼**
   - `gui.*` - 所有用戶界面相關
   - `db.*` - 所有數據庫相關

2. **HTTP API 作為中間層**
   - Java 無法直接連接伺服器上的 MySQL（防火牆限制）
   - 使用 PHP 作為中介
   - 優點：跨平台、易於維護、安全

3. **分離餐點表 (McOS_meal)**
   - 不使用學校現有的 Product 表
   - 避免與其他課程作業混淆
   - 獨立控制數據結構

4. **只讀餐點 API**
   - `get_meals.php` 專用於讀取餐點
   - 簡潔、高效、易於緩存

## 🐛 故障排查

### GUI 無法讀取餐點
- 檢查 `get_meals.php` 是否已上傳到伺服器
- 檢查 MySQL 連接是否正常
- 查看 Java 控制臺輸出的錯誤信息

### 修改數據庫後 GUI 無法更新
- 重新啟動 GUI（應用程式啟動時載入餐點）
- 確認 PHP API 返回的是最新數據

### PHP 語法錯誤
- 檢查文件編碼 (UTF-8 BOM 可能導致問題)
- 查看伺服器錯誤日誌

## 📚 相關文檔

- **QUICKSTART.md** - 5 分鐘快速開始
- **DEVELOPMENT.md** - 詳細開發指南
- **README.md** - 項目概述

---

**最後更新**: 2026-04-17  
**版本**: 1.0.0
