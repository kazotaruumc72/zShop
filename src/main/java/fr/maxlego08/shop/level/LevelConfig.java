package fr.maxlego08.shop.level;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.zcore.logger.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and exposes the configuration of the level/exp system.
 * <p>
 * Three YAML files are read:
 * <ul>
 *     <li>{@code exp.yml} – experience gained per item</li>
 *     <li>{@code levels.yml} – levels and their bonus percentage</li>
 *     <li>{@code progression.yml} – cumulated items required to reach a level</li>
 * </ul>
 */
public class LevelConfig {

    private final ShopPlugin plugin;
    private final Map<String, Integer> expByItem = new HashMap<>();
    private final List<ShopLevel> levels = new ArrayList<>();
    private final Map<Integer, Long> progression = new HashMap<>();

    public LevelConfig(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Save the default resource files (if missing) and load them.
     */
    public void load() {

        saveDefaultResource("exp.yml");
        saveDefaultResource("levels.yml");
        saveDefaultResource("progression.yml");

        loadExp();
        loadLevels();
        loadProgression();
    }

    private void saveDefaultResource(String name) {
        File file = new File(this.plugin.getDataFolder(), name);
        if (!file.exists()) {
            this.plugin.saveResource(name, false);
        }
    }

    private void loadExp() {
        this.expByItem.clear();
        File file = new File(this.plugin.getDataFolder(), "exp.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        List<?> items = configuration.getList("items", new ArrayList<>());
        for (Object raw : items) {
            parseEntry(raw, "exp.yml", (key, value) -> {
                try {
                    int exp = Integer.parseInt(value);
                    this.expByItem.put(normalize(key), exp);
                } catch (NumberFormatException ex) {
                    Logger.info("Invalid exp value in exp.yml for " + key + ": " + value, Logger.LogType.WARNING);
                }
            });
        }
    }

    private void loadLevels() {
        this.levels.clear();
        File file = new File(this.plugin.getDataFolder(), "levels.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        List<?> rawLevels = configuration.getList("levels", new ArrayList<>());
        for (Object raw : rawLevels) {
            parseEntry(raw, "levels.yml", (key, value) -> {
                try {
                    int level = Integer.parseInt(key);
                    double bonus = Double.parseDouble(value);
                    if (level <= 0) {
                        Logger.info("Invalid level in levels.yml: " + level + " (must be >= 1). Entry ignored.",
                                Logger.LogType.WARNING);
                        return;
                    }
                    this.levels.add(new ShopLevel(level, bonus));
                } catch (NumberFormatException ex) {
                    Logger.info("Invalid number in levels.yml for entry: " + key + ":" + value, Logger.LogType.WARNING);
                }
            });
        }
        this.levels.sort(Comparator.comparingInt(ShopLevel::getLevel));
        if (this.levels.isEmpty()) {
            Logger.info("levels.yml has no valid entries; %zshop_level% will fall back to 1. "
                    + "Make sure each entry is quoted, e.g. - '1:0'.", Logger.LogType.WARNING);
        }
    }

    private void loadProgression() {
        this.progression.clear();
        File file = new File(this.plugin.getDataFolder(), "progression.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        List<?> rawProgression = configuration.getList("progression", new ArrayList<>());
        for (Object raw : rawProgression) {
            parseEntry(raw, "progression.yml", (key, value) -> {
                try {
                    int level = Integer.parseInt(key);
                    long amount = Long.parseLong(value);
                    if (level <= 0) {
                        Logger.info("Invalid level in progression.yml: " + level + " (must be >= 1). Entry ignored.",
                                Logger.LogType.WARNING);
                        return;
                    }
                    this.progression.put(level, amount);
                } catch (NumberFormatException ex) {
                    Logger.info("Invalid number in progression.yml for entry: " + key + ":" + value, Logger.LogType.WARNING);
                }
            });
        }
    }

    /**
     * Parse a YAML list entry that follows the {@code key:value} convention.
     * Depending on quoting, the YAML parser may produce either a plain string
     * such as {@code "STICK:1"} or a single-entry map such as
     * {@code {nexo:item_id=1}}. This helper accepts both representations.
     *
     * <p>Older versions of the default {@code levels.yml} shipped unquoted
     * entries like {@code - 1:0}. SnakeYAML applies YAML 1.1's sexagesimal
     * resolver to those, turning {@code 1:0} into the integer {@code 60},
     * {@code 2:2} into {@code 122}, and so on. To stay backwards compatible
     * with files already extracted to disk, we accept bare {@link Number}
     * entries and reverse the {@code H:M} conversion (so {@code 60} becomes
     * {@code "1:0"}). A warning is logged so the user is aware that they
     * should quote their entries.
     */
    private void parseEntry(Object raw, String fileName, java.util.function.BiConsumer<String, String> consumer) {
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
            consumer.accept(String.valueOf(entry.getKey()).trim(), String.valueOf(entry.getValue()).trim());
            return;
        }
        if (raw instanceof Number) {
            long value = ((Number) raw).longValue();
            // YAML 1.1 sexagesimal "H:M" requires hours >= 1 and minutes in [0,59],
            // so the smallest possible result is 1*60+0 = 60. Any bare number below
            // that cannot be the byproduct of sexagesimal parsing — treat it as a
            // genuinely invalid entry instead of silently turning, e.g., `5` into
            // `0:5` (level 0, which then breaks %zshop_level%/%zshop_level_max%).
            if (value >= 60) {
                long minutes = value % 60;
                long hours = value / 60;
                Logger.info("Entry in " + fileName + " was parsed as a sexagesimal number (" + value
                        + "). Recovering it as " + hours + ":" + minutes
                        + ". Please wrap your entries in quotes (e.g. '1:0') to silence this warning.",
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

    private String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    /**
     * @return the experience gained when buying or selling a single item, or 0 if the item is not registered.
     */
    public int getExp(String materialOrId) {
        if (materialOrId == null) return 0;
        return this.expByItem.getOrDefault(normalize(materialOrId), 0);
    }

    /**
     * @return the minimum level number defined in levels.yml, or 1 if none is defined.
     *         Always returns at least 1 even when {@code levels.yml} contains
     *         only invalid entries, so {@code %zshop_level%} never renders as 0.
     */
    public int getMinLevel() {
        if (this.levels.isEmpty()) return 1;
        return Math.max(1, this.levels.get(0).getLevel());
    }

    /**
     * @return the maximum level number defined in levels.yml, or {@link #getMinLevel()} if none is defined.
     *         Always returns at least 1.
     */
    public int getMaxLevel() {
        if (this.levels.isEmpty()) return getMinLevel();
        return Math.max(1, this.levels.get(this.levels.size() - 1).getLevel());
    }

    public List<ShopLevel> getLevels() {
        return this.levels;
    }

    /**
     * @return the bonus percent for the given level, or 0 if the level is not defined.
     */
    public double getBonusPercent(int level) {
        for (ShopLevel shopLevel : this.levels) {
            if (shopLevel.getLevel() == level) return shopLevel.getBonusPercent();
        }
        return 0;
    }

    /**
     * @return the highest level reachable for the given cumulated items.
     */
    public int computeLevel(long totalItems) {
        int current = getMinLevel();
        for (ShopLevel shopLevel : this.levels) {
            Long required = this.progression.get(shopLevel.getLevel());
            if (required == null) continue;
            if (totalItems >= required && shopLevel.getLevel() > current) {
                current = shopLevel.getLevel();
            }
        }
        return current;
    }

    /**
     * @return the cumulated items required to reach the given level, or 0 if
     *         the level is the minimum (or has no progression entry).
     */
    public long getItemsForLevel(int level) {
        Long required = this.progression.get(level);
        return required == null ? 0L : required;
    }

    /**
     * @return the cumulated items required to reach the next level, or empty if already at the maximum level.
     */
    public Optional<Long> getItemsForNextLevel(int currentLevel) {
        Long best = null;
        for (ShopLevel shopLevel : this.levels) {
            if (shopLevel.getLevel() <= currentLevel) continue;
            Long required = this.progression.get(shopLevel.getLevel());
            if (required == null) continue;
            if (best == null || required < best) best = required;
        }
        return Optional.ofNullable(best);
    }
}
