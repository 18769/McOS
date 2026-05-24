<?php
/**
 * Consume inventory based on aggregated ingredient quantities.
 * Expected JSON body:
 * {
 *   "order_id": "123",
 *   "completed_at": "2026-05-24 12:30:00",
 *   "consumptions": [ { "ing_id": 1, "qty": 2.5 }, ... ]
 * }
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

    $consumptions = isset($payload['consumptions']) && is_array($payload['consumptions'])
        ? $payload['consumptions']
        : [];

    if (empty($consumptions)) {
        throw new Exception('consumptions 為空');
    }

    $conn->begin_transaction();

    $stmt = $conn->prepare(
        "UPDATE `{$ingredient_table}` SET stock_qty = GREATEST(stock_qty - ?, 0) WHERE ing_id = ?"
    );
    if (!$stmt) {
        throw new Exception('準備語句失敗: ' . $conn->error);
    }

    $updated = 0;
    foreach ($consumptions as $row) {
        $ing_id = isset($row['ing_id']) ? intval($row['ing_id']) : 0;
        $qty = isset($row['qty']) ? floatval($row['qty']) : 0;
        if ($ing_id <= 0 || $qty <= 0) {
            continue;
        }
        $stmt->bind_param('di', $qty, $ing_id);
        if (!$stmt->execute()) {
            throw new Exception('更新失敗: ' . $stmt->error);
        }
        $updated += $stmt->affected_rows;
    }

    $conn->commit();

    $response = [
        'status' => 'success',
        'message' => '庫存已扣除',
        'updated_rows' => $updated,
        'timestamp' => date('Y-m-d H:i:s')
    ];
} catch (Exception $e) {
    if (isset($conn) && $conn->errno) {
        $conn->rollback();
    }
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