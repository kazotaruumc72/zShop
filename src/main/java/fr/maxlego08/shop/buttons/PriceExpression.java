package fr.maxlego08.shop.buttons;

import fr.maxlego08.shop.placeholder.ZShopPlaceholders;
import org.bukkit.entity.Player;

/**
 * A buy/sell price that is either a literal number or a placeholder formula
 * (e.g. {@code %math_0:0_12*({zshop_level}*2)%}). The formula is resolved
 * through PlaceholderAPI on every evaluation, so the price reacts to the
 * caller's current level/state.
 */
public final class PriceExpression {

    private final String formula;
    private final Double literal;

    public PriceExpression(Object value) {
        String parsedFormula = null;
        Double parsedLiteral = null;
        if (value == null) {
            parsedLiteral = 0.0;
        } else if (value instanceof Number number) {
            parsedLiteral = number.doubleValue();
        } else {
            String raw = value.toString();
            if (raw.indexOf('%') < 0) {
                try {
                    parsedLiteral = Double.parseDouble(raw.trim());
                } catch (NumberFormatException ignored) {
                    parsedFormula = raw;
                }
            } else {
                parsedFormula = raw;
            }
        }
        this.formula = parsedFormula;
        this.literal = parsedLiteral;
    }

    public double evaluate(Player player) {
        if (this.literal != null) return this.literal;
        if (this.formula == null) return 0.0;
        String resolved = ZShopPlaceholders.setPlaceholders(player, this.formula);
        if (resolved == null) return 0.0;
        try {
            return Double.parseDouble(resolved.trim());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    public boolean isPositive() {
        if (this.literal != null) return this.literal > 0;
        return this.formula != null && !this.formula.isEmpty();
    }
}