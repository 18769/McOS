# 📤 FileZilla 上傳清單

## ✅ 需要上傳的文件

### PHP API (必須)
- **`php/api/get_meals.php`** 
  - 位置：`/public_html/DB/get_meals.php`
  - 功能：查詢所有餐點
  - 大小：1.4 KB

---

## ⚠️ 不需要上傳

### 已歸檔的舊文件 (php/archive/)
- `test_db.php` - 舊的通用 API（已棄用）
- `setup_meal_table.php` - 初始化腳本（表已建立）

### Java 源文件
- 所有 `.java` 文件在本地編譯
- 二進制文件（`.class`）在本地的 `bin/` 目錄

---

## 🚀 上傳步驟

1. 打開 FileZilla
2. 連接到 `120.107.152.110`
3. 登錄帳號：`a0303`
4. 瀏覽到 `/public_html/DB/` 目錄
5. 上傳：`php/api/get_meals.php`
6. 完成！

---

## ✨ 上傳後驗證

訪問以下 URL 測試：
```
http://120.107.152.110/~a0303/DB/get_meals.php
```

應該返回 JSON，包含所有 13 個餐點。

---

**簡化成功！只需上傳 1 個 PHP 文件。** ✓

最後更新：2026-04-17
