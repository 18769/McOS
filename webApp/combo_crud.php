<?php
/**
 * 套餐管理 API - combo_crud.php
 * 支援對 McOS_comboMeals 表的 CRUD 操作
 * 
 * 操作說明：
 * - POST /combo_crud.php?action=insert (body: JSON {combo_name, food_items})
 * - POST /combo_crud.php?action=update&id=X (body: JSON {combo_name, food_items})
 * - GET /combo_crud.php?action=delete&id=X
 * - GET /combo_crud.php?action=query_all
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
    $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
    
    if ($action === 'insert') {
        // 新增套餐
        $json = file_get_contents('php://input');
        $data = json_decode($json, true);
        
        if (!$data || !isset($data['combo_name']) || !isset($data['food_items'])) {
            throw new Exception("缺少必要欄位: combo_name, food_items");
        }
        
        $combo_name = $data['combo_name'];
        // food_items 可以是陣列 [1,3,5] 或字串 "1,3,5"
        if (is_array($data['food_items'])) {
            $food_items = implode(',', $data['food_items']);
        } else {
            $food_items = $data['food_items'];
        }
        
        $sql = "INSERT INTO McOS_comboMeals (combo_name, food_items) VALUES (?, ?)";
        $stmt = $conn->prepare($sql);
        
        if (!$stmt) {
            throw new Exception("準備語句失敗: " . $conn->error);
        }
        
        $stmt->bind_param("ss", $combo_name, $food_items);
        
        if (!$stmt->execute()) {
            throw new Exception("插入失敗: " . $stmt->error);
        }
        
        $response = [
            'status' => 'success',
            'message' => '套餐新增成功',
            'combo_id' => $conn->insert_id,
            'timestamp' => date('Y-m-d H:i:s')
        ];
        
    } elseif ($action === 'update') {
        // 更新套餐
        if ($id <= 0) {
            throw new Exception("缺少有效的 combo_id");
        }
        
        $json = file_get_contents('php://input');
        $data = json_decode($json, true);
        
        if (!$data) {
            throw new Exception("無效的 JSON 資料");
        }
        
        $updates = [];
        $params = [];
        $types = '';
        
        if (isset($data['combo_name'])) {
            $updates[] = 'combo_name = ?';
            $params[] = $data['combo_name'];
            $types .= 's';
        }
        
        if (isset($data['food_items'])) {
            $updates[] = 'food_items = ?';
            $food_items = is_array($data['food_items']) ? json_encode($data['food_items'], JSON_UNESCAPED_UNICODE) : $data['food_items'];
            $params[] = $food_items;
            $types .= 's';
        }
        
        if (empty($updates)) {
            throw new Exception("沒有可更新的欄位");
        }
        
        $params[] = $id;
        $types .= 'i';
        
        $sql = "UPDATE McOS_comboMeals SET " . implode(', ', $updates) . ", updated_at = CURRENT_TIMESTAMP WHERE combo_id = ?";
        $stmt = $conn->prepare($sql);
        
        if (!$stmt) {
            throw new Exception("準備語句失敗: " . $conn->error);
        }
        
        $stmt->bind_param($types, ...$params);
        
        if (!$stmt->execute()) {
            throw new Exception("更新失敗: " . $stmt->error);
        }
        
        $response = [
            'status' => 'success',
            'message' => '套餐更新成功',
            'affected_rows' => $stmt->affected_rows,
            'timestamp' => date('Y-m-d H:i:s')
        ];
        
    } elseif ($action === 'delete') {
        // 刪除套餐
        if ($id <= 0) {
            throw new Exception("缺少有效的 combo_id");
        }
        
        $sql = "DELETE FROM McOS_comboMeals WHERE combo_id = ?";
        $stmt = $conn->prepare($sql);
        
        if (!$stmt) {
            throw new Exception("準備語句失敗: " . $conn->error);
        }
        
        $stmt->bind_param("i", $id);
        
        if (!$stmt->execute()) {
            throw new Exception("刪除失敗: " . $stmt->error);
        }
        
        $response = [
            'status' => 'success',
            'message' => '套餐刪除成功',
            'affected_rows' => $stmt->affected_rows,
            'timestamp' => date('Y-m-d H:i:s')
        ];
        
    } elseif ($action === 'query_all') {
        // 查詢所有套餐
        $sql = "SELECT combo_id, combo_name, JSON_UNQUOTE(food_items) as food_items, created_at, updated_at FROM McOS_comboMeals ORDER BY combo_id ASC";
        $result = $conn->query($sql);
        
        if (!$result) {
            throw new Exception("查詢失敗: " . $conn->error);
        }
        
        $data = [];
        while ($row = $result->fetch_assoc()) {
            $row['food_items'] = json_decode($row['food_items'], true);
            $data[] = $row;
        }
        
        $response = [
            'status' => 'success',
            'message' => '成功查詢所有套餐',
            'count' => count($data),
            'data' => $data,
            'timestamp' => date('Y-m-d H:i:s')
        ];
        
    } else {
        throw new Exception("無效的操作: " . $action);
    }
    
} catch (Exception $e) {
    http_response_code(400);
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
