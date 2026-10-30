package dev.autoeattotem.input;

import dev.autoeattotem.config.ModConfig;
import dev.autoeattotem.mixin.KeyBindingAccessor;
import dev.autoeattotem.util.Timing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class InputSimulator {
    private static final Set<KeyBinding> HELD = new HashSet<>();
    private static double cx = Double.NaN, cy = Double.NaN;

    private InputSimulator() {}

    public static void tap(KeyBinding key) {
        KeyBindingAccessor a = (KeyBindingAccessor) key;
        a.autoeattotem$setTimesPressed(a.autoeattotem$getTimesPressed() + 1);
    }

    public static void hold(KeyBinding key) {
        if (HELD.add(key)) tap(key);
        key.setPressed(true);
    }

    public static void release(KeyBinding key) {
        if (HELD.remove(key)) key.setPressed(false);
    }

    public static void releaseAll() {
        for (KeyBinding k : new ArrayList<>(HELD)) release(k);
    }

    public static boolean shiftHeld(MinecraftClient mc) {
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public static void resetCursor(MinecraftClient mc) {
        cx = mc.mouse.getX();
        cy = mc.mouse.getY();
    }

    public static boolean moveCursorTo(MinecraftClient mc, double guiX, double guiY) {
        Window w = mc.getWindow();
        double s = w.getScaleFactor();
        double tx = guiX * s, ty = guiY * s;

        if (Double.isNaN(cx)) resetCursor(mc);

        double dx = tx - cx, dy = ty - cy, d = Math.hypot(dx, dy);
        boolean human = ModConfig.get().humanLikeTiming;
        double step = human ? Math.max(d * 0.35, 10 * s) : d;
        boolean arrived = step >= d;

        if (arrived) {
            cx = tx;
            cy = ty;
        } else {
            cx += dx / d * step + (human ? Timing.jitter(1.2) : 0);
            cy += dy / d * step + (human ? Timing.jitter(1.2) : 0);
        }

        GLFW.glfwSetCursorPos(w.getHandle(), cx, cy);
        return arrived;
    }

    public static void leftPress(Screen screen, double guiX, double guiY) {
        screen.mouseClicked(
            new Click(guiX, guiY, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)),
            false
        );
    }

    public static void leftRelease(Screen screen, double guiX, double guiY) {
        screen.mouseReleased(
            new Click(guiX, guiY, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0))
        );
    }
}
