# McOS - Kitchen Order Management System

> 🍔 一個廚房訂單管理系統，用 Java Swing GUI 處理訂單，支持實時倒計時

## ✨ 功能特性

- ✅ 圖形化訂單界面（Java Swing）
- ✅ 從資料庫動態載入餐點菜單
- ✅ 支持外帶/內用標記
- ✅ 實時訂單計時系統
- ✅ 訂單隊列管理
- ✅ 雲端數據庫存儲（MySQL）

## 🚀 快速開始

### 前置條件
- Windows 10+
- JDK 26.0.1+ (java 和 javac 在 PATH 中)
- 網絡連接到學校伺服器

### 運行方式

**最簡單的方式 - 雙擊文件**
```
build_and_run.bat
```

**或命令行**
```cmd
cd C:\Users\ken7y\OneDrive\文件\GitHub\McOS
build_and_run.bat
```

## 📋 項目結構

```
src/
├── gui/          ← GUI 層 (KitchenGUI.java)
└── db/           ← 數據層 (DBHelper.java)

php/api/         ← 後端 API (只需 get_meals.php)

lib/             ← 依賴庫 (json-20240303.jar)

docs/            ← 詳細文檔
```

更詳細的架構說明見 **docs/ARCHITECTURE.md**

## 🏗️ 系統架構

```
Java GUI ← HTTP → PHP API ← SQL → MySQL Database
```

- **Java GUI** (KitchenGUI): 用戶界面、訂單管理
- **PHP API** (get_meals.php): 數據查詢接口
- **MySQL** (McOS_meal 表): 13 個餐點數據

## 🔧 配置

### 數據庫配置

伺服器地址：`120.107.152.110`  
數據庫名：`a0303`  
餐點表：`McOS_meal`

在 `src/db/DBHelper.java` 中修改 API URL：

```java
private static final String MEALS_API_URL = "http://120.107.152.110/~a0303/DB/get_meals.php";
```

## 📊 餐點菜單

系統已預置 13 個餐點：
- 大麥克 (8秒)
- 小麥克 (6秒)
- 麥克 (7秒)
- 薯條 (3秒)
- 雞塊 (5秒)
- 蘋果派 (4秒)
- 玉米湯 (2秒)
- 可樂 (1秒)
- 舊東洋熱狗 (5秒)
- 冰美式 (3秒)
- 大麥克預設 (0秒)
- 雞塊特餐 (0秒)
- (可自定添加更多)

## 💾 數據庫修改

修改數據庫中的餐點信息後，只需重新啟動 GUI 應用即可讀取最新數據。

## 📚 文檔

查看 **docs/** 目錄以獲取詳細信息：

- **docs/ARCHITECTURE.md** - 系統架構詳解
- **docs/DEVELOPMENT.md** - 開發指南
- **docs/UPLOAD_CHECKLIST.md** - 上傳清單
- **docs/SIMPLIFIED_ARCHITECTURE.md** - 簡化架構

## 👥 開發環境

- Python 3.13
- JDK 26
- MySQL (學校伺服器)

## 🐛 問題報告

遇到問題？查看 DEVELOPMENT.md 中的故障排查部分

---

**最後更新**: 2026-04-17  
**版本**: 1.0.0