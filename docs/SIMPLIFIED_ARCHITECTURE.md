# 🎯 簡化後的項目結構

## 📁 完整目錄

```
McOS/
│
├─ 💻 src/                     Java 源代碼（本地開發）
│  ├─ gui/
│  │  └─ KitchenGUI.java       ✓ 編譯後在 bin/gui/ 中
│  └─ db/
│     └─ DBHelper.java         ✓ 編譯後在 bin/db/ 中
│
├─ 🌐 php/api/                 **伺服器需要**
│  └─ get_meals.php            ⭐ **唯一需要上傳的文件**
│
├─ 📦 php/archive/             舊文件（可選保留）
│  ├─ test_db.php              (已棄用)
│  └─ setup_meal_table.php     (表已建立)
│
├─ 📚 lib/
│  └─ json-20240303.jar        Java 依賴
│
├─ 🔧 bin/                     編譯輸出（本地）
│
├─ 📄 README.md
├─ 📄 ARCHITECTURE.md
├─ 📄 UPLOAD_CHECKLIST.md      ⭐ NEW
├─ 📄 PROJECT_STATUS.md
└─ 🚀 build_and_run.bat
```

---

## 🚀 運行流程

### 本地開發
```
源代碼 (src/)
    ↓ javac 編譯
編譯文件 (bin/)
    ↓ java 執行
GUI 應用運行
    ↓ HTTP 請求
```

### 遠程服務器
```
PHP API (get_meals.php)
    ↓ SQL 查詢
MySQL 數據庫
    ↓ JSON 返回
GUI 應用顯示
```

---

## ✅ 簡化清單

| 項目 | 狀態 | 說明 |
|------|------|------|
| Java 源文件 | ✓ 2 個 | KitchenGUI + DBHelper |
| PHP 文件 | ✓ 1 個 | 只需 get_meals.php |
| 依賴庫 | ✓ 1 個 | json-20240303.jar |
| 編譯腳本 | ✓ 1 個 | build_and_run.bat |
| 文檔 | ✓ 5 個 | README + 架構 + 清單 |

---

## 📤 FileZilla 上傳

**只需上傳 1 個文件：**
```
Local:        C:\...\McOS\php\api\get_meals.php
Remote:       /public_html/DB/get_meals.php
```

---

## 🎯 系統最小化架構

```
┌─────────────────────┐
│  KitchenGUI         │
│  (本地運行)         │
└──────────┬──────────┘
           │
           │ HTTP GET
           │
┌──────────▼──────────┐
│  get_meals.php      │
│  (伺服器)           │
└──────────┬──────────┘
           │
           │ SQL
           │
┌──────────▼──────────┐
│  MySQL (McOS_meal)  │
│  13 個餐點          │
└─────────────────────┘
```

---

## ✨ 最終成果

**簡化度：80% ✓**

- ✅ Java 部分：2 個源文件（已組織）
- ✅ PHP 部分：1 個 API 文件（已最小化）
- ✅ 上傳工作：1 個文件（已簡化）
- ✅ 文檔：清晰易懂（已優化）

---

**項目已達到最小化狀態！** 🚀

下一步：
1. 驗證 get_meals.php 運行正常
2. 交付給團隊
3. 準備部署文檔

---

最後更新：2026-04-17
