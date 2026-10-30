package dev.autoeattotem.module;

import dev.autoeattotem.config.ModConfig;
import dev.autoeattotem.input.ClickSequence;
import dev.autoeattotem.input.InputSimulator;
import dev.autoeattotem.util.Debug;
import dev.autoeattotem.util.Timing;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.List;

public final class AutoEat {
    private enum S { IDLE, OPENING, MOVING, CLOSING, SELECT_WAIT, HOLDING, RESTORE_SEL, R_OPENING, R_MOVING, R_CLOSING, COOLDOWN }

    private S s = S.IDLE;
    private long due, deadline, cooldownUntil, lastCombat;
    private int eatSlot = -1, prevSlot = -1, srcSlot = -1, displacedHome = -1;
    private int startFood, startCount;
    private Item displacedItem, eatingItem;
    private boolean started, warnedNoFood, warnedTarget;
    private InventoryScreen screen;
    private ClickSequence seq;

    public boolean isBusy() { return s != S.IDLE && s != S.COOLDOWN; }

    public void reset() {
        InputSimulator.release(MinecraftClient.getInstance().options.useKey);
        if (seq != null) seq.cancel();
        seq = null; screen = null; s = S.IDLE; displacedItem = null; started = false;
    }

    /** Called when AutoTotem takes over input: stop holding use, do not touch the hotbar. */
    public void preempt() {
        if (!isBusy()) return;
        abort("AutoTotem has priority", false);
    }

    public void tick(MinecraftClient mc) {
        ModConfig c = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (p != null && (p.hurtTime > 0 || (mc.options.attackKey.isPressed() && mc.targetedEntity != null))) lastCombat = Timing.now();
        if (!c.eatEnabled || p == null || mc.world == null || !p.isAlive()) {
            if (isBusy()) abort("disabled, dead or disconnected", false); else s = S.IDLE;
            return;
        }
        long now = Timing.now();
        if (s == S.COOLDOWN) { if (now < cooldownUntil) return; s = S.IDLE; }

        switch (s) {
            case IDLE -> idle(mc, p, c, now);
            case OPENING, R_OPENING -> opening(mc, p, c, now);
            case MOVING, R_MOVING -> moving(mc, p, c, now);
            case CLOSING, R_CLOSING -> closing(mc, p, c, now);
            case SELECT_WAIT -> selectWait(mc, p, c, now);
            case HOLDING -> holding(mc, p, c, now);
            case RESTORE_SEL -> {
                if (p.getInventory().getSelectedSlot() == prevSlot || now > deadline) afterRestoreSlot(mc, p, c, now);
            }
            default -> { }
        }
    }

    // ---------------- decision ----------------
    private void idle(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        int food = p.getHungerManager().getFoodLevel();
        if (food > c.hungerThreshold || !p.getHungerManager().isNotFull()) { warnedNoFood = false; return; }
        if (p.isCreative() || p.isSpectator() || mc.currentScreen != null || p.isUsingItem()) return;
        if (c.inputConflictPrevention && (mc.options.useKey.isPressed() || mc.options.attackKey.isPressed())) return;
        boolean combat = c.combatPrevention && now - lastCombat < c.combatWindowMs && food > c.urgentFoodLevel;
        if (combat) return;
        if (blockedByInteractableTarget(mc)) {
            if (!warnedTarget) { Debug.log("Waiting: crosshair is on something right-click would interact with"); warnedTarget = true; }
            return;
        }
        warnedTarget = false;

        int idx = pickFood(p, c, food);
        if (idx < 0) {
            if (!warnedNoFood) { Debug.log("No suitable food available"); warnedNoFood = true; }
            cooldownUntil = now + 3000; s = S.COOLDOWN;
            return;
        }
        warnedNoFood = false;
        prevSlot = p.getInventory().getSelectedSlot();
        displacedItem = null; displacedHome = -1;
        if (idx < 9) { eatSlot = idx; beginSelect(mc, p, c, now); return; }

        if (!c.autoMoveToHotbar) {
            Debug.log("Food is only in the inventory and auto-move is off");
            cooldownUntil = now + 3000; s = S.COOLDOWN;
            return;
        }
        if (mc.interactionManager != null && mc.interactionManager.hasRidingInventory()) { cooldownUntil = now + 3000; s = S.COOLDOWN; return; }
        srcSlot = idx;
        eatSlot = chooseHotbarTarget(p);
        ItemStack there = p.getInventory().getStack(eatSlot);
        if (!there.isEmpty()) { displacedItem = there.getItem(); displacedHome = srcSlot; }
        InputSimulator.tap(mc.options.inventoryKey);
        screen = null; seq = null; s = S.OPENING; deadline = now + 2000;
    }

    private int chooseHotbarTarget(ClientPlayerEntity p) {
        int best = prevSlot, bestScore = 99;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            int sc = st.isEmpty() ? 0 : (st.getMaxCount() > 1 ? 1 : 2);
            if (sc < bestScore) { bestScore = sc; best = i; }
        }
        return best;
    }

    private static String idOf(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    private static boolean isFood(ItemStack st) {
        return !st.isEmpty() && st.contains(DataComponentTypes.FOOD);
    }

    private int pickFood(ClientPlayerEntity p, ModConfig c, int foodLevel) {
        List<String> black = ModConfig.parseIds(c.blacklistedFoods);
        List<String> pref = ModConfig.parseIds(c.preferredFoods);
        int missing = 20 - foodLevel;
        int bestIdx = -1; double bestScore = -1e9;
        int max = c.eatSearchInventory ? 36 : 9;
        for (int i = 0; i < max; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!isFood(st)) continue;
            FoodComponent f = st.get(DataComponentTypes.FOOD);
            if (f == null) continue;
            String id = idOf(st.getItem());
            if (black.contains(id)) continue;
            if (!f.canAlwaysEat() && !p.getHungerManager().isNotFull()) continue;
            double score = switch (c.foodPriority) {
                case SATURATION -> f.saturation() * 10 + f.nutrition();
                case NUTRITION -> f.nutrition() * 10 + f.saturation();
                case LEAST_WASTE -> f.nutrition() >= missing ? 1000 - f.nutrition() : f.nutrition();
                case PREFERRED_LIST -> {
                    int k = pref.indexOf(id);
                    yield (k >= 0 ? 1000 - k * 10 : 0) + f.saturation();
                }
            };
            if (i < 9) score += 0.01;
            if (score > bestScore) { bestScore = score; bestIdx = i; }
        }
        return bestIdx;
    }

    /** Right-click would interact with these instead of using the held food. */
    private boolean blockedByInteractableTarget(MinecraftClient mc) {
        HitResult hr = mc.crosshairTarget;
        if (hr == null || mc.world == null) return false;
        if (hr.getType() == HitResult.Type.ENTITY && hr instanceof EntityHitResult eh) {
            return !(eh.getEntity() instanceof HostileEntity) && !(eh.getEntity() instanceof PlayerEntity);
        }
        if (hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bh) {
            BlockState st = mc.world.getBlockState(bh.getBlockPos());
            return st.hasBlockEntity() || st.isIn(BlockTags.DOORS) || st.isIn(BlockTags.TRAPDOORS)
                || st.isIn(BlockTags.FENCE_GATES) || st.isIn(BlockTags.BUTTONS) || st.isIn(BlockTags.BEDS)
                || st.isIn(BlockTags.ANVIL) || st.isOf(Blocks.CRAFTING_TABLE) || st.isOf(Blocks.LEVER);
        }
        return false;
    }

    // ---------------- inventory phase ----------------
    private void opening(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        boolean restoring = s == S.R_OPENING;
        if (!(mc.currentScreen instanceof InventoryScreen is)) {
            if (now > deadline) abort("inventory did not open", false);
            return;
        }
        if (screen == null) { screen = is; due = now + Timing.delay(c.clickDelayMs); }
        if (now < due) return;
        if (!is.getScreenHandler().getCursorStack().isEmpty()) { abort("cursor already holds an item", false); return; }
        if (restoring) {
            seq = new ClickSequence(mc, is, displacedHome, displacedHome, eatSlot);
            s = S.R_MOVING;
        } else {
            if (!isFood(p.getInventory().getStack(srcSlot))) { abort("food moved before the click", false); return; }
            seq = new ClickSequence(mc, is, srcSlot, srcSlot, eatSlot);
            s = S.MOVING;
        }
    }

    private void moving(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        ClickSequence.Status st = seq.tick();
        if (st == ClickSequence.Status.RUNNING) return;
        if (st == ClickSequence.Status.FAILED) { abort("click sequence failed: " + seq.failReason(), false); return; }
        seq = null;
        if (s == S.MOVING && !isFood(p.getInventory().getStack(eatSlot))) { abort("food did not arrive in the hotbar", false); return; }
        s = (s == S.MOVING) ? S.CLOSING : S.R_CLOSING;
        due = now + Timing.delay(c.clickDelayMs);
    }

    private void closing(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (now < due) return;
        if (screen == null || mc.currentScreen != screen) { abort("inventory closed unexpectedly", false); return; }
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) { abort("cursor holds an item; leaving inventory open", false); return; }
        screen.close();
        screen = null;
        if (s == S.CLOSING) beginSelect(mc, p, c, now);
        else finish(now);
    }

    // ---------------- eating phase ----------------
    private void beginSelect(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (p.getInventory().getSelectedSlot() != eatSlot) InputSimulator.tap(mc.options.hotbarKeys[eatSlot]);
        s = S.SELECT_WAIT; due = now + Timing.delay(c.eatDelayMs); deadline = now + 1500;
    }

    private void selectWait(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (mc.currentScreen != null) { abort("a screen opened", false); return; }
        if (p.getInventory().getSelectedSlot() != eatSlot) {
            if (now > deadline) abort("could not select the food slot", true);
            return;
        }
        if (now < due) return;
        if (!isFood(p.getMainHandStack())) { abort("selected slot does not hold food", true); return; }
        s = S.HOLDING; started = false; deadline = now + 2500;
    }

    private void holding(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (mc.currentScreen != null) { abort("a screen opened", false); return; }
        if (p.getInventory().getSelectedSlot() != eatSlot) { abort("hotbar slot changed manually", false); return; }
        if (c.inputConflictPrevention && mc.options.attackKey.isPressed()) { abort("player pressed attack", true); return; }
        ItemStack held = p.getMainHandStack();
        if (!started) {
            if (!isFood(held)) { abort("held item is no longer food", true); return; }
            InputSimulator.hold(mc.options.useKey);
            if (p.isUsingItem()) {
                started = true; eatingItem = held.getItem();
                startFood = p.getHungerManager().getFoodLevel(); startCount = held.getCount();
                deadline = now + 8000;
            } else if (now > deadline) abort("eating did not start", true);
            return;
        }
        InputSimulator.hold(mc.options.useKey);
        if (p.isUsingItem()) {
            if (now > deadline) abort("eating took too long", true);
            return;
        }
        InputSimulator.release(mc.options.useKey);
        boolean ok = p.getHungerManager().getFoodLevel() > startFood
            || p.getMainHandStack().getCount() < startCount || !p.getMainHandStack().isOf(eatingItem);
        Debug.log(ok ? "Finished eating " + idOf(eatingItem) : "Eating stopped without effect");
        cooldownUntil = now + 1000;
        if (c.restorePreviousSlot && prevSlot >= 0 && prevSlot != p.getInventory().getSelectedSlot()) {
            InputSimulator.tap(mc.options.hotbarKeys[prevSlot]);
            s = S.RESTORE_SEL; deadline = now + 1500;
        } else afterRestoreSlot(mc, p, c, now);
    }

    private void afterRestoreSlot(MinecraftClient mc, ClientPlayerEntity p, ModConfig c, long now) {
        if (displacedItem != null && c.autoMoveToHotbar && mc.currentScreen == null
            && p.getInventory().getStack(displacedHome).isOf(displacedItem)) {
            InputSimulator.tap(mc.options.inventoryKey);
            screen = null; seq = null; s = S.R_OPENING; deadline = now + 2000;
        } else finish(now);
    }

    private void finish(long now) {
        displacedItem = null; started = false;
        s = S.COOLDOWN; cooldownUntil = Math.max(cooldownUntil, now + 500);
    }

    private void abort(String why, boolean restoreSlot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Debug.log("AutoEat aborted: " + why);
        InputSimulator.release(mc.options.useKey);
        if (seq != null) { seq.cancel(); seq = null; }
        if (screen != null && mc.currentScreen == screen && screen.getScreenHandler().getCursorStack().isEmpty()) screen.close();
        screen = null;
        ClientPlayerEntity p = mc.player;
        if (restoreSlot && p != null && mc.currentScreen == null && prevSlot >= 0 && p.getInventory().getSelectedSlot() != prevSlot)
            InputSimulator.tap(mc.options.hotbarKeys[prevSlot]);
        displacedItem = null; started = false;
        s = S.COOLDOWN; cooldownUntil = Timing.now() + 2500;
    }
}
