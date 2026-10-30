package dev.autoeattotem.util;

import dev.autoeattotem.AutoEatTotemMod;
import dev.autoeattotem.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Debug {
    private Debug() {}
    public static void log(String msg) {
        AutoEatTotemMod.LOGGER.info(msg);
        if (!ModConfig.get().debug) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoEat/AutoTotem] " + msg).formatted(Formatting.GRAY), false);
    }
}
