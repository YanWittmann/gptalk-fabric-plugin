function fetchData() {
    fetch("conversation_list_json.php")
        .then(response => response.json())
        .then(data => generatePage(data));
}

function initialize() {
    fetchData();

    let interval = setInterval(function () {
        fetchData();
    }, 10000);

    setTimeout(function () {
        clearInterval(interval);
    }, 10 * 60 * 1000);
}

function generatePage(data) {
    let conversation_list = document.getElementById("conversation_list");

    let appendEntries = [];
    for (let target in data) {
        for (let player in data[target]) {
            let conversation = generateEntry(target, player, data[target][player]);
            appendEntries.push(conversation);
        }
    }

    conversation_list.innerHTML = "";
    appendEntries.forEach(entry => {
        conversation_list.appendChild(entry);
    });

    createConversationFilter(data);
    applyConversationFilter();
}

function generateEntry(targetUUID, playerUUID, messages) {
    let classPlayerName = messages[0].class_name_player;
    let classTargetName = messages[0].class_name_target;
    let playerName = messages[0].player_name;
    let targetType = messages[0].target_type;
    let targetName = messages[0].target_name;

    let rows = [];
    let previousChatId = -1;
    for (let i = 0; i < messages.length; i++) {
        let message = messages[i];
        let messageSource = message.message_source;
        let messageText = message.message;
        let createdAt = message.created_at;
        let chatId = message.chat_id;

        if (chatId !== previousChatId) {
            rows.push(createElement("tr", {}, [
                createElement("td", {"class": "chat_id_change"}, ["Chat ID: " + chatId]),
                createElement("td", {}, [])
            ]));
            previousChatId = chatId;
        }

        if (messageSource === "p") {
            rows.push(createElement("tr", {}, [
                createElement("td", {"class": "created_at"}, [createdAt]),
                createElement("td", {"class": "player_message"}, [messageText])
            ]));
        } else if (messageSource === "a") {
            rows.push(createElement("tr", {}, [
                createElement("td", {"class": "created_at"}, [createdAt]),
                createElement("td", {"class": "ai_message"}, [messageText])
            ]));
        } else if (messageSource === "f") {
            rows.push(createElement("tr", {}, [
                createElement("td", {"class": "created_at"}, [createdAt]),
                createElement("td", {"class": "fact_message"}, [messageText])
            ]));
        }
    }

    return createElement("div", {
            "class": "conversation player_" + classPlayerName + " target_" + classTargetName
        }, [
            createElement("h2", {
                "class": "conversation_header player_" + classPlayerName + " target_" + classTargetName
            }, [
                createElement("span", {"class": "player_message"}, [playerName]),
                createElement("span", {}, [" talking to "]),
                createElement("span", {"class": "ai_message"}, [targetName + " (" + targetType + ")"]),
                createElement("span", {}, [" (" + targetUUID + ")"])
            ]),
            createElement("table", {}, [
                createElement("thead", {}, [
                    createElement("tr", {}, [
                        createElement("th", {}, ["Time"]),
                        createElement("th", {}, ["Message"])
                    ])
                ]),
                createElement("tbody", {}, rows)
            ])
        ]
    );
}

function createConversationFilter(data) {
    let player_filter = document.getElementById("player_filter");
    let target_filter = document.getElementById("target_filter");

    let previousPlayer = player_filter.value;
    let previousTarget = target_filter.value;

    player_filter.innerHTML = "";
    target_filter.innerHTML = "";

    let player_options = [];
    let target_options = [];
    let unique_target = [];
    let unique_player = [];

    player_options.push(createElement("option", {"value": ""}, ["All Players"]));
    target_options.push(createElement("option", {"value": ""}, ["All Targets"]));

    for (let target in data) {
        for (let player in data[target]) {
            let messages = data[target][player];
            let classPlayerName = messages[0].class_name_player;
            let classTargetName = messages[0].class_name_target;
            let playerName = messages[0].player_name;
            let targetType = messages[0].target_type;
            let targetName = messages[0].target_name;

            if (unique_player.indexOf(classPlayerName) === -1) {
                unique_player.push(classPlayerName);

                player_options.push(createElement("option", {
                    "value": "player_" + classPlayerName
                }, [playerName]));
            }
            if (unique_target.indexOf(classTargetName) === -1) {
                unique_target.push(classTargetName);

                target_options.push(createElement("option", {
                    "value": "target_" + classTargetName
                }, [targetName + " (" + targetType + ")"]));
            }
        }
    }

    for (let i = 0; i < player_options.length; i++) {
        player_filter.appendChild(player_options[i]);
    }
    for (let i = 0; i < target_options.length; i++) {
        target_filter.appendChild(target_options[i]);
    }

    if (previousPlayer !== "") {
        player_filter.value = previousPlayer;
    }
    if (previousTarget !== "") {
        target_filter.value = previousTarget;
    }
}

function createElement(type, attributes, children = []) {
    let element = document.createElement(type);
    for (let key in attributes) {
        if (attributes.hasOwnProperty(key)) {
            element.setAttribute(key, attributes[key]);
        }
    }
    for (let i = 0; i < children.length; i++) {
        if (typeof children[i] === "string") {
            children[i] = document.createTextNode(children[i]);
        }
        element.appendChild(children[i]);
    }
    return element;
}


function applyConversationFilter() {
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
            conversation.style.display = "block";
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

initialize();
