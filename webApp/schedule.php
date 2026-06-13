<?php
// Enable CORS headers so local and remote browser calls are not blocked
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");
header("Access-Control-Allow-Methods: POST, GET, OPTIONS");
header("Content-Type: application/json; charset=UTF-8");

// Handle preflight CORS OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

// 1. Read input payload (JSON request from Web App)
$input = file_get_contents('php://input');
if (empty($input) && isset($_GET['type'])) {
    $input = json_encode(array("type" => $_GET['type']));
}

// 2. Define the path to Python and script
// On university servers, it is usually "python3" or "python"
$python = "python3"; 
$script = "cli_scheduler.py";

// 3. Setup descriptors for process communication
$descriptorspec = array(
    0 => array("pipe", "r"),  // stdin: write data to python
    1 => array("pipe", "w"),  // stdout: read output from python
    2 => array("pipe", "w")   // stderr: read errors
);

// 4. Launch python script in a separate sub-process
$process = proc_open("$python $script", $descriptorspec, $pipes);

if (is_resource($process)) {
    // Write JSON payload to Python's stdin
    fwrite($pipes[0], $input);
    fclose($pipes[0]);

    // Read result from Python's stdout
    $output = stream_get_contents($pipes[1]);
    fclose($pipes[1]);

    // Read potential logs or errors from Python's stderr
    $error = stream_get_contents($pipes[2]);
    fclose($pipes[2]);

    $returnValue = proc_close($process);

    // If Python failed or output was empty, return details
    if ($returnValue !== 0 || empty($output)) {
        echo json_encode([
            "error" => "Python scheduler execution failed: " . trim($error),
            "return_code" => $returnValue,
            "stderr" => trim($error)
        ], JSON_UNESCAPED_UNICODE);
    } else {
        // Echo the exact JSON output returned from cli_scheduler.py
        echo $output;
    }
} else {
    echo json_encode(["error" => "Failed to spawn Python process. Check server configurations."], JSON_UNESCAPED_UNICODE);
}
