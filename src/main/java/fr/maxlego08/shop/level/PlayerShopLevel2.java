package fr.maxlego08.shop.level;

import java.util.UUID;

/**
 * Per-player data tracked by the second (rarity) level system.
 *
 * <p>{@code totalExp} is the cumulated experience of the player on this
 * progression track. {@code level} is the highest rarity level reached
 * (recomputed from {@code totalExp} against {@link ShopLevel2Config}).
 */
public class PlayerShopLevel2 {

    private UUID uniqueId;
    private long totalExp;
    private int level;

    @SuppressWarnings("unused") // Used by Gson for deserialization.
    private PlayerShopLevel2() {
    }

    public PlayerShopLevel2(UUID uniqueId, int level) {
        this.uniqueId = uniqueId;
        this.level = level;
        this.totalExp = 0;
    }

    public UUID getUniqueId() {
        return this.uniqueId;
    }

    public long getTotalExp() {
        return this.totalExp;
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setTotalExp(long totalExp) {
        this.totalExp = Math.max(0L, totalExp);
    }

    public void addExp(long amount) {
        if (amount <= 0) return;
        this.totalExp += amount;
    }

    public void removeExp(long amount) {
        if (amount <= 0) return;
        this.totalExp = Math.max(0L, this.totalExp - amount);
    }

    public void reset() {
        this.totalExp = 0;
    }
}