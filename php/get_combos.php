<?php
/**
 * 查詢 McOS_comboMeals 表的 API
 * Java GUI 通過這個接口讀取套餐定義
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
    
    // 查詢套餐定義表 (food_items 是逗號分隔的 meal_id)
    $sql = "SELECT combo_id, combo_name, food_items FROM McOS_comboMeals ORDER BY combo_id ASC";
    $result = $conn->query($sql);
    
    if (!$result) {
        throw new Exception("查詢失敗: " . $conn->error);
    }
    
    $data = [];
    while ($row = $result->fetch_assoc()) {
        $data[] = $row;
    }
    
    $response = [
        'status' => 'success',
        'message' => '成功查詢 McOS_comboMeals 表',
        'count' => count($data),
        'data' => $data,
        'timestamp' => date('Y-m-d H:i:s')
    ];
    
} catch (Exception $e) {
    http_response_code(500);
    $response = [
        'status' => 'error',
        'message' => $e->getMessage(),
        'timestamp' => date('Y-m-d H:i:s')
    ];
    
} finally {
    if (isset($conn)) {
        $conn->close();
    }
}

echo json_encode($response, JSON_UNESCAPED_UNICODE);
?>
