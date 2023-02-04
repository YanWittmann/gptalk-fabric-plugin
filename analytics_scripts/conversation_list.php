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

echo "<link rel='stylesheet' type='text/css' href='conversation.css'>";
echo "<script src='conversations.js'></script>";

echo "<div class='conversation_container'>";
echo "<h1>GPTalk Conversations</h1>";

// generate two dropdowns for filtering conversations
echo "<div class='conversation_filter'>";

echo "<select id='player_filter' onchange='filterConversations()'>";
echo "<option value=''>All Players</option>";
$players = array_unique($players, SORT_REGULAR);
foreach ($players as $player) {
    $player_name = $player['player_name'];
    $class_name_player = $player['class_name_player'];
    echo "<option value='player_$class_name_player'>$player_name</option>";
}
echo "</select>";

echo "<select id='target_filter' onchange='filterConversations()'>";
echo "<option value=''>All Targets</option>";
$targets = array_unique($targets, SORT_REGULAR);
foreach ($targets as $target) {
    $target_name = $target['target_name'];
    $class_name_target = $target['class_name_target'];
    echo "<option value='target_$class_name_target'>$target_name</option>";
}
echo "</select>";

echo "</div>";

foreach ($conversations as $target_uuid => $conversation) {

    // generate HTML table rows for each player->AI->message entry
    foreach ($conversation as $player_uuid => $messages) {
        $player_name = $messages[0]['player_name'];
        $target_type = $messages[0]['target_type'];
        $target_uuid = $messages[0]['target_uuid'];
        $target_name = $messages[0]['target_name'];
        $class_name_player = $messages[0]['class_name_player'];
        $class_name_target = $messages[0]['class_name_target'];

        echo "<h2 class='conversation_header player_$class_name_player target_$class_name_target'><span class='player_message'>$player_name</span> talking to <span class='ai_message'>$target_name</span> ($target_type - $target_uuid)</h2>";
        echo "<table class='conversation player_$class_name_player target_$class_name_target'>";
        echo "<thead><tr><th>Time</th><th>Message</th></tr></thead><tbody>";

        $previous_chat_id = -1;

        // generate HTML table rows for each message
        foreach ($messages as $message) {
            $message_source = $message['message_source'];
            $message_text = $message['message'];
            $created_at = $message['created_at'];
            $chat_id = $message['chat_id'];

            if ($chat_id != $previous_chat_id) {
                echo "<tr><td class='chat_id_change'>Chat ID: $chat_id</td><td></td></tr>";
                $previous_chat_id = $chat_id;
            }

            if ($message_source == 'p') {
                echo "<tr><td class='created_at'>$created_at</td><td class='player_message'>$message_text</td></tr>";
            } else if ($message_source == 'a') {
                echo "<tr><td class='created_at'>$created_at</td><td class='ai_message'>$message_text</td></tr>";
            } else if ($message_source == 'f') {
                echo "<tr><td class='created_at'>$created_at</td><td class='fact_message'>$message_text</td></tr>";
            }
        }

        echo "</tbody></table>";
    }
}
echo "</div>";

?>
