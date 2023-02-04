<?php

// connection to database now exist in $conn
include_once('db_login.php');

// query the database for all conversations
$sql = "SELECT * FROM gptalk_event ORDER BY created_at ASC";
$result = $conn->query($sql);

$conversations = array(); // array of arrays of rows, player->AI->message entry

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
        'chat_id' => $chat_id
    );
}

echo "<link rel='stylesheet' type='text/css' href='conversation.css'>";

echo "<div class='conversation_container'>";
echo "<h1>GPTalk Conversations</h1>";
foreach ($conversations as $target_uuid => $conversation) {

    // generate HTML table rows for each player->AI->message entry
    foreach ($conversation as $player_uuid => $messages) {
        $player_name = $messages[0]['player_name'];
        $target_type = $messages[0]['target_type'];
        $target_uuid = $messages[0]['target_uuid'];
        $target_name = $messages[0]['target_name'];

        echo "<h2><span class='player_message'>$player_name</span> talking to <span class='ai_message'>$target_name</span> ($target_type - $target_uuid)</h2>";
        echo "<table class='conversation'>";
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
