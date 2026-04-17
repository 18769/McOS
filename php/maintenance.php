<?php
/**
 * McOS - 資料庫維護工具
 * 用於重置 AUTO_INCREMENT 或查看表狀態
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
    
    $action = isset($_GET['action']) ? $_GET['action'] : 'status';
    
    if ($action === 'status') {
        // 查看表狀態
        $status_sql = "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES 
                       WHERE TABLE_SCHEMA = '" . $db_name . "' 
                       AND TABLE_NAME = 'McOS_meal'";
        $status_result = $conn->query($status_sql);
        $status_row = $status_result->fetch_assoc();
        
        $count_sql = "SELECT COUNT(*) as total, MAX(meal_id) as max_id FROM McOS_meal";
        $count_result = $conn->query($count_sql);
        $count_row = $count_result->fetch_assoc();
        
        $result = [
            'status' => 'success',
            'message' => '資料表狀態',
            'current_auto_increment' => $status_row['AUTO_INCREMENT'],
            'total_records' => $count_row['total'],
            'max_id' => $count_row['max_id'],
            'recommended_auto_increment' => intval($count_row['max_id']) + 1
        ];
        
    } elseif ($action === 'reset') {
        // 重置 AUTO_INCREMENT
        // 先找出最大的 ID
        $max_sql = "SELECT MAX(meal_id) as max_id FROM McOS_meal";
        $max_result = $conn->query($max_sql);
        $max_row = $max_result->fetch_assoc();
        $next_id = intval($max_row['max_id']) + 1;
        
        // 重置 AUTO_INCREMENT
        $reset_sql = "ALTER TABLE McOS_meal AUTO_INCREMENT = " . $next_id;
        if (!$conn->query($reset_sql)) {
            throw new Exception("重置失敗: " . $conn->error);
        }
        
        // 驗證
        $verify_sql = "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES 
                       WHERE TABLE_SCHEMA = '" . $db_name . "' 
                       AND TABLE_NAME = 'McOS_meal'";
        $verify_result = $conn->query($verify_sql);
        $verify_row = $verify_result->fetch_assoc();
        
        $result = [
            'status' => 'success',
            'message' => '成功重置 AUTO_INCREMENT',
            'new_auto_increment' => $verify_row['AUTO_INCREMENT']
        ];
        
    } else {
        throw new Exception("未知操作: " . $action);
    }
    
    echo json_encode($result, JSON_UNESCAPED_UNICODE);
    
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'status' => 'error',
        'message' => $e->getMessage()
    ], JSON_UNESCAPED_UNICODE);
}

$conn->close();
?>
