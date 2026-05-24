<?php
header('Content-Type: application/json; charset=UTF-8');

// 1. 資料庫連線設定 (請替換為您真實的帳號密碼)
$host = 'localhost';
$db   = 'a0303';
$user = 'a0303';
$pass = 'pwd0303';
$charset = 'utf8mb4';

$dsn = "mysql:host=$host;dbname=$db;charset=$charset";
$options = [
    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
];

try {
    $pdo = new PDO($dsn, $user, $pass, $options);
} catch (\PDOException $e) {
    echo json_encode(['error' => '資料庫連線失敗: ' . $e->getMessage()]);
    exit;
}

// 2. 接收日期參數 (若無則預設為今日)
$date = isset($_GET['date']) ? $_GET['date'] : date('Y-m-d');

/**
 * 3. SQL 查詢邏輯
 * 使用 LEFT JOIN 將消耗統計表與庫存水位建議表合併
 * 假設兩張表都有 "原料編號" 欄位作為關聯鍵
 */
$sql = "SELECT 
            c.原料名稱, 
            c.單日總消耗數量, 
            c.單位, 
            IFNULL(i.庫存狀態, '無庫存監控') AS 庫存狀態
        FROM View_Daily_Total_Consumption c
        LEFT JOIN View_Inventory_Alert i ON c.原料編號 = i.原料編號
        WHERE c.日期 = :date";

try {
    $stmt = $pdo->prepare($sql);
    $stmt->execute(['date' => $date]);
    $results = $stmt->fetchAll();

    // 4. 回傳 JSON 資料
    echo json_encode(['data' => $results]);
} catch (Exception $e) {
    echo json_encode(['error' => '查詢失敗: ' . $e->getMessage()]);
}
?>