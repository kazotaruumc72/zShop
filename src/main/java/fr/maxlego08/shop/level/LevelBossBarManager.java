package fr.maxlego08.shop.level;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.zcore.logger.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the per-player progression bossbar.
 *
 * <p>The bossbar is shown (and refreshed) every time the player buys or sells
 * an item that contributes to leveling up. After {@link #durationMillis}
 * without any update the bar is hidden again, so the HUD stays out of the
 * way during normal gameplay.</p>
 *
 * <p>Configuration lives under the {@code bossbar} section of {@code levels.yml}:
 * <pre>
 * bossbar:
 *   enabled: true
 *   title: "&amp;eLevel %level% &amp;7| &amp;a%progress%/%required% &amp;7(%percent%%)"
 *   color: GREEN          # PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
 *   style: SEGMENTED_10   # SOLID, SEGMENTED_6/10/12/20
 *   duration-seconds: 5
 * </pre>
 *
 * <p>Folia/Bukkit safe: BossBar mutators (setTitle, setProgress, …) only touch
 * the player's connection, so the manager can be poked from any thread.</p>
 */
public class LevelBossBarManager {

    private final ShopPlugin plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> expiresAt = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private String titleFormat = "&eLevel %level% &7| &a%progress%&7/&a%required% &7(%percent%%)";
    private BarColor color = BarColor.GREEN;
    private BarStyle style = BarStyle.SEGMENTED_10;
    private long durationMillis = 5_000L;

    public LevelBossBarManager(ShopPlugin plugin) {
        this.plugin = plugin;
        // Periodic sweep that hides bars that have not been refreshed within
        // durationMillis. 20 ticks granularity is plenty for a 5-second bar.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * (Re)load the configuration from {@code levels.yml}. Falls back to
     * sensible defaults when fields or the whole section are missing.
     */
    public void load() {
        File file = new File(this.plugin.getDataFolder(), "levels.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("bossbar");
        if (section == null) {
            this.enabled = false;
            return;
        }
        this.enabled = section.getBoolean("enabled", true);
        this.titleFormat = section.getString("title", this.titleFormat);
        this.color = parseEnum(BarColor.class, section.getString("color", this.color.name()), this.color);
        this.style = parseEnum(BarStyle.class, section.getString("style", this.style.name()), this.style);
        this.durationMillis = Math.max(500L, section.getLong("duration-seconds", 5L) * 1000L);
    }

    /**
     * Push a fresh state to the player's bossbar. Called by
     * {@link ZLevelManager#addExp(Player, String, int, fr.maxlego08.shop.api.history.HistoryType)}
     * after every successful transaction.
     */
    public void update(Player player, PlayerLevel playerLevel, LevelConfig levelConfig) {
        if (!this.enabled || player == null || playerLevel == null || !player.isOnline()) return;

        long currentLevelThreshold = levelConfig.getItemsForLevel(playerLevel.getLevel());
        long progressInLevel = Math.max(0L, playerLevel.getTotalItems() - currentLevelThreshold);
        Optional<Long> nextOpt = levelConfig.getItemsForNextLevel(playerLevel.getLevel());

        long required;
        double progressFraction;
        if (nextOpt.isEmpty()) {
            // Player is at the maximum level: keep the bar full.
            required = Math.max(1L, progressInLevel);
            progressFraction = 1.0;
        } else {
            required = Math.max(1L, nextOpt.get() - currentLevelThreshold);
            progressFraction = Math.min(1.0, Math.max(0.0, (double) progressInLevel / (double) required));
        }

        double bonus = levelConfig.getBonusPercent(playerLevel.getLevel());
        String title = ChatColor.translateAlternateColorCodes('&', this.titleFormat
                .replace("%level%", String.valueOf(playerLevel.getLevel()))
                .replace("%level_max%", String.valueOf(levelConfig.getMaxLevel()))
                .replace("%progress%", String.valueOf(progressInLevel))
                .replace("%required%", String.valueOf(required))
                .replace("%total%", String.valueOf(playerLevel.getTotalItems()))
                .replace("%percent%", String.valueOf((int) Math.round(progressFraction * 100.0)))
                .replace("%bonus%", ZLevelManager.formatBonus(bonus)));

        UUID uid = player.getUniqueId();
        BossBar bar = this.bars.get(uid);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, this.color, this.style);
            bar.addPlayer(player);
            this.bars.put(uid, bar);
        } else {
            bar.setTitle(title);
            bar.setColor(this.color);
            bar.setStyle(this.style);
            // Re-add if removeAll() was called by the cleanup tick (player went
            // idle for a while, then sold something again).
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        }
        bar.setProgress(progressFraction);
        bar.setVisible(true);

        this.expiresAt.put(uid, System.currentTimeMillis() + this.durationMillis);
    }

    /**
     * Hide and discard the bar for the given player. Should be called from a
     * {@code PlayerQuitEvent} listener so we do not leak a {@link BossBar}
     * reference per-disconnect.
     */
    public void remove(UUID uniqueId) {
        BossBar bar = this.bars.remove(uniqueId);
        if (bar != null) bar.removeAll();
        this.expiresAt.remove(uniqueId);
    }

    /**
     * Hide every bossbar (used on plugin disable / reload).
     */
    public void removeAll() {
        for (BossBar bar : this.bars.values()) bar.removeAll();
        this.bars.clear();
        this.expiresAt.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = this.expiresAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() < now) {
                BossBar bar = this.bars.remove(entry.getKey());
                if (bar != null) bar.removeAll();
                it.remove();
            }
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            Logger.info("Invalid value '" + value + "' for " + type.getSimpleName()
                    + " in levels.yml > bossbar; falling back to " + fallback.name() + ".",
                    Logger.LogType.WARNING);
            return fallback;
        }
    }
}

