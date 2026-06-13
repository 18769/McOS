<?php
/**
 * McOS - 簡化的 CRUD API
 * 專門為 MealManagerGUI 設計
 * 
 * 支援操作：
 * - POST /crud.php?action=insert&table=McOS_meal (body: JSON)
 * - POST /crud.php?action=update&table=McOS_meal&id=X (body: JSON)
 * - GET /crud.php?action=delete&table=McOS_meal&id=X
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
    
    $action = isset($_GET['action']) ? $_GET['action'] : '';
    $table = isset($_GET['table']) ? $_GET['table'] : '';
    
    // 驗證 table 名稱（防止 SQL injection）
    if (!preg_match('/^[a-zA-Z_][a-zA-Z0-9_]*$/', $table)) {
        throw new Exception("無效的資料表名稱");
    }
    
    if ($action === 'insert') {
        // 新增記錄
        $json = file_get_contents('php://input');
        $data = json_decode($json, true);
        
        if (!$data || !is_array($data)) {
            // 如果沒有 JSON body，嘗試從 GET 參數讀取
            $data = [];
            foreach ($_GET as $key => $value) {
                if (!in_array($key, ['action', 'table', 'id'])) {
                    $data[$key] = $value;
                }
            }
        }
        
        if (empty($data)) {
            throw new Exception("缺少要插入的資料");
        }
        
        $columns = array_keys($data);
        $values = array_values($data);
        
        // 驗證 column 名稱
        foreach ($columns as $col) {
            if (!preg_match('/^[a-zA-Z_][a-zA-Z0-9_]*$/', $col)) {
                throw new Exception("無效的欄位名稱: " . $col);
            }
        }
        
        $col_str = '`' . implode('`, `', $columns) . '`';
        $val_placeholders = implode(',', array_fill(0, count($values), '?'));
        
        $sql = "INSERT INTO `" . $table . "` (" . $col_str . ") VALUES (" . $val_placeholders . ")";
        
        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            throw new Exception("準備語句失敗: " . $conn->error);
        }
        
        // 動態類型綁定
        $types = '';
        foreach ($values as $val) {
            if (is_int($val)) {
                $types .= 'i';
            } else {
                $types .= 's';
            }
        }
        
        $stmt->bind_param($types, ...$values);
        
        if (!$stmt->execute()) {
            throw new Exception("執行失敗: " . $stmt->error);
        }
        
        $result = [
            'status' => 'success',
            'message' => '成功新增',
            'insert_id' => $conn->insert_id,
            'affected_rows' => $stmt->affected_rows
        ];
        
    } elseif ($action === 'update') {
        // 更新記錄
        $id = isset($_GET['id']) ? intval($_GET['id']) : null;
        
        if (!$id) {
            throw new Exception("缺少 id 參數");
        }
        
        $json = file_get_contents('php://input');
        $data = json_decode($json, true);
        
        if (!$data || !is_array($data)) {
            // 如果沒有 JSON body，嘗試從 GET 參數讀取
            $data = [];
            foreach ($_GET as $key => $value) {
                if (!in_array($key, ['action', 'table', 'id'])) {
                    $data[$key] = $value;
                }
            }
        }
        
        if (empty($data)) {
            throw new Exception("缺少要更新的資料");
        }
        
        $set_parts = [];
        $values = [];
        
        foreach ($data as $col => $val) {
            if (!preg_match('/^[a-zA-Z_][a-zA-Z0-9_]*$/', $col)) {
                throw new Exception("無效的欄位名稱: " . $col);
            }
            $set_parts[] = "`" . $col . "` = ?";
            $values[] = $val;
        }
        
        $values[] = $id;
        $set_str = implode(', ', $set_parts);
        
        $sql = "UPDATE `" . $table . "` SET " . $set_str . " WHERE `meal_id` = ?";
        
        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            throw new Exception("準備語句失敗: " . $conn->error);
        }
        
        // 動態類型綁定
        $types = '';
        foreach ($values as $val) {
            if (is_int($val)) {
                $types .= 'i';
            } else {
                $types .= 's';
            }
        }
        
        $stmt->bind_param($types, ...$values);
        
        if (!$stmt->execute()) {
            throw new Exception("執行失敗: " . $stmt->error);
        }
        
        $result = [
            'status' => 'success',
            'message' => '成功更新',
            'affected_rows' => $stmt->affected_rows
        ];
        
    } elseif ($action === 'delete') {
        // 刪除記錄
        $id = isset($_GET['id']) ? intval($_GET['id']) : null;
        
        if (!$id) {
            throw new Exception("缺少 id 參數");
        }
        
        $sql = "DELETE FROM `" . $table . "` WHERE `meal_id` = ?";
        $stmt = $conn->prepare($sql);
        
        if (!$stmt) {
            throw new Exception("準備語句失敗: " . $conn->error);
        }
        
        $stmt->bind_param("i", $id);
        
        if (!$stmt->execute()) {
            throw new Exception("執行失敗: " . $stmt->error);
        }
        
        $result = [
            'status' => 'success',
            'message' => '成功刪除',
            'affected_rows' => $stmt->affected_rows
        ];
        
    } elseif ($action === 'status') {
        // 查看 AUTO_INCREMENT 狀態
        if (!$table) {
            throw new Exception("status 操作需要指定 table 參數");
        }
        
        $status_sql = "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES 
                       WHERE TABLE_SCHEMA = '" . $db_name . "' 
                       AND TABLE_NAME = '" . $table . "'";
        $status_result = $conn->query($status_sql);
        
        if (!$status_result) {
            throw new Exception("查詢失敗: " . $conn->error);
        }
        
        $status_row = $status_result->fetch_assoc();
        
        $count_sql = "SELECT COUNT(*) as total, MAX(meal_id) as max_id FROM `" . $table . "`";
        $count_result = $conn->query($count_sql);
        
        if (!$count_result) {
            throw new Exception("查詢失敗: " . $conn->error);
        }
        
        $count_row = $count_result->fetch_assoc();
        
        $result = [
            'status' => 'success',
            'message' => '資料表狀態',
            'current_auto_increment' => intval($status_row['AUTO_INCREMENT']),
            'total_records' => intval($count_row['total']),
            'max_id' => $count_row['max_id'] ? intval($count_row['max_id']) : 0,
            'recommended_auto_increment' => ($count_row['max_id'] ? intval($count_row['max_id']) : 0) + 1
        ];
        
    } elseif ($action === 'reset') {
        // 重置 AUTO_INCREMENT
        if (!$table) {
            throw new Exception("reset 操作需要指定 table 參數");
        }
        
        $max_sql = "SELECT MAX(meal_id) as max_id FROM `" . $table . "`";
        $max_result = $conn->query($max_sql);
        
        if (!$max_result) {
            throw new Exception("查詢失敗: " . $conn->error);
        }
        
        $max_row = $max_result->fetch_assoc();
        $next_id = ($max_row['max_id'] ? intval($max_row['max_id']) : 0) + 1;
        
        $reset_sql = "ALTER TABLE `" . $table . "` AUTO_INCREMENT = " . $next_id;
        if (!$conn->query($reset_sql)) {
            throw new Exception("重置失敗: " . $conn->error);
        }
        
        // 驗證
        $verify_sql = "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES 
                       WHERE TABLE_SCHEMA = '" . $db_name . "' 
                       AND TABLE_NAME = '" . $table . "'";
        $verify_result = $conn->query($verify_sql);
        $verify_row = $verify_result->fetch_assoc();
        
        $result = [
            'status' => 'success',
            'message' => '成功重置 AUTO_INCREMENT',
            'new_auto_increment' => intval($verify_row['AUTO_INCREMENT'])
        ];
        
    } else {
        throw new Exception("未知的操作: " . $action);
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
