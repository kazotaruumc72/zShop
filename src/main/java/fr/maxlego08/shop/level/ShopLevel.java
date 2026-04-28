package fr.maxlego08.shop.level;

/**
 * Represents a shop level entry as defined in {@code levels.yml}.
 *
 * <p>The bonus percentage is applied:
 * <ul>
 *     <li>as a discount when the player buys an item</li>
 *     <li>as a gain when the player sells an item</li>
 * </ul>
 */
public class ShopLevel {

    private final int level;
    private final double bonusPercent;

    public ShopLevel(int level, double bonusPercent) {
        this.level = level;
        this.bonusPercent = bonusPercent;
    }

    public int getLevel() {
        return this.level;
    }

    public double getBonusPercent() {
        return this.bonusPercent;
    }
}
