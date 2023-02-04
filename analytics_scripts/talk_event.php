<?php
ini_set('display_errors', '1');
ini_set('display_startup_errors', '1');
error_reporting(E_ALL);

function require_string_length_between($string, $name, $min, $max) {
    if (empty($string)) {
        die('{"status": "error", "message": "Missing required data", "field": "' . $name . '"}');
    } else if (strlen($string) < $min || strlen($string) > $max) {
        die('{"status": "error", "message": "Invalid length of data", "field": "' . $name . '", "min": ' . $min . ', "max": ' . $max . ', "length": ' . strlen($string) . '}');
    }
}

// check if the required POST data is filled out
require_string_length_between($_REQUEST['target_uuid'], 'target_uuid', 36, 36);
require_string_length_between($_REQUEST['target_type'], 'target_type', 2, 50);
require_string_length_between($_REQUEST['target_name'], 'target_name', 2, 50);
require_string_length_between($_REQUEST['player_uuid'], 'player_uuid', 36, 36);
require_string_length_between($_REQUEST['player_name'], 'player_name', 2, 16);
require_string_length_between($_REQUEST['message'], 'message', 1, 300);
require_string_length_between($_REQUEST['source'], 'source', 1, 1);
require_string_length_between($_REQUEST['chat_id'], 'chat_id', 1, 7);

$target_uuid = $_REQUEST['target_uuid'];
$target_type = $_REQUEST['target_type'];
$target_name = $_REQUEST['target_name'];
$player_uuid = $_REQUEST['player_uuid'];
$player_name = $_REQUEST['player_name'];
$message = $_REQUEST['message'];
$source = $_REQUEST['source'];
$chat_id = $_REQUEST['chat_id'];

if ($source != 'p' && $source != 'a' && $source != 'f') {
    die('{"status": "error", "message": "Invalid source, must be either \'f\', \'p\' or \'a\'", "field": "source"}');
}
if (!is_numeric($chat_id)) {
    die('{"status": "error", "message": "Invalid chat id, must be an integer", "field": "chat_id"}');
}

function createTableIfNotExists($conn, $table_name) {
    $sql = "CREATE TABLE IF NOT EXISTS $table_name (
        id INT(6) UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        target_uuid VARCHAR(36) NOT NULL,
        target_type VARCHAR(50) NOT NULL,
        target_name VARCHAR(50) NOT NULL,
        player_uuid VARCHAR(36) NOT NULL,
        player_name VARCHAR(16) NOT NULL,
        message VARCHAR(300) NOT NULL,
        message_source VARCHAR(1) NOT NULL,
        chat_id INTEGER NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    )";
    if ($conn->query($sql) !== TRUE) {
        die('{"status": "error", "message": "Error creating table: ' . $conn->error . '"}');
    }
}

include_once('db_login.php');
// connection to database now exist in $conn

createTableIfNotExists($conn, 'gptalk_event');

$statement = $conn->prepare("INSERT INTO gptalk_event (target_uuid, target_type, target_name, player_uuid, player_name, message_source, chat_id, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
if ($statement === FALSE) {
    die('{"status": "error", "message": "Error preparing statement: ' . $conn->error . '"}');
}
$statement->bind_param("ssssssis", $target_uuid, $target_type, $target_name, $player_uuid, $player_name, $source, $chat_id, $message);

if ($statement->execute() === TRUE) {
    echo '{"status": "success", "message": "New record created successfully"}';
} else {
    echo '{"status": "error", "message": "Error: ' . $statement->error . '"}';
}

$statement->close();
$conn->close();

?>

