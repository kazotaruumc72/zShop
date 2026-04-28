package fr.maxlego08.shop.level;

import com.google.gson.reflect.TypeToken;
import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.utils.ZUtils;
import fr.maxlego08.shop.zcore.utils.storage.Persist;
import fr.maxlego08.shop.zcore.utils.storage.Saveable;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the level/exp system at runtime: tracks per-player progression,
 * persists it to JSON via {@link Persist}, and exposes the bonus percentage
 * applied on prices.
 */
public class ZLevelManager extends ZUtils implements Saveable {

    private static final String FILE_NAME = "playerlevels";

    private final transient ShopPlugin plugin;
    private final transient LevelConfig levelConfig;
    private final transient Map<UUID, PlayerLevel> players = new HashMap<>();

    public ZLevelManager(ShopPlugin plugin, LevelConfig levelConfig) {
        this.plugin = plugin;
        this.levelConfig = levelConfig;
    }

    public LevelConfig getConfig() {
        return this.levelConfig;
    }

    /**
     * Get (or lazily create) the {@link PlayerLevel} of the given player.
     */
    public PlayerLevel getOrCreate(UUID uniqueId) {
        return this.players.computeIfAbsent(uniqueId, id -> new PlayerLevel(id, this.levelConfig.getMinLevel()));
    }

    public PlayerLevel getOrCreate(OfflinePlayer player) {
        return getOrCreate(player.getUniqueId());
    }

    /**
     * Get the bonus percentage that applies to the given player's prices, or 0
     * if no level applies.
     */
    public double getBonusPercent(Player player) {
        if (player == null) return 0;
        PlayerLevel playerLevel = getOrCreate(player.getUniqueId());
        return this.levelConfig.getBonusPercent(playerLevel.getLevel());
    }

    /**
     * Apply the level bonus to a buy price (a discount).
     */
    public double applyBuyBonus(Player player, double price) {
        double bonus = getBonusPercent(player);
        if (bonus <= 0) return price;
        return price * (1.0 - bonus / 100.0);
    }

    /**
     * Apply the level bonus to a sell price (a gain).
     */
    public double applySellBonus(Player player, double price) {
        double bonus = getBonusPercent(player);
        if (bonus <= 0) return price;
        return price * (1.0 + bonus / 100.0);
    }

    /**
     * Add experience to the player after a buy/sell transaction. If the player
     * reaches a new level, they are notified via the {@link Message#LEVEL_UP}
     * message.
     *
     * @param player        the player concerned
     * @param materialOrId  the material name (or custom item id) traded
     * @param amount        the amount of items traded
     * @return the updated {@link PlayerLevel}
     */
    public PlayerLevel addExp(Player player, String materialOrId, int amount) {
        if (player == null || amount <= 0) return null;
        PlayerLevel playerLevel = getOrCreate(player.getUniqueId());

        int expPerItem = this.levelConfig.getExp(materialOrId);
        playerLevel.addItems(amount);
        if (expPerItem > 0) {
            playerLevel.addExp((long) expPerItem * amount);
        }

        int previousLevel = playerLevel.getLevel();
        int newLevel = this.levelConfig.computeLevel(playerLevel.getTotalItems());
        if (newLevel > previousLevel) {
            playerLevel.setLevel(newLevel);
            double bonus = this.levelConfig.getBonusPercent(newLevel);
            this.message(this.plugin, player, Message.LEVEL_UP,
                    "%level%", String.valueOf(newLevel),
                    "%bonus%", formatBonus(bonus));
        } else if (newLevel != previousLevel) {
            playerLevel.setLevel(newLevel);
        }
        return playerLevel;
    }

    /**
     * Format a bonus percentage for display, stripping any trailing zero decimal.
     */
    public static String formatBonus(double bonus) {
        if (bonus == Math.floor(bonus)) return String.valueOf((long) bonus);
        return String.valueOf(bonus);
    }

    /**
     * Force the player to a specific level. The total items counter is updated
     * to match the requirement of that level so the player keeps it on relog.
     */
    public PlayerLevel setLevel(UUID uniqueId, int level) {
        PlayerLevel playerLevel = getOrCreate(uniqueId);
        int clamped = Math.max(this.levelConfig.getMinLevel(), Math.min(level, this.levelConfig.getMaxLevel()));
        playerLevel.setLevel(clamped);
        return playerLevel;
    }

    /**
     * Reset the progression of the given player to the minimum level.
     */
    public PlayerLevel reset(UUID uniqueId) {
        PlayerLevel playerLevel = getOrCreate(uniqueId);
        playerLevel.reset();
        playerLevel.setLevel(this.levelConfig.getMinLevel());
        return playerLevel;
    }

    @Override
    public void save(Persist persist) {
        persist.save(new ArrayList<>(this.players.values()), FILE_NAME);
    }

    @Override
    public void load(Persist persist) {
        Type type = new TypeToken<List<PlayerLevel>>() {
        }.getType();
        List<PlayerLevel> loaded = persist.load(type, FILE_NAME);
        this.players.clear();
        if (loaded == null) return;
        for (PlayerLevel playerLevel : loaded) {
            if (playerLevel == null || playerLevel.getUniqueId() == null) continue;
            // Recompute the level in case progression has changed.
            int recomputed = this.levelConfig.computeLevel(playerLevel.getTotalItems());
            if (recomputed > playerLevel.getLevel()) {
                playerLevel.setLevel(recomputed);
            }
            this.players.put(playerLevel.getUniqueId(), playerLevel);
        }
    }

    public Collection<PlayerLevel> getPlayers() {
        return this.players.values();
    }
}
