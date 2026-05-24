<?php
/**
 * Record completed order info.
 * Expected JSON body fields (optional):
 * order_id, order_name, customer_id, customer_name,
 * scheduled_at, completed_at, is_takeout, items (array)
 */

header('Content-Type: application/json; charset=utf-8');

$db_host = 'localhost';
$db_user = 'a0303';
$db_pass = 'pwd0303';
$db_name = 'a0303';

$order_table = 'McOS_order';
$order_meal_table = 'McOS_orderMeal';
$order_combo_table = 'McOS_orderComboMeal';
$meal_table = 'McOS_meal';
$combo_table = 'McOS_comboMeals_new_new';

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

    $order_id = isset($payload['order_id']) ? intval($payload['order_id']) : 0;
    $customer_id = isset($payload['customer_id']) ? intval($payload['customer_id']) : 0;
    $is_combo = isset($payload['is_combo']) ? (bool)$payload['is_combo'] : false;
    $order_time = '';
    if (!empty($payload['completed_at'])) {
        $order_time = $payload['completed_at'];
    } elseif (!empty($payload['scheduled_at'])) {
        $order_time = $payload['scheduled_at'];
    } else {
        $order_time = date('Y-m-d H:i:s');
    }

    if ($customer_id <= 0) {
        throw new Exception('customer_id 不合法');
    }

    $conn->begin_transaction();

    $db_order_id = 0;
    if ($order_id > 0) {
        $order_stmt = $conn->prepare(
            "INSERT INTO `{$order_table}` (orderID, customerID, orderTime) VALUES (?, ?, ?)"
        );
        if (!$order_stmt) {
            throw new Exception('準備語句失敗: ' . $conn->error);
        }
        $order_stmt->bind_param('iis', $order_id, $customer_id, $order_time);
        if (!$order_stmt->execute()) {
            $db_order_id = 0;
        } else {
            $db_order_id = $order_id;
        }
    }

    if ($db_order_id <= 0) {
        $order_stmt = $conn->prepare(
            "INSERT INTO `{$order_table}` (customerID, orderTime) VALUES (?, ?)"
        );
        if (!$order_stmt) {
            throw new Exception('準備語句失敗: ' . $conn->error);
        }
        $order_stmt->bind_param('is', $customer_id, $order_time);
        if (!$order_stmt->execute()) {
            throw new Exception('寫入訂單失敗: ' . $order_stmt->error);
        }
        $db_order_id = $conn->insert_id;
    }

    $meal_map = [];
    $meal_result = $conn->query("SELECT meal_id, meal_name FROM `{$meal_table}`");
    if ($meal_result) {
        while ($row = $meal_result->fetch_assoc()) {
            $meal_map[$row['meal_name']] = intval($row['meal_id']);
        }
    }

    $combo_map = [];
    $combo_result = $conn->query("SELECT comboID, comboName FROM `{$combo_table}`");
    if ($combo_result) {
        while ($row = $combo_result->fetch_assoc()) {
            $combo_map[$row['comboName']] = intval($row['comboID']);
        }
    }

    $items = isset($payload['items']) && is_array($payload['items']) ? $payload['items'] : [];

    // Insert meals for non-combo orders
    if (!$is_combo && !empty($items)) {
        $meal_counts = [];
        foreach ($items as $name) {
            if (!isset($meal_map[$name])) {
                continue;
            }
            $meal_id = $meal_map[$name];
            $meal_counts[$meal_id] = ($meal_counts[$meal_id] ?? 0) + 1;
        }

        $meal_stmt = $conn->prepare(
            "INSERT INTO `{$order_meal_table}` (orderID, mealID, qty) VALUES (?, ?, ?)"
        );
        if (!$meal_stmt) {
            throw new Exception('準備訂單餐點失敗: ' . $conn->error);
        }
        foreach ($meal_counts as $meal_id => $qty) {
            $meal_stmt->bind_param('iii', $db_order_id, $meal_id, $qty);
            if (!$meal_stmt->execute()) {
                throw new Exception('寫入訂單餐點失敗: ' . $meal_stmt->error);
            }
        }
    }

    // Insert combo if order_name matches combo table
    if (!empty($payload['order_name']) && isset($combo_map[$payload['order_name']])) {
        $combo_id = $combo_map[$payload['order_name']];
        $combo_stmt = $conn->prepare(
            "INSERT INTO `{$order_combo_table}` (comboID, qty, orderID) VALUES (?, ?, ?)"
        );
        if (!$combo_stmt) {
            throw new Exception('準備套餐失敗: ' . $conn->error);
        }
        $qty = 1;
        $combo_stmt->bind_param('iii', $combo_id, $qty, $db_order_id);
        if (!$combo_stmt->execute()) {
            throw new Exception('寫入套餐失敗: ' . $combo_stmt->error);
        }
    }

    $conn->commit();

    $response = [
        'status' => 'success',
        'message' => '訂單已記錄',
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

if (isset($meal_stmt) && $meal_stmt) {
    $meal_stmt->close();
}
if (isset($combo_stmt) && $combo_stmt) {
    $combo_stmt->close();
}
if (isset($order_stmt) && $order_stmt) {
    $order_stmt->close();
}
if (isset($conn)) {
    $conn->close();
}
?>