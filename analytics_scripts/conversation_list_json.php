<?php

// connection to database now exist in $conn
include_once('db_login.php');

// query the database for all conversations
$sql = "SELECT * FROM gptalk_event ORDER BY created_at DESC";
$result = $conn->query($sql);

$conversations = array(); // array of arrays of rows, player->AI->message entry
$players = array();
$targets = array();

while ($row = $result->fetch_assoc()) {
    $target_uuid = $row['target_uuid'];
    $player_uuid = $row['player_uuid'];
    $message_source = $row['message_source'];
    $message = $row['message'];
    $created_at = $row['created_at'];
    $player_name = $row['player_name'];
    $target_type = $row['target_type'];
    $target_name = $row['target_name'];
    $chat_id = $row['chat_id'];

    // if this is the first message in a conversation, create a new conversation
    if (!isset($conversations[$target_uuid])) {
        $conversations[$target_uuid] = array();
    }

    // if this is the first message from the player in a conversation, create a new player->AI->message entry
    if (!isset($conversations[$target_uuid][$player_uuid])) {
        $conversations[$target_uuid][$player_uuid] = array();
    }

    // add the message to the player->AI->message entry
    $conversations[$target_uuid][$player_uuid][] = array(
        'message_source' => $message_source,
        'message' => $message,
        'created_at' => $created_at,
        'player_name' => $player_name,
        'target_type' => $target_type,
        'target_name' => $target_name,
        'target_uuid' => $target_uuid,
        'chat_id' => $chat_id,
        'class_name_player' => preg_replace('/[^a-zA-Z0-9]/i', '', $player_name),
        'class_name_target' => preg_replace('/[^a-zA-Z0-9]/i', '', $target_name)
    );

    $players[] = array(
        'player_name' => $player_name,
        'class_name_player' => preg_replace('/[^a-zA-Z0-9]/i', '', $player_name)
    );
    $targets[] = array(
        'target_name' => $target_name,
        'class_name_target' => preg_replace('/[^a-zA-Z0-9]/i', '', $target_name)
    );
}

// invert the $conversations array so that the last entry is the first entry
foreach ($conversations as $target_uuid => $conversation) {
    foreach ($conversation as $player_uuid => $messages) {
        $conversations[$target_uuid][$player_uuid] = array_reverse($messages);
    }
}

// generate a JSON object from the $conversations array and print it
echo json_encode($conversations);

?>
