<?php
header('Content-Type: application/json; charset=UTF-8');

// 1. 資料庫連線設定
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

// 3. SQL 查詢邏輯
$sql_meals = "SELECT 
    o.orderID AS 訂單編號,
    c.name AS 顧客名稱,
    o.orderTime AS 點餐時間,
    CONCAT(
        IFNULL(meals.meal_items, ''),
        IF(meals.meal_items IS NOT NULL AND combos.combo_items IS NOT NULL, ', ', ''),
        IFNULL(combos.combo_items, '')
    ) AS 訂購內容
FROM McOS_order o
LEFT JOIN McOS_customer c ON o.customerID = c.customerID
LEFT JOIN (
    SELECT 
        om.orderID,
        GROUP_CONCAT(CONCAT(m.meal_name, ' * ', om.qty) SEPARATOR ', ') AS meal_items
    FROM McOS_orderMeal om
    JOIN McOS_meal m ON om.mealID = m.meal_id
    GROUP BY om.orderID
) meals ON o.orderID = meals.orderID
LEFT JOIN (
    SELECT 
        ocm.orderID,
        GROUP_CONCAT(CONCAT(c.comboName, ' * ', ocm.qty) SEPARATOR ', ') AS combo_items
    FROM McOS_orderComboMeal ocm
    JOIN McOS_comboMeals_new_new c ON ocm.comboID = c.comboID
    GROUP BY ocm.orderID
) combos ON o.orderID = combos.orderID
WHERE DATE(o.orderTime) = :date";

$sql_cons = "SELECT 
            原料名稱, 
            單日總消耗數量, 
            單位
        FROM View_Daily_Total_Consumption
        WHERE 日期 = :date";

try {
    $results = [];

    // 取得當日訂單 (dataType = meal)
    $stmt_meals = $pdo->prepare($sql_meals);
    $stmt_meals->execute(['date' => $date]);
    while ($row = $stmt_meals->fetch()) {
        $row['dataType'] = 'meal';
        $results[] = $row;
    }

    // 取得當日原物料消耗量 (dataType = consumption)
    $stmt_cons = $pdo->prepare($sql_cons);
    $stmt_cons->execute(['date' => $date]);
    while ($row = $stmt_cons->fetch()) {
        $row['dataType'] = 'consumption';
        $results[] = $row;
    }

    echo json_encode($results, JSON_UNESCAPED_UNICODE);
} catch (Exception $e) {
    echo json_encode(['error' => '查詢失敗: ' . $e->getMessage()]);
}
?>