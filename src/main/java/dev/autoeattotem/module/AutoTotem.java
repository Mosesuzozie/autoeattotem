package dev.autoeattotem.module;

import dev.autoeattotem.config.ModConfig;
import dev.autoeattotem.input.ClickSequence;
import dev.autoeattotem.input.InputSimulator;
import dev.autoeattotem.util.Debug;
import dev.autoeattotem.util.Timing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;

public final class AutoTotem {
    private enum S { IDLE, OPENING, CLICKING, VERIFY, CLOSING, SEL_WAIT, SWAP_WAIT, RESTORE_WAIT, COOLDOWN }

    private S s = S.IDLE;
    private long due, deadline, cooldownUntil;
    private int srcSlot = -1, prevSlot = -1, failures;
    private InventoryScreen screen;
    private ClickSequence seq;
    private boolean warnedNone;

    public boolean isBusy() { return s != S.IDLE && s != S.COOLDOWN; }

    public void reset() {
        if (seq != null) seq.cancel();
        seq = null; screen = null; s = S.IDLE; failures = 0; warnedNone = false;
    }

    public void tick(MinecraftClient mc) {
        ModConfig c = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (!c.totemEnabled || p == null || mc.world == null || !p.isAlive()) {
            if (isBusy()) fail(mc, "disabled, dead or disconnected", false);
            else s = S.IDLE;
            return;
        }
        long now = Timing.now();
        if (s == S.COOLDOWN) { if (now < cooldownUntil) return; s = S.IDLE; }

        switch (s) {
            case IDLE -> idle(mc, p, c, now);
            case OPENING -> opening(mc, p, c, now);
            case CLICKING -> clicking(mc, p, c, now);
            case VERIFY -> verify(mc, p, c, now);
            case CLOSING -> closing(mc, p, c, now);
            case SEL_WAIT -> {
                if (mc.currentScreen != null) { fail(mc, "a screen opened", false); return; }
                if (p.getInventory().getSelectedSlot() != srcSlot) {
                    if (now > deadline) fail(mc, "could not select the totem slot", true);
                    return;
                }
                if (now < due) return;
                swapNow(mc, c, now);
            }
            case SWAP_WAIT -> {
                boolean ok = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
                if (c.verifySwap) {
                    if (ok) { Debug.log("Totem equipped (verified) via hotbar swap"); afterHotbar(mc, p, now); }
                    else if (now > deadline) fail(mc, "offhand did not receive a totem", true);
                } else if (now >= due) {
                    Debug.log("Swap key sent (verification disabled)");
                    afterHotbar(mc, p, now);
                }
            }
            case RESTORE_WAIT -> {
                if (p.getInventory().getSelectedSlot() == prevSlot || now > deadline) done(now);
            }
            default -> { }
        }
    }

    private boolean needsTotem(ClientPlayerEntity p, ModConfig c) {
        if (p.isSpectator()) return false;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return false;
        if (!c.replaceNonTotemOffhand && !p.getOffHandStack().isEmpty()) return false;
        return p.getHealth() <= c.healthThreshold;
    }

    private int findTotem(ClientPlayerEntity p, ModConfig c) {
        if (c.totemSearchHotbar)
            for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        if (c.totemSearchInventory && !p.isCreative())
            for (int i = 9; i < 36; i++) if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        return -1;
    }

    private void idle(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (!needsTotem(p, c)) { warnedNone = false; return; }
        if (mc.currentScreen != null) return;
        int src = findTotem(p, c);
        if (src < 0) {
            if (!warnedNone) { Debug.log("No totem available to equip"); warnedNone = true; }
            cooldownUntil = now + 1000; s = S.COOLDOWN;
            return;
        }
        warnedNone = false;
        srcSlot = src;
        if (src < 9) {
            prevSlot = p.getInventory().getSelectedSlot();
            if (prevSlot == src) { swapNow(mc, c, now); return; }
            InputSimulator.tap(mc.options.hotbarKeys[src]);
            s = S.SEL_WAIT; due = now + Timing.delay(c.swapDelayMs); deadline = now + 1500;
        } else {
            if (mc.interactionManager != null && mc.interactionManager.hasRidingInventory()) { cooldownUntil = now + 2000; s = S.COOLDOWN; return; }
            if (c.inputConflictPrevention && InputSimulator.shiftHeld(mc)) return;
            InputSimulator.tap(mc.options.inventoryKey);
            screen = null; s = S.OPENING; deadline = now + 2000;
        }
    }

    private void swapNow(MinecraftClient mc, ModConfig c, long now) {
        InputSimulator.tap(mc.options.swapHandsKey);
        s = S.SWAP_WAIT;
        due = now + 250;
        deadline = now + 1500;
    }

    private void afterHotbar(MinecraftClient mc, ClientPlayerEntity p, long now) {
        if (prevSlot >= 0 && prevSlot != p.getInventory().getSelectedSlot() && mc.currentScreen == null) {
            InputSimulator.tap(mc.options.hotbarKeys[prevSlot]);
            s = S.RESTORE_WAIT; deadline = now + 1500;
        } else done(now);
    }

    private void opening(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (!(mc.currentScreen instanceof InventoryScreen is)) {
            if (now > deadline) fail(mc, "inventory did not open", false);
            return;
        }
        if (screen == null) { screen = is; due = now + Timing.delay(c.inventoryOpenDelayMs); }
        if (now < due) return;
        if (!is.getScreenHandler().getCursorStack().isEmpty()) { fail(mc, "cursor already holds an item", false); return; }
        if (!p.getInventory().getStack(srcSlot).isOf(Items.TOTEM_OF_UNDYING)) { fail(mc, "totem moved before the click", true); return; }
        seq = new ClickSequence(mc, is, srcSlot, srcSlot, 40);
        s = S.CLICKING;
    }

    private void clicking(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        ClickSequence.Status st = seq.tick();
        if (st == ClickSequence.Status.RUNNING) return;
        if (st == ClickSequence.Status.FAILED) { fail(mc, "click sequence failed: " + seq.failReason(), true); return; }
        seq = null;
        s = S.VERIFY; due = now + Timing.delay(c.swapDelayMs); deadline = now + 1500;
    }

    private void verify(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (now < due) return;
        boolean ok = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (c.verifySwap && !ok) {
            if (now > deadline) fail(mc, "offhand did not receive a totem", true);
            return;
        }
        Debug.log(ok ? "Totem equipped (verified) via inventory" : "Totem click done (verification disabled)");
        s = S.CLOSING; due = now + Timing.delay(c.inventoryCloseDelayMs);
    }

    private void closing(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (now < due) return;
        if (mc.currentScreen != screen || screen == null) { done(now); return; }
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) { fail(mc, "cursor holds an item; leaving inventory open", false); return; }
        screen.close();
        screen = null;
        done(now);
    }

    private void done(long now) {
        failures = 0;
        cooldownUntil = now + 500;
        s = S.COOLDOWN;
    }

    private void fail(MinecraftClient mc, String why, boolean restoreSlot) {
        Debug.log("AutoTotem aborted: " + why);
        if (seq != null) { seq.cancel(); seq = null; }
        if (screen != null && mc.currentScreen == screen && screen.getScreenHandler().getCursorStack().isEmpty()) screen.close();
        screen = null;
        ClientPlayerEntity p = mc.player;
        if (restoreSlot && p != null && mc.currentScreen == null && prevSlot >= 0 && p.getInventory().getSelectedSlot() != prevSlot)
            InputSimulator.tap(mc.options.hotbarKeys[prevSlot]);
        failures++;
        cooldownUntil = Timing.now() + (failures >= 3 ? 10000 : 1500);
        s = S.COOLDOWN;
    }
}
