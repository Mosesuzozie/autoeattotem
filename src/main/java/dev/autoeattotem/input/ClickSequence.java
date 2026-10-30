package dev.autoeattotem.input;

import dev.autoeattotem.config.ModConfig;
import dev.autoeattotem.mixin.HandledScreenAccessor;
import dev.autoeattotem.util.Timing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayDeque;

public final class ClickSequence {
    public enum Status { RUNNING, DONE, FAILED }
    private enum Phase { PICK_NEXT, MOVING, AIMED, PRESSED }

    private final MinecraftClient mc;
    private final InventoryScreen screen;
    private final ArrayDeque<Integer> clicks = new ArrayDeque<>();
    private final int returnSlot;
    private final long startedAt = Timing.now();
    private Phase phase = Phase.PICK_NEXT;
    private long due;
    private int current = -1, extra = 0, done = 0;
    private double gx, gy;
    private boolean pressed;
    private String failReason = "";

    public ClickSequence(MinecraftClient mc, InventoryScreen screen, int returnSlot, int... slots) {
        this.mc = mc;
        this.screen = screen;
        this.returnSlot = returnSlot;
        for (int s : slots) clicks.add(s);
        InputSimulator.resetCursor(mc);
        due = Timing.now() + Timing.delay(ModConfig.get().clickDelayMs);
    }

    public String failReason() { return failReason; }

    public void cancel() {
        if (pressed) {
            InputSimulator.leftRelease(screen, gx, gy);
            pressed = false;
        }
    }

    private Status fail(String why) {
        cancel();
        failReason = why;
        return Status.FAILED;
    }

    private static boolean valid(int i) {
        return (i >= 0 && i <= 35) || i == 40;
    }

    private Slot slotFor(ScreenHandler h, int idx) {
        for (Slot s : h.slots) {
            if (s.inventory == mc.player.getInventory() && s.getIndex() == idx) return s;
        }
        return null;
    }

    private int cursorDestination() {
        if (returnSlot >= 0 && returnSlot <= 35
            && mc.player.getInventory().getStack(returnSlot).isEmpty()) return returnSlot;

        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    public Status tick() {
        ModConfig cfg = ModConfig.get();

        if (mc.player == null || mc.currentScreen != screen)
            return fail("inventory screen closed unexpectedly");

        if (Timing.now() - startedAt > 20000) return fail("timed out");
        if (Timing.now() < due) return Status.RUNNING;
        if (InputSimulator.shiftHeld(mc)) return Status.RUNNING;

        ScreenHandler h = screen.getScreenHandler();

        switch (phase) {
            case PICK_NEXT -> {
                if (clicks.isEmpty()) {
                    if (h.getCursorStack().isEmpty()) return Status.DONE;
                    if (extra++ >= 2) return fail("cursor still holds an item");
                    int dest = cursorDestination();
                    if (dest < 0) return fail("no free slot for the cursor stack");
                    clicks.add(dest);
                }

                current = clicks.poll();
                if (!valid(current)) return fail("invalid slot " + current);
                if (slotFor(h, current) == null) return fail("slot " + current + " not found");
                if (done == 0 && !h.getCursorStack().isEmpty())
                    return fail("cursor not empty before first click");

                phase = Phase.MOVING;
            }

            case MOVING -> {
                Slot s = slotFor(h, current);
                if (s == null) return fail("slot vanished");

                HandledScreenAccessor acc = (HandledScreenAccessor) screen;
                gx = acc.autoeattotem$getX() + s.x + 8;
                gy = acc.autoeattotem$getY() + s.y + 8;

                if (InputSimulator.moveCursorTo(mc, gx, gy)) {
                    phase = Phase.AIMED;
                    due = Timing.now() + Timing.delay(cfg.clickDelayMs);
                }
            }

            case AIMED -> {
                Slot s = slotFor(h, current);
                if (s == null) return fail("slot vanished");

                ItemStack cursor = h.getCursorStack();
                if (cursor.isEmpty() && s.getStack().isEmpty())
                    return fail("refusing to click an empty slot with an empty cursor");

                InputSimulator.leftPress(screen, gx, gy);
                pressed = true;
                phase = Phase.PRESSED;
                due = Timing.now() + Timing.delay(60);
            }

            case PRESSED -> {
                InputSimulator.leftRelease(screen, gx, gy);
                pressed = false;
                done++;
                phase = Phase.PICK_NEXT;
                due = Timing.now() + Timing.delay(cfg.clickDelayMs);
            }
        }

        return Status.RUNNING;
    }
}
