package com.tameableanimals.utils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.tameableanimals.TameableAnimalsPlugin;
import com.tameableanimals.config.ConfigManager;

import java.util.logging.Level;

public final class Debug {
    public static HytaleLogger LOGGER = TameableAnimalsPlugin.get().getLogger();

    private Debug() {}

    public static Boolean isNullLog(Object obj, String message) {
        if (obj == null) {
            LOGGER.atSevere().log(message);
            return true;
        }
        return false;
    }

    public static Boolean isNullMsg(PlayerRef player, Object obj, String message) {
        if (obj == null) {
            log(message, Level.SEVERE);
            msg(player, message, Level.SEVERE);
            return true;
        }
        return false;
    }

    public static void log(String message, Level level) { LOGGER.at(level).log(message); }

    public static void msg(PlayerRef player, String message, Level level) {
        log(message, level);

        if (!ConfigManager.getConfig().getDebugChatMessages()) return;
        if (player == null) return;

        Message msg = switch (level.getName()) {
            case "SEVERE" -> Message.raw(message).color("#FF5555").bold(true);
            case "WARNING" -> Message.raw(message).color("#FFFF55");
            default -> Message.raw(message).color("#FFFFFF");
        };
        player.sendMessage(msg);
    }
}
