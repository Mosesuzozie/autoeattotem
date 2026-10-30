package dev.autoeattotem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.autoeattotem.AutoEatTotemMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModConfig {
    public enum FoodPriority { SATURATION, NUTRITION, LEAST_WASTE, PREFERRED_LIST }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("autoeattotem.json");
    private static ModConfig instance = new ModConfig();

    public boolean debug = false;
    public boolean humanLikeTiming = true;
    public boolean inputConflictPrevention = true;
    public int clickDelayMs = 120;

    public boolean eatEnabled = true;
    public int hungerThreshold = 14;
    public FoodPriority foodPriority = FoodPriority.SATURATION;
    public boolean eatSearchInventory = true;
    public boolean autoMoveToHotbar = true;
    public boolean restorePreviousSlot = true;
    public int eatDelayMs = 250;
    public boolean combatPrevention = true;
    public int combatWindowMs = 3000;
    public int urgentFoodLevel = 6;
    public String preferredFoods = "golden_carrot,cooked_beef,cooked_porkchop,cooked_mutton,cooked_salmon,bread";
    public String blacklistedFoods = "rotten_flesh,spider_eye,pufferfish,poisonous_potato,chorus_fruit,suspicious_stew,enchanted_golden_apple,golden_apple";

    public boolean totemEnabled = true;
    public boolean totemSearchInventory = true;
    public boolean totemSearchHotbar = true;
    public int healthThreshold = 20;
    public int swapDelayMs = 150;
    public int inventoryOpenDelayMs = 150;
    public int inventoryCloseDelayMs = 150;
    public boolean verifySwap = true;
    public boolean replaceNonTotemOffhand = true;

    public static ModConfig get() { return instance; }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    ModConfig c = GSON.fromJson(r, ModConfig.class);
                    if (c != null) { c.sanitize(); instance = c; }
                }
            } else {
                save();
            }
        } catch (Exception e) {
            AutoEatTotemMod.LOGGER.error("Failed to load config, using defaults", e);
        }
    }

    public static void save() {
        try {
            instance.sanitize();
            try (Writer w = Files.newBufferedWriter(PATH)) { GSON.toJson(instance, w); }
        } catch (Exception e) {
            AutoEatTotemMod.LOGGER.error("Failed to save config", e);
        }
    }

    private void sanitize() {
        hungerThreshold = clamp(hungerThreshold, 1, 19);
        urgentFoodLevel = clamp(urgentFoodLevel, 0, 19);
        healthThreshold = clamp(healthThreshold, 1, 20);
        clickDelayMs = clamp(clickDelayMs, 50, 1000);
        eatDelayMs = clamp(eatDelayMs, 50, 2000);
        swapDelayMs = clamp(swapDelayMs, 50, 2000);
        inventoryOpenDelayMs = clamp(inventoryOpenDelayMs, 50, 2000);
        inventoryCloseDelayMs = clamp(inventoryCloseDelayMs, 50, 2000);
        combatWindowMs = clamp(combatWindowMs, 500, 10000);
        if (foodPriority == null) foodPriority = FoodPriority.SATURATION;
        if (preferredFoods == null) preferredFoods = "";
        if (blacklistedFoods == null) blacklistedFoods = "";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static List<String> parseIds(String csv) {
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            String t = s.trim().toLowerCase();
            if (t.isEmpty()) continue;
            if (t.startsWith("minecraft:")) t = t.substring(10);
            out.add(t);
        }
        return out;
    }
}
