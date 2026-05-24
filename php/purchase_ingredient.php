<?php
/**
 * Increase ingredient stock by purchase quantity.
 * Expected JSON body:
 * { "ing_id": 1, "qty": 10 }
 */

header('Content-Type: application/json; charset=utf-8');

$db_host = 'localhost';
$db_user = 'a0303';
$db_pass = 'pwd0303';
$db_name = 'a0303';

$ingredient_table = 'McOS_ingredients';

try {
    $conn = new mysqli($db_host, $db_user, $db_pass, $db_name);
    if ($conn->connect_error) {
        throw new Exception('連線失敗: ' . $conn->connect_error);
    }
    $conn->set_charset('utf8mb4');

    $raw = file_get_contents('php://input');
    $payload = json_decode($raw, true);
    if (!is_array($payload)) {
        throw new Exception('缺少 JSON body');
    }

    $ing_id = isset($payload['ing_id']) ? intval($payload['ing_id']) : 0;
    $qty = isset($payload['qty']) ? floatval($payload['qty']) : 0;

    if ($ing_id <= 0 || $qty <= 0) {
        throw new Exception('ing_id 或 qty 不合法');
    }

    $stmt = $conn->prepare(
        "UPDATE `{$ingredient_table}` SET stock_qty = stock_qty + ? WHERE ing_id = ?"
    );
    if (!$stmt) {
        throw new Exception('準備語句失敗: ' . $conn->error);
    }

    $stmt->bind_param('di', $qty, $ing_id);
    if (!$stmt->execute()) {
        throw new Exception('更新失敗: ' . $stmt->error);
    }

    $response = [
        'status' => 'success',
        'message' => '採買完成',
        'affected_rows' => $stmt->affected_rows,
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

if (isset($stmt) && $stmt) {
    $stmt->close();
}
if (isset($conn)) {
    $conn->close();
}
?>