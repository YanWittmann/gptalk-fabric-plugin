package de.yanwittmann.gptalk;

import de.yanwittmann.gptalk.mobtalk.MobTalkCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Gptalk implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(new MobTalkCommand());
    }
}
