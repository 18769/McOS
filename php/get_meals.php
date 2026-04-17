<?php
/**
 * 專門用來查詢 McOS_meal 表的 API
 * Java GUI 通過這個接口讀取餐點資料
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
    
    // 只查詢 McOS_meal 表
    $sql = "SELECT * FROM McOS_meal ORDER BY meal_id ASC";
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
        'message' => '成功查詢 McOS_meal 表',
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
}

echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

if (isset($conn)) {
    $conn->close();
}
?>
