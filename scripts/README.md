# 📜 McOS 智慧廚房：自動化點餐腳本編輯指南 (JSON Scripting DSL)

為了方便 QA 人員與開發者撰寫自動化的「餐廳營運模擬」情境，我們開發了這套「腳本化點餐系統 (Scriptable System)」。您可以透過編寫簡單的 **JSON 格式檔**，讓系統模擬真實顧客的點餐節奏、大量訂單壓力測試，以及外送內用的混雜情境。

---

## 🏗 腳本結構解析

一份自動化測試腳本 (.json) 必須包含兩個欄位：
1. `meta`：腳本的自我介紹，包含名稱與描述。
2. `steps`：執行的動作清單，程式將會「從上至下」依序執行裡面的動作。

### 範例起手式：
```json
{
  "meta": {
    "script_name": "我的第一份測試腳本",
    "description": "測試基本功能"
  },
  "steps": [
    // 這裡放入動作 (Actions)
  ]
}
```

---

## ⚡ 支援的行為指令 (Actions)

每一個 `step` 都是一個擁有 `"action"` 欄位的 JSON 物件。目前支援的核心行為包含以下七種：

### 1. 勾選/取消「外帶模式」(`SET_MODE`)
切換應用程式的外帶選項狀態（等同於用滑鼠勾選介面右上角的「外帶模式」）。
```json
{ "action": "SET_MODE", "takeout": true }
```

### 2. 點擊一份餐點 (`ADD_ORDER`)
模擬滑鼠去點擊畫面上的漢堡或薯條。會將點單送入「等待暫存區」。
* 屬性 `"count"`: 可選，若設定為 3，這行會連點三次（預設為 1）。
* 屬性 `"prep_time"`: 可選，若不填寫（或設定為不大於 0），系統會自動帶入餐點的預設製作秒數（例如大麥克 8 秒、薯條 3 秒等）。
```json
{
  "action": "ADD_ORDER",
  "item": "大麥克",
  "count": 2
}
```

### 3. 送出排程 (`SEND_ORDERS`)
將目前在等待區的餐點全部送出給後端排程發動。
```json
{ "action": "SEND_ORDERS" }
```

### 4. 清除暫存 (`CLEAR_BUFFER`)
點擊「清空暫存」按鈕，如果按錯可以用這個清掉等待區。
```json
{ "action": "CLEAR_BUFFER" }
```

### 5. 等待時間：模擬現實中的發呆/吃晚餐 (`WAIT`)
用來製造行為與行為之間的時間差，讓背景排程能有時間把做好的漢堡交給客人。
```json
{ "action": "WAIT", "seconds": 5 }
```

### 6. 迴圈控制流 (`REPEAT`)
**[進階功能]** 當你需要「無限迴圈」或「重複做 N 遍相同的點餐動作」去壓測伺服器時，無需複製貼上一大堆程式碼，只要將指令包裝在 `REPEAT` 即可。

* `"times"`: 重複幾次。設定為 `0` 的話該區塊會被跳過。
* `"steps"`: 在底下的陣列放入要被重複的動作。支援「巢狀 (無限疊加)」。
```json
{
  "action": "REPEAT",
  "times": 3,
  "steps": [
    { "action": "ADD_ORDER", "item": "薯條", "count": 1 },
    { "action": "SEND_ORDERS" },
    { "action": "WAIT", "seconds": 2 }
  ]
}
```

---

## 🚀 完整執行範例：`demo_rush_hour.json`
這份腳本會做以下事情：
1. 叫了 2 份大麥克與 1 份薯條 (內用)，直接送出，然後等 3 秒。
2. 切換成外帶，並**連續 5 次**叫餐：進店買一份蘋果派立刻送出，接著等 1 秒離開。
```json
{
  "meta": {
    "script_name": "午餐尖峰負載與重複排單",
    "description": "測試複雜流程與 REPEAT 控制"
  },
  "steps": [
    { "action": "SET_MODE", "takeout": false },
    { "action": "ADD_ORDER", "item": "大麥克", "count": 2 },
    { "action": "ADD_ORDER", "item": "薯條", "count": 1 },
    { "action": "SEND_ORDERS" },
    { "action": "WAIT", "seconds": 3 },
    { "action": "SET_MODE", "takeout": true },
    {
      "action": "REPEAT",
      "times": 5,
      "steps": [
        { "action": "ADD_ORDER", "item": "蘋果派", "count": 1 },
        { "action": "SEND_ORDERS" },
        { "action": "WAIT", "seconds": 1 }
      ]
    }
  ]
}
```

## ❓ 該怎麼執行？
啟動您的 `run_demo.bat` 打開 Java 程式後，點擊畫面上方的「**📂 載入腳本**」，選擇準備好的 .json 腳本，系統即會啟動自動導航！
