package de.yanwittmann.gptalk.mobtalk;

import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatElement {

    private final String text;
    private final Entity entity;
    private final long timestamp = System.currentTimeMillis();

    public ChatElement(String text, Entity source) {
        this.text = text;
        this.entity = source;
    }

    public String getRawText() {
        return text;
    }

    public String getEntityUUID() {
        return entity.getUuidAsString();
    }

    public String getEntityName() {
        return entity.getName().getString();
    }

    public boolean isPlayer() {
        return entity.isPlayer();
    }

    private String formatTimestamp() {
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    public String getPrompt() {
        if (isPlayer()) return formatTimestamp() + " PLAYER " + getEntityName() + (text == null ? ":" : ": " + text);
        else return formatTimestamp() + " CHATBOT " + getEntityName() + (text == null ? ":" : ": " + text);
    }

    public Text toText() {
        return Text.of("<" + getEntityName() + "> " + text);
    }
}
