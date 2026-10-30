package dev.autoeattotem.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class ConfigScreen extends Screen {
    private enum Page {
        AUTOEAT("AutoEat"),
        AUTOTOTEM("AutoTotem"),
        GENERAL("General");

        final String label;
        Page(String l) { label = l; }
    }

    private final Screen parent;
    private Page page = Page.AUTOEAT;
    private int idx, cw, left, right;

    public ConfigScreen(Screen parent) {
        super(Text.literal("AutoEat + AutoTotem"));
        this.parent = parent;
    }

    private int px(int i) { return i % 2 == 0 ? left : right; }
    private int py(int i) { return 52 + (i / 2) * 24; }

    @Override
    protected void init() {
        ModConfig c = ModConfig.get();
        cw = Math.min(200, (width - 30) / 2);
        left = width / 2 - cw - 5;
        right = width / 2 + 5;
        idx = 0;

        int tabW = Math.min(120, (width - 30) / 3);
        int tx = width / 2 - (tabW * 3 + 10) / 2;
        int n = 0;

        for (Page pg : Page.values()) {
            ButtonWidget b = ButtonWidget.builder(
                Text.literal(pg.label),
                btn -> { page = pg; clearAndInit(); }
            ).dimensions(tx + n * (tabW + 5), 24, tabW, 20).build();

            b.active = pg != page;
            addDrawableChild(b);
            n++;
        }

        switch (page) {
            case AUTOEAT -> {
                toggle("AutoEat", () -> c.eatEnabled, v -> c.eatEnabled = v);
                slider("Hunger threshold", 1, 19, 1, () -> c.hungerThreshold, v -> c.hungerThreshold = (int) v, " (food pts)");
                cycle("Food priority", () -> c.foodPriority, v -> c.foodPriority = v, ModConfig.FoodPriority.values());
                toggle("Search inventory", () -> c.eatSearchInventory, v -> c.eatSearchInventory = v);
                toggle("Auto-move food to hotbar", () -> c.autoMoveToHotbar, v -> c.autoMoveToHotbar = v);
                toggle("Restore previous slot", () -> c.restorePreviousSlot, v -> c.restorePreviousSlot = v);
                slider("Eating delay", 50, 2000, 50, () -> c.eatDelayMs, v -> c.eatDelayMs = (int) v, " ms");
                toggle("Combat interruption prevention", () -> c.combatPrevention, v -> c.combatPrevention = v);
                slider("Combat window", 500, 10000, 500, () -> c.combatWindowMs, v -> c.combatWindowMs = (int) v, " ms");
                slider("Eat anyway at food <=", 0, 19, 1, () -> c.urgentFoodLevel, v -> c.urgentFoodLevel = (int) v, "");
                textField("Preferred foods (ids, comma separated)", () -> c.preferredFoods, v -> c.preferredFoods = v);
                textField("Never eat (ids, comma separated)", () -> c.blacklistedFoods, v -> c.blacklistedFoods = v);
            }

            case AUTOTOTEM -> {
                toggle("AutoTotem", () -> c.totemEnabled, v -> c.totemEnabled = v);
                toggle("Search entire inventory", () -> c.totemSearchInventory, v -> c.totemSearchInventory = v);
                toggle("Search hotbar", () -> c.totemSearchHotbar, v -> c.totemSearchHotbar = v);
                slider("Health threshold", 1, 20, 1, () -> c.healthThreshold, v -> c.healthThreshold = (int) v, " HP");
                slider("Swap delay", 50, 2000, 50, () -> c.swapDelayMs, v -> c.swapDelayMs = (int) v, " ms");
                slider("Inventory open delay", 50, 2000, 50, () -> c.inventoryOpenDelayMs, v -> c.inventoryOpenDelayMs = (int) v, " ms");
                slider("Inventory close delay", 50, 2000, 50, () -> c.inventoryCloseDelayMs, v -> c.inventoryCloseDelayMs = (int) v, " ms");
                toggle("Verify successful equip", () -> c.verifySwap, v -> c.verifySwap = v);
                toggle("Swap over non-totem offhand item", () -> c.replaceNonTotemOffhand, v -> c.replaceNonTotemOffhand = v);
            }

            case GENERAL -> {
                toggle("Debug mode", () -> c.debug, v -> c.debug = v);
                toggle("Human-like input timing", () -> c.humanLikeTiming, v -> c.humanLikeTiming = v);
                toggle("Input conflict prevention", () -> c.inputConflictPrevention, v -> c.inputConflictPrevention = v);
                slider("Click delay", 50, 1000, 10, () -> c.clickDelayMs, v -> c.clickDelayMs = (int) v, " ms");
            }
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
            .dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    private static Text onOff(String label, boolean v) {
        return Text.literal(label + ": " + (v ? "ON" : "OFF"));
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        int i = idx++;
        addDrawableChild(ButtonWidget.builder(onOff(label, get.getAsBoolean()), b -> {
            boolean v = !get.getAsBoolean();
            set.accept(v);
            b.setMessage(onOff(label, v));
        }).dimensions(px(i), py(i), cw, 20).build());
    }

    private <E extends Enum<E>> void cycle(
        String label, Supplier<E> get, Consumer<E> set, E[] values
    ) {
        int i = idx++;
        addDrawableChild(ButtonWidget.builder(
            Text.literal(label + ": " + get.get().name()),
            b -> {
                E next = values[(get.get().ordinal() + 1) % values.length];
                set.accept(next);
                b.setMessage(Text.literal(label + ": " + next.name()));
            }
        ).dimensions(px(i), py(i), cw, 20).build());
    }

    private void slider(
        String label, double min, double max, double step,
        DoubleSupplier get, DoubleConsumer set, String suffix
    ) {
        int i = idx++;
        addDrawableChild(new ValueSlider(
            px(i), py(i), cw, 20, label, min, max, step,
            get.getAsDouble(), set, suffix
        ));
    }

    private void textField(String hint, Supplier<String> get, Consumer<String> set) {
        if (idx % 2 == 1) idx++;
        int i = idx;
        idx += 2;

        TextFieldWidget f = new TextFieldWidget(
            textRenderer, left, py(i), cw * 2 + 10, 20, Text.literal(hint)
        );
        f.setMaxLength(512);
        f.setPlaceholder(Text.literal(hint));
        f.setText(get.get());
        f.setChangedListener(set::accept);
        addDrawableChild(f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);

        if (page == Page.GENERAL) {
            ctx.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Keybinds (toggle AutoEat, toggle AutoTotem, open config): Options > Controls > Key Binds"),
                width / 2, py(4) + 8, 0xFFAAAAAA
            );
        }
    }

    @Override
    public void close() {
        ModConfig.save();
        if (client != null) client.setScreen(parent);
    }

    private static final class ValueSlider extends SliderWidget {
        private final String label, suffix;
        private final double min, max, step;
        private final DoubleConsumer onChange;

        ValueSlider(
            int x, int y, int w, int h, String label,
            double min, double max, double step,
            double initial, DoubleConsumer onChange, String suffix
        ) {
            super(x, y, w, h, Text.empty(), (initial - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            this.suffix = suffix;
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            return Math.max(min, Math.min(max, Math.round(v / step) * step));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + (long) current() + suffix));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
