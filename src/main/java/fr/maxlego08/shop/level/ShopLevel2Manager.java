package fr.maxlego08.shop.level;

import com.google.gson.reflect.TypeToken;
import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.logger.Logger;
import fr.maxlego08.shop.zcore.utils.ZUtils;
import fr.maxlego08.shop.zcore.utils.storage.Persist;
import fr.maxlego08.shop.zcore.utils.storage.Saveable;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the second (rarity) level system at runtime: tracks per-player exp,
 * persists data to JSON via {@link Persist} and exposes the API used by the
 * commands and placeholders.
 */
public class ShopLevel2Manager extends ZUtils implements Saveable {

    private static final String FILE_NAME = "playershoplevels2";

    private final ShopPlugin plugin;
    private final ShopLevel2Config config;
    private final Map<UUID, PlayerShopLevel2> players = new HashMap<>();

    public ShopLevel2Manager(ShopPlugin plugin, ShopLevel2Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    public ShopLevel2Config getConfig() {
        return this.config;
    }

    /**
     * Get (or lazily create) the {@link PlayerShopLevel2} of the given player.
     */
    public PlayerShopLevel2 getOrCreate(UUID uniqueId) {
        return this.players.computeIfAbsent(uniqueId,
                id -> new PlayerShopLevel2(id, this.config.getMinLevel()));
    }

    public PlayerShopLevel2 getOrCreate(OfflinePlayer player) {
        return getOrCreate(player.getUniqueId());
    }

    /**
     * Add experience to the player's rarity progression. If the player reaches
     * a new level they are notified via {@link Message#LEVEL2_UP}.
     */
    public PlayerShopLevel2 addExp(UUID uniqueId, long amount) {
        if (uniqueId == null || amount <= 0) return uniqueId == null ? null : getOrCreate(uniqueId);
        PlayerShopLevel2 playerLevel = getOrCreate(uniqueId);
        int previousLevel = playerLevel.getLevel();
        playerLevel.addExp(amount);
        applyLevelChange(uniqueId, playerLevel, previousLevel);
        return playerLevel;
    }

    /**
     * Remove experience from the player's rarity progression. Total exp is
     * clamped to 0 and the level is recomputed (and possibly demoted).
     */
    public PlayerShopLevel2 removeExp(UUID uniqueId, long amount) {
        if (uniqueId == null || amount <= 0) return uniqueId == null ? null : getOrCreate(uniqueId);
        PlayerShopLevel2 playerLevel = getOrCreate(uniqueId);
        int previousLevel = playerLevel.getLevel();
        playerLevel.removeExp(amount);
        int recomputed = this.config.computeLevel(playerLevel.getTotalExp());
        if (recomputed != previousLevel) {
            playerLevel.setLevel(recomputed);
        }
        return playerLevel;
    }

    /**
     * Force the player to a specific level. The total exp is updated to match
     * that level's threshold so the change persists across reloads.
     */
    public PlayerShopLevel2 setLevel(UUID uniqueId, int level) {
        PlayerShopLevel2 playerLevel = getOrCreate(uniqueId);
        int clamped = Math.max(this.config.getMinLevel(), Math.min(level, this.config.getMaxLevel()));
        playerLevel.setLevel(clamped);
        playerLevel.setTotalExp(this.config.getExpForLevel(clamped));
        return playerLevel;
    }

    /**
     * Reset the player's progression to the minimum level.
     */
    public PlayerShopLevel2 reset(UUID uniqueId) {
        PlayerShopLevel2 playerLevel = getOrCreate(uniqueId);
        playerLevel.reset();
        playerLevel.setLevel(this.config.getMinLevel());
        return playerLevel;
    }

    /**
     * Compute the progression percentage of the player towards the next
     * rarity level, clamped to {@code [0, 100]}. Players that have reached
     * the maximum level always return {@code 100}.
     */
    public int getProgressPercent(PlayerShopLevel2 playerLevel) {
        if (playerLevel == null) return 0;
        Optional<Long> nextOpt = this.config.getExpForNextLevel(playerLevel.getLevel());
        if (nextOpt.isEmpty()) return 100;
        long next = nextOpt.get();
        long previous = this.config.getExpForLevel(playerLevel.getLevel());
        long denom = next - previous;
        if (denom <= 0) return 100;
        long progress = playerLevel.getTotalExp() - previous;
        if (progress <= 0) return 0;
        if (progress >= denom) return 100;
        return (int) Math.round(progress * 100.0 / denom);
    }

    private void applyLevelChange(UUID uniqueId, PlayerShopLevel2 playerLevel, int previousLevel) {
        int newLevel = this.config.computeLevel(playerLevel.getTotalExp());
        if (newLevel == previousLevel) return;
        playerLevel.setLevel(newLevel);
        if (newLevel <= previousLevel) return;
        Player online = this.plugin.getServer().getPlayer(uniqueId);
        if (online == null) return;
        Logger.info("Level2-up: " + online.getName() + " went from level " + previousLevel
                + " to " + newLevel + " (totalExp=" + playerLevel.getTotalExp() + ").",
                Logger.LogType.SUCCESS);
        this.message(this.plugin, online, Message.LEVEL2_UP,
                "%level%", String.valueOf(newLevel),
                "%max%", String.valueOf(this.config.getMaxLevel()));
    }

    @Override
    public void save(Persist persist) {
        persist.save(new ArrayList<>(this.players.values()), FILE_NAME);
    }

    @Override
    public void load(Persist persist) {
        Type type = new TypeToken<List<PlayerShopLevel2>>() {
        }.getType();
        List<PlayerShopLevel2> loaded = persist.load(type, FILE_NAME);
        this.players.clear();
        if (loaded == null) return;
        for (PlayerShopLevel2 playerLevel : loaded) {
            if (playerLevel == null || playerLevel.getUniqueId() == null) continue;
            int recomputed = this.config.computeLevel(playerLevel.getTotalExp());
            if (recomputed != playerLevel.getLevel()) {
                playerLevel.setLevel(recomputed);
            }
            this.players.put(playerLevel.getUniqueId(), playerLevel);
        }
    }

    public Collection<PlayerShopLevel2> getPlayers() {
        return this.players.values();
    }

    /**
     * Build the leaderboard sorted by descending level, with total exp as a
     * tie-breaker.
     */
    public List<PlayerShopLevel2> getTop() {
        return this.players.values().stream()
                .sorted(Comparator.comparingInt(PlayerShopLevel2::getLevel).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerShopLevel2::getTotalExp).reversed()))
                .collect(Collectors.toList());
    }

    /**
     * @param rank 1-based rank
     * @return the {@link PlayerShopLevel2} at the given rank, or {@code null}
     *         if it does not exist.
     */
    public PlayerShopLevel2 getTop(int rank) {
        if (rank <= 0) return null;
        List<PlayerShopLevel2> top = getTop();
        if (rank > top.size()) return null;
        return top.get(rank - 1);
    }
}