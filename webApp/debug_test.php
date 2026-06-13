<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");

$python = "python3";
$script = "cli_scheduler.py";
$descriptorspec = array(
    0 => array("pipe", "r"),
    1 => array("pipe", "w"),
    2 => array("pipe", "w")
);

// Clean old state first
@unlink("/tmp/mcOS_scheduler_state.json");

// ADD 2 real Chinese orders
$payload1 = json_encode(array(
    "type" => "ADD_ORDER",
    "data" => array(
        array("id" => 1, "item" => "\xe5\xa4\xa7\xe9\xba\xa5\xe5\x85\x8b", "is_takeout" => false, "prep_time" => "9"),
        array("id" => 2, "item" => "\xe8\x96\xaf\xe6\xa2\x9d",             "is_takeout" => false, "prep_time" => "9")
    )
));

$proc1 = proc_open("$python $script", $descriptorspec, $pipes1);
$out1 = $err1 = "";
if (is_resource($proc1)) {
    fwrite($pipes1[0], $payload1); fclose($pipes1[0]);
    $out1 = stream_get_contents($pipes1[1]); fclose($pipes1[1]);
    $err1 = stream_get_contents($pipes1[2]); fclose($pipes1[2]);
    $rc1 = proc_close($proc1);
}

// GET_STATUS
$proc2 = proc_open("$python $script", $descriptorspec, $pipes2);
$out2 = $err2 = "";
if (is_resource($proc2)) {
    fwrite($pipes2[0], '{"type":"GET_STATUS"}'); fclose($pipes2[0]);
    $out2 = stream_get_contents($pipes2[1]); fclose($pipes2[1]);
    $err2 = stream_get_contents($pipes2[2]); fclose($pipes2[2]);
    $rc2 = proc_close($proc2);
}

$result1 = json_decode($out1, true);
$result2 = json_decode($out2, true);

// Summarize tasks
$summarize = function($tasks) {
    if (!is_array($tasks)) return [];
    return array_map(function($t) {
        return array(
            "id"          => $t["id"],
            "item"        => $t["item"],
            "worker_id"   => isset($t["worker_id"]) ? $t["worker_id"] : null,
            "equipment_id"=> isset($t["equipment_id"]) ? $t["equipment_id"] : null,
            "equipment_type" => isset($t["equipment_type"]) ? $t["equipment_type"] : "",
            "remaining"   => isset($t["remaining_time"]) ? $t["remaining_time"] : 0
        );
    }, $tasks);
};

echo json_encode(array(
    "ADD_ORDER_result"    => $summarize($result1),
    "GET_STATUS_result"   => $summarize($result2),
    "tasks_survive"       => is_array($result2) && count($result2) > 0,
    "add_stderr"          => trim($err1),
    "get_stderr"          => trim($err2)
), JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
