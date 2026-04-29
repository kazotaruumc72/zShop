package fr.maxlego08.shop.placeholder;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.level.PlayerLevel;
import fr.maxlego08.shop.level.ZLevelManager;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single PlaceholderAPI integration class for zShop.
 * <p>
 * Exposes only the level placeholders documented in the project README:
 * <ul>
 *   <li>{@code %zshop_level%} – current level of the player</li>
 *   <li>{@code %zshop_level_max%} – maximum level configured in {@code levels.yml}</li>
 *   <li>{@code %zshop_level_bonus%} – bonus percent at the current level</li>
 *   <li>{@code %zshop_level_progress%} – cumulative bought/sold items</li>
 *   <li>{@code %zshop_level_progress_required%} – cumulative items required for the next level</li>
 *   <li>{@code %zshop_level_progress_remaining%} – items remaining to reach the next level</li>
 *   <li>{@code %zshop_level_progress_percent%} – progression toward the next level (0–100)</li>
 *   <li>{@code %zshop_level_top_name_<rank>%} – player name at leaderboard rank</li>
 *   <li>{@code %zshop_level_top_level_<rank>%} – level at leaderboard rank</li>
 *   <li>{@code %zshop_level_top_items_<rank>%} – total items at leaderboard rank</li>
 * </ul>
 * <p>
 * The class also exposes a static {@link #setPlaceholders(Player, String)} /
 * {@link #setPlaceholders(Player, List)} facade that delegates to PlaceholderAPI
 * when it is installed on the server, or falls back to a local resolver
 * otherwise.
 */
public class ZShopPlaceholders extends PlaceholderExpansion {

    public static final String IDENTIFIER = "zshop";

    private static final Pattern LOCAL_PATTERN = Pattern.compile("%([^%]+)%");

    private static volatile ZShopPlaceholders instance;
    private static volatile boolean papiAvailable;

    private final ShopPlugin plugin;

    private ZShopPlaceholders(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the placeholder integration. Called once during plugin
     * enable. When PlaceholderAPI is installed the expansion is registered;
     * otherwise the static facade falls back to local resolution.
     *
     * @param plugin     the zShop plugin instance
     * @param hasPlaceholderApi {@code true} if PlaceholderAPI is enabled on the server
     */
    public static void initialize(ShopPlugin plugin, boolean hasPlaceholderApi) {
        instance = new ZShopPlaceholders(plugin);
        papiAvailable = hasPlaceholderApi;

        if (!hasPlaceholderApi) return;

        // PlaceholderAPI silently refuses to register an expansion when one
        // with the same identifier is already registered (e.g. the legacy
        // "zshop" expansion downloaded from the eCloud). Forcefully unregister
        // any pre-existing expansion so our in-plugin one always wins.
        try {
            PlaceholderExpansion existing = PlaceholderAPIPlugin.getInstance()
                    .getLocalExpansionManager()
                    .getExpansion(IDENTIFIER);
            if (existing != null) existing.unregister();
        } catch (LinkageError | Exception ignored) {
            // Older PAPI versions may not expose getLocalExpansionManager;
            // the register() call below will simply be a no-op if PAPI rejects it.
        }

        instance.register();
    }

    /**
     * Resolve every {@code %zshop_*%} occurrence inside {@code value}. When
     * PlaceholderAPI is installed the call is delegated to it (so any other
     * registered expansion also gets resolved); otherwise only zShop's own
     * placeholders are replaced.
     */
    public static String setPlaceholders(Player player, String value) {
        if (value == null || value.indexOf('%') < 0) return value;
        if (papiAvailable) return PlaceholderAPI.setPlaceholders(player, value);
        return localReplace(player, value);
    }

    public static List<String> setPlaceholders(Player player, List<String> values) {
        if (values == null) return null;
        if (papiAvailable) return PlaceholderAPI.setPlaceholders(player, values);
        return values.stream().map(line -> localReplace(player, line)).collect(Collectors.toList());
    }

    private static String localReplace(Player player, String value) {
        if (value == null || value.indexOf('%') < 0 || instance == null) return value;
        Matcher matcher = LOCAL_PATTERN.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = null;
            if (token.startsWith(IDENTIFIER + "_")) {
                replacement = instance.resolve(player, token.substring(IDENTIFIER.length() + 1));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group(0)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return this.plugin.getDescription().getAuthors().isEmpty()
                ? "Maxlego08"
                : this.plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return resolve(player, params);
    }

    /**
     * Resolve the placeholder body (everything after {@code zshop_}). Returns
     * {@code null} when the placeholder is not recognised, which lets PAPI
     * fall back to other expansions and lets {@link #localReplace} keep the
     * literal token unchanged.
     */
    private String resolve(Player player, String params) {
        if (params == null) return null;
        ZLevelManager levelManager = this.plugin.getLevelManager();
        if (levelManager == null) return null;

        // Leaderboard placeholders: zshop_level_top_{name|level|items}_<rank>
        if (params.startsWith("level_top_name_")) {
            return resolveTopName(levelManager, params.substring("level_top_name_".length()));
        }
        if (params.startsWith("level_top_level_")) {
            PlayerLevel top = parseTop(levelManager, params.substring("level_top_level_".length()));
            return top == null ? "0" : String.valueOf(top.getLevel());
        }
        if (params.startsWith("level_top_items_")) {
            PlayerLevel top = parseTop(levelManager, params.substring("level_top_items_".length()));
            return top == null ? "0" : String.valueOf(top.getTotalItems());
        }

        // Global level metadata placeholders.
        if (params.equals("level_max")) {
            return String.valueOf(levelManager.getConfig().getMaxLevel());
        }

        // Player-bound placeholders below this point.
        if (params.equals("level")) {
            if (player == null) return String.valueOf(levelManager.getConfig().getMinLevel());
            return String.valueOf(levelManager.getOrCreate(player.getUniqueId()).getLevel());
        }
        if (params.equals("level_bonus")) {
            if (player == null) return "0";
            double bonus = levelManager.getConfig().getBonusPercent(
                    levelManager.getOrCreate(player.getUniqueId()).getLevel());
            return ZLevelManager.formatBonus(bonus);
        }
        if (params.equals("level_progress")) {
            if (player == null) return "0";
            return String.valueOf(levelManager.getOrCreate(player.getUniqueId()).getTotalItems());
        }
        if (params.equals("level_progress_required")) {
            if (player == null) return "0";
            PlayerLevel pl = levelManager.getOrCreate(player.getUniqueId());
            return String.valueOf(levelManager.getConfig()
                    .getItemsForNextLevel(pl.getLevel())
                    .orElse(pl.getTotalItems()));
        }
        if (params.equals("level_progress_remaining")) {
            if (player == null) return "0";
            PlayerLevel pl = levelManager.getOrCreate(player.getUniqueId());
            Optional<Long> next = levelManager.getConfig().getItemsForNextLevel(pl.getLevel());
            if (next.isEmpty()) return "0";
            return String.valueOf(Math.max(0L, next.get() - pl.getTotalItems()));
        }
        if (params.equals("level_progress_percent")) {
            if (player == null) return "0";
            return String.valueOf(levelManager.getProgressPercent(
                    levelManager.getOrCreate(player.getUniqueId())));
        }

        // Unknown placeholder: let PAPI/local resolver leave it untouched.
        return null;
    }

    private String resolveTopName(ZLevelManager levelManager, String rankString) {
        PlayerLevel top = parseTop(levelManager, rankString);
        if (top == null) return "";
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(top.getUniqueId());
        String name = offlinePlayer.getName();
        return name != null ? name : top.getUniqueId().toString();
    }

    private PlayerLevel parseTop(ZLevelManager levelManager, String rankString) {
        if (rankString == null || rankString.isEmpty()) return null;
        try {
            return levelManager.getTop(Integer.parseInt(rankString.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
