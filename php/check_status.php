<?php
/**
 * 查看 McOS_meal 表的狀態和 AUTO_INCREMENT 值
 */

header('Content-Type: application/json; charset=utf-8');

$db_host = 'localhost';
$db_user = 'a0303';
$db_pass = 'pwd0303';
$db_name = 'a0303';

try {
    $conn = new mysqli($db_host, $db_user, $db_pass, $db_name);
    
    if ($conn->connect_error) {
        throw new Exception("連線失敗: " . $conn->connect_error);
    }
    
    $conn->set_charset("utf8mb4");
    
    // 查詢 McOS_meal 表的資料
    $sql = "SELECT * FROM McOS_meal ORDER BY meal_id DESC LIMIT 5";
    $result = $conn->query($sql);
    
    $recent_meals = [];
    while ($row = $result->fetch_assoc()) {
        $recent_meals[] = $row;
    }
    
    // 查詢表的 AUTO_INCREMENT 值
    $status_sql = "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES 
                   WHERE TABLE_SCHEMA = '" . $db_name . "' 
                   AND TABLE_NAME = 'McOS_meal'";
    $status_result = $conn->query($status_sql);
    $status_row = $status_result->fetch_assoc();
    
    // 計算總記錄數
    $count_sql = "SELECT COUNT(*) as total FROM McOS_meal";
    $count_result = $conn->query($count_sql);
    $count_row = $count_result->fetch_assoc();
    
    $response = [
        'status' => 'success',
        'auto_increment' => $status_row['AUTO_INCREMENT'],
        'total_records' => $count_row['total'],
        'recent_meals' => $recent_meals
    ];
    
} catch (Exception $e) {
    http_response_code(500);
    $response = [
        'status' => 'error',
        'message' => $e->getMessage()
    ];
}

echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
$conn->close();
?>
