package dev.autoeattotem;

import dev.autoeattotem.config.ConfigScreen;
import dev.autoeattotem.config.ModConfig;
import dev.autoeattotem.input.InputSimulator;
import dev.autoeattotem.module.AutoEat;
import dev.autoeattotem.module.AutoTotem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoEatTotemMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("autoeattotem");
    private static final AutoEat EAT = new AutoEat();
    private static final AutoTotem TOTEM = new AutoTotem();
    private static KeyBinding toggleEat, toggleTotem, openConfig;
    private static Object lastPlayer;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("autoeattotem", "main"));
        toggleEat = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autoeattotem.toggle_eat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        toggleTotem = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autoeattotem.toggle_totem", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        openConfig = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autoeattotem.open_config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.END_CLIENT_TICK.register(AutoEatTotemMod::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> InputSimulator.releaseAll());
    }

    private static void resetAll() {
        EAT.reset();
        TOTEM.reset();
        InputSimulator.releaseAll();
        lastPlayer = null;
    }

    private static void tick(MinecraftClient mc) {
        ModConfig c = ModConfig.get();
        while (toggleEat.wasPressed()) {
            c.eatEnabled = !c.eatEnabled; ModConfig.save();
            if (mc.player != null) mc.player.sendMessage(Text.literal("AutoEat: " + (c.eatEnabled ? "ON" : "OFF")), true);
        }
        while (toggleTotem.wasPressed()) {
            c.totemEnabled = !c.totemEnabled; ModConfig.save();
            if (mc.player != null) mc.player.sendMessage(Text.literal("AutoTotem: " + (c.totemEnabled ? "ON" : "OFF")), true);
        }
        while (openConfig.wasPressed()) mc.setScreen(new ConfigScreen(mc.currentScreen));

        if (mc.player == null || mc.world == null) {
            if (lastPlayer != null) resetAll();
            return;
        }
        if (lastPlayer != mc.player) {
            if (lastPlayer != null) resetAll();
            lastPlayer = mc.player;
        }

        TOTEM.tick(mc);
        if (TOTEM.isBusy()) EAT.preempt();
        else EAT.tick(mc);
    }
}
