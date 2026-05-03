package fr.maxlego08.shop.level;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.zcore.logger.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and exposes the configuration of the second (rarity) level system.
 *
 * <p>{@code shop_levels.yml} entries follow the {@code <level>:<requiredExp>}
 * convention. Players start at level 0 and progress to the highest level
 * whose threshold they have reached.
 */
public class ShopLevel2Config {

    private static final int MIN_LEVEL = 0;

    private final ShopPlugin plugin;
    /** Insertion-ordered map of {@code level -> requiredExp}. */
    private final Map<Integer, Long> thresholds = new LinkedHashMap<>();
    private final List<Integer> sortedLevels = new ArrayList<>();

    public ShopLevel2Config(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Save the default resource file (if missing) and load it.
     */
    public void load() {
        saveDefaultResource("shop_levels.yml");
        loadLevels();
    }

    private void saveDefaultResource(String name) {
        File file = new File(this.plugin.getDataFolder(), name);
        if (!file.exists()) {
            this.plugin.saveResource(name, false);
        }
    }

    private void loadLevels() {
        this.thresholds.clear();
        this.sortedLevels.clear();
        File file = new File(this.plugin.getDataFolder(), "shop_levels.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        List<?> rawLevels = configuration.getList("levels", new ArrayList<>());
        for (Object raw : rawLevels) {
            parseEntry(raw, "shop_levels.yml", (key, value) -> {
                try {
                    int level = Integer.parseInt(key);
                    long required = Long.parseLong(value);
                    if (level <= 0) {
                        Logger.info("Invalid level in shop_levels.yml: " + level
                                + " (must be >= 1). Entry ignored.", Logger.LogType.WARNING);
                        return;
                    }
                    if (required < 0L) {
                        Logger.info("Invalid required exp in shop_levels.yml for level "
                                + level + ": " + required + " (must be >= 0). Entry ignored.",
                                Logger.LogType.WARNING);
                        return;
                    }
                    this.thresholds.put(level, required);
                } catch (NumberFormatException ex) {
                    Logger.info("Invalid number in shop_levels.yml for entry: " + key + ":" + value,
                            Logger.LogType.WARNING);
                }
            });
        }
        this.sortedLevels.addAll(this.thresholds.keySet());
        this.sortedLevels.sort(Comparator.naturalOrder());
        if (this.thresholds.isEmpty()) {
            Logger.info("shop_levels.yml has no valid entries; %zshop_level2% will fall back to 0. "
                    + "Make sure each entry is quoted, e.g. - '1:25000'.", Logger.LogType.WARNING);
        }
    }

    /**
     * Parse a YAML list entry that follows the {@code key:value} convention.
     * Mirrors {@link LevelConfig}'s parser so {@code shop_levels.yml} can be
     * written with the same conventions, including the YAML-1.1 sexagesimal
     * recovery for unquoted entries.
     */
    private void parseEntry(Object raw, String fileName,
                            java.util.function.BiConsumer<String, String> consumer) {
        if (raw == null) return;
        if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            if (map.size() != 1) {
                Logger.info("Invalid " + fileName + " entry: " + raw, Logger.LogType.WARNING);
                return;
            }
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            if (entry.getKey() == null || entry.getValue() == null) {
                Logger.info("Invalid " + fileName + " entry: " + raw, Logger.LogType.WARNING);
                return;
            }
            consumer.accept(String.valueOf(entry.getKey()).trim(),
                    String.valueOf(entry.getValue()).trim());
            return;
        }
        if (raw instanceof Number) {
            long value = ((Number) raw).longValue();
            if (value >= 60) {
                long minutes = value % 60;
                long hours = value / 60;
                Logger.info("Entry in " + fileName + " was parsed as a sexagesimal number ("
                        + value + "). Recovering it as " + hours + ":" + minutes
                        + ". Please wrap your entries in quotes (e.g. '1:25000') to silence this warning.",
                        Logger.LogType.WARNING);
                consumer.accept(String.valueOf(hours), String.valueOf(minutes));
                return;
            }
        }
        String entry = String.valueOf(raw);
        int idx = entry.lastIndexOf(':');
        if (idx <= 0 || idx == entry.length() - 1) {
            Logger.info("Invalid " + fileName + " entry: " + entry, Logger.LogType.WARNING);
            return;
        }
        consumer.accept(entry.substring(0, idx).trim(), entry.substring(idx + 1).trim());
    }

    /**
     * @return the minimum level a player can have. Always 0 — players start
     *         "below" any defined rarity tier and unlock them as they gain
     *         experience.
     */
    public int getMinLevel() {
        return MIN_LEVEL;
    }

    /**
     * @return the highest level number defined in {@code shop_levels.yml}, or
     *         {@link #getMinLevel()} if none is defined.
     */
    public int getMaxLevel() {
        if (this.sortedLevels.isEmpty()) return MIN_LEVEL;
        return this.sortedLevels.get(this.sortedLevels.size() - 1);
    }

    /**
     * @return the cumulated exp required to reach the given level, or 0 if the
     *         level is the minimum (or has no threshold entry).
     */
    public long getExpForLevel(int level) {
        if (level <= MIN_LEVEL) return 0L;
        Long required = this.thresholds.get(level);
        return required == null ? 0L : required;
    }

    /**
     * @return the cumulated exp required to reach the next level above
     *         {@code currentLevel}, or empty if the player is already at the
     *         maximum level.
     */
    public Optional<Long> getExpForNextLevel(int currentLevel) {
        Long best = null;
        for (Integer level : this.sortedLevels) {
            if (level <= currentLevel) continue;
            Long required = this.thresholds.get(level);
            if (required == null) continue;
            if (best == null || required < best) best = required;
        }
        return Optional.ofNullable(best);
    }

    /**
     * @return the highest level reachable for the given cumulated exp, clamped
     *         to {@code [minLevel, maxLevel]}.
     */
    public int computeLevel(long totalExp) {
        int current = MIN_LEVEL;
        for (Integer level : this.sortedLevels) {
            Long required = this.thresholds.get(level);
            if (required == null) continue;
            if (totalExp >= required && level > current) {
                current = level;
            }
        }
        return current;
    }

    public List<Integer> getSortedLevels() {
        return this.sortedLevels;
    }
}