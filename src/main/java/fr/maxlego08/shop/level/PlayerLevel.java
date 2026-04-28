package fr.maxlego08.shop.level;

import java.util.UUID;

/**
 * Per-player data tracked by the level/exp system.
 *
 * <p>{@code totalItems} is the cumulated amount of items the player has bought or sold.
 * {@code totalExp} is the cumulated experience gained from {@code exp.yml}.
 * {@code level} is recomputed from {@code totalItems} against the loaded progression.
 */
public class PlayerLevel {

    private UUID uniqueId;
    private long totalItems;
    private long totalExp;
    private int level;

    @SuppressWarnings("unused") // Used by Gson for deserialization.
    private PlayerLevel() {
    }

    public PlayerLevel(UUID uniqueId, int level) {
        this.uniqueId = uniqueId;
        this.level = level;
        this.totalItems = 0;
        this.totalExp = 0;
    }

    public UUID getUniqueId() {
        return this.uniqueId;
    }

    public long getTotalItems() {
        return this.totalItems;
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

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }

    public void addItems(long amount) {
        if (amount <= 0) return;
        this.totalItems += amount;
    }

    public void addExp(long amount) {
        if (amount <= 0) return;
        this.totalExp += amount;
    }

    public void reset() {
        this.totalItems = 0;
        this.totalExp = 0;
    }
}
