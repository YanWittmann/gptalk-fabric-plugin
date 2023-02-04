
function filterConversations() {
    let player_filter = document.getElementById("player_filter").value;
    let target_filter = document.getElementById("target_filter").value;

    let conversation_container = document.getElementsByClassName("conversation_container")[0];
    let conversations = conversation_container.getElementsByClassName("conversation");
    for (let i = 0; i < conversations.length; i++) {
        let conversation = conversations[i];

        if (player_filter !== "" && conversation.className.indexOf(player_filter) === -1) {
            conversation.style.display = "none";
        } else if (target_filter !== "" && conversation.className.indexOf(target_filter) === -1) {
            conversation.style.display = "none";
        } else {
            conversation.style.display = "table";
        }
    }

    let conversation_headers = conversation_container.getElementsByClassName("conversation_header");
    for (let i = 0; i < conversation_headers.length; i++) {
        let conversation_header = conversation_headers[i];

        if (player_filter !== "" && conversation_header.className.indexOf(player_filter) === -1) {
            conversation_header.style.display = "none";
        } else if (target_filter !== "" && conversation_header.className.indexOf(target_filter) === -1) {
            conversation_header.style.display = "none";
        } else {
            conversation_header.style.display = "block";
        }
    }
}

setTimeout(filterConversations, 100);
