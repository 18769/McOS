# 📚 數據庫接手指南

## 👥 角色分工

### 你的部分 ✅ (已完成)
- Java GUI 應用 (src/gui/)
- 數據訪問層 (src/db/)
- API 集成層 (php/api/get_meals.php)

### Teammate 的部分 📋 (待接手)
- **MySQL 數據庫管理**
- **餐點數據維護** (CRUD 操作)
- **新增功能表設計**（未來擴展）

---

## 🗂️ 數據庫架構

### 當前表結構

#### McOS_meal 表（餐點表）

```sql
CREATE TABLE McOS_meal (
    meal_id INT AUTO_INCREMENT PRIMARY KEY,
    meal_name VARCHAR(100) NOT NULL UNIQUE,
    prep_time INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**欄位說明：**
- `meal_id` - 唯一識別符
- `meal_name` - 餐點名稱（不能重複）
- `prep_time` - 準備時間（秒）
- `created_at` - 建立時間（自動）
- `updated_at` - 更新時間（自動）

**現有 13 個餐點：**
```
大麥克 (8秒), 小麥克 (6秒), 麥克 (7秒), 薯條 (3秒), 
雞塊 (5秒), 蘋果派 (4秒), 玉米湯 (2秒), 可樂 (1秒),
舊東洋熱狗 (5秒), 冰美式 (3秒), 大麥克預設 (0秒), 
雞塊特餐 (0秒), + 1 個自定義餐點
```

---

## 🔧 數據庫管理方式

### 方案 1：直接用 phpMyAdmin ✅ **推薦初期**

**優點：**
- 無需編寫代碼
- 直接在圖形界面操作
- 安全、簡單

**操作流程：**
```
1. 訪問 http://120.107.152.110/phpmyadmin
2. 登錄 (帳號: a0303, 密碼: pwd0303)
3. 進入 a0303 數據庫
4. 編輯 McOS_meal 表
5. 新增/編輯/刪除餐點
```

**常見操作：**
```sql
-- 查看所有餐點
SELECT * FROM McOS_meal;

-- 新增餐點
INSERT INTO McOS_meal (meal_name, prep_time) VALUES ('新餐點', 10);

-- 編輯餐點
UPDATE McOS_meal SET prep_time = 5 WHERE meal_name = '新餐點';

-- 刪除餐點
DELETE FROM McOS_meal WHERE meal_id = 14;
```

---

### 方案 2：寫專用 PHP 管理程序 ✅ **推薦專業使用**

為 teammate 創建一個 PHP 管理面板（後台）：

**功能清單：**
- ✅ 查看所有餐點
- ✅ 新增餐點
- ✅ 編輯餐點
- ✅ 刪除餐點
- ✅ 導入/導出 CSV

**檔案位置：** `php/admin/meal_manager.php`

---

## 💡 建議的做法

### 短期（現在）
1. **Teammate 直接用 phpMyAdmin**
   - 登錄並操作
   - 無需學習 SQL

### 中期（1-2周）
2. **如果操作頻繁，建立 PHP 管理後台**
   - 更友好的界面
   - 批量操作支持
   - 操作日誌記錄

### 長期（下個月）
3. **整合到 Java 應用中**
   - 在 GUI 中添加管理員功能
   - 實時更新菜單

---

## 🚀 立即可用的 PHP 管理工具

我建議為 teammate 準備一個簡單的 PHP 管理頁面：

**文件：** `php/admin/meal_manager.php`

功能：
- 列表顯示所有餐點
- 表單新增餐點
- 快速編輯功能
- 刪除按鈕

---

## 📋 交接清單

### 文檔要求
- ✅ 本文檔（DB 管理指南）
- ✅ SQL 語句參考
- ✅ 常見操作教程
- ✅ 故障排查

### 工具要求
- ✅ PHP 管理後台（可選）
- ✅ SQL 備份腳本
- ✅ 數據驗證工具

### 權限要求
- ✅ MySQL 帳號 (a0303)
- ✅ phpMyAdmin 訪問權限
- ✅ 伺服器文件訪問權限

---

## 🔐 安全考慮

1. **不要改變表結構**
   - 只修改數據
   - 如需新表，先溝通

2. **備份重要操作**
   ```sql
   -- 每周備份一次
   BACKUP TABLE McOS_meal TO '/backup/meal_backup.sql';
   ```

3. **記錄所有修改**
   - 誰、什麼時間、做了什麼

4. **不要修改字段名稱**
   - meal_name, prep_time 不能改
   - Java 代碼依賴這些字段

---

## 📞 故障排查

### 問題 1：新增的餐點在 GUI 中看不到
**解決：** 重新啟動 Java GUI 應用（它在啟動時讀取一次）

### 問題 2：餐點名稱有重複
**解決：** meal_name 設為 UNIQUE，不允許重複

### 問題 3：prep_time 為負數
**建議：** 
```sql
ALTER TABLE McOS_meal ADD CONSTRAINT check_prep_time CHECK (prep_time >= 0);
```

---

## 🎯 推薦方案

**現階段（你交接給 Teammate）：**

1. **教 Teammate 用 phpMyAdmin**
   - 最簡單快速
   - 無需編程

2. **提供 SQL 參考卡**
   - 常用語句
   - 快速查詢

3. **準備一個簡單的 PHP 後台**（可選，可以稍後添加）
   - 更友好的界面
   - 減少手工操作

---

## 📊 業務流程

```
Teammate 修改餐點資料（phpMyAdmin or PHP後台）
           ↓
MySQL 數據庫更新
           ↓
Java GUI 讀取 get_meals.php
           ↓
GUI 重新啟動時自動反映最新菜單
```

---

## 建議下一步

要我幫你創建以下之一嗎？

1. **PHP 管理後台** (`php/admin/meal_manager.php`)
   - 視覺化界面
   - 適合非技術人員

2. **SQL 參考卡** (`docs/SQL_REFERENCE.md`)
   - 常用操作
   - 快速查詢

3. **Teammate 交接文檔** (`docs/HANDOVER_GUIDE.md`)
   - 逐步教程
   - 常見問題

---

最後更新：2026-04-17
