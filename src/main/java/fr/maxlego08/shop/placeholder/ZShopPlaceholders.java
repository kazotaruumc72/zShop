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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Resolution is driven by two registries: {@link #exactResolvers} for fixed
 * placeholder names and {@link #prefixResolvers} for placeholders that take an
 * argument suffix (the leaderboard ones). Adding or removing a placeholder is
 * a single line in {@link #registerDefaults()}.
 * <p>
 * The class also exposes a static {@link #setPlaceholders(Player, String)} /
 * {@link #setPlaceholders(Player, List)} facade that delegates to PlaceholderAPI
 * when it is installed on the server, or falls back to a local resolver
 * otherwise so configurations keep working without PAPI.
 */
public class ZShopPlaceholders extends PlaceholderExpansion {

    public static final String IDENTIFIER = "zshop";

    private static final String DEFAULT_NUMERIC = "0";
    private static final Pattern LOCAL_PATTERN = Pattern.compile("%([^%]+)%");

    private static volatile ZShopPlaceholders instance;
    private static volatile boolean papiAvailable;

    private final ShopPlugin plugin;
    private final Map<String, PlaceholderResolver> exactResolvers = new LinkedHashMap<>();
    private final Map<String, PlaceholderResolver> prefixResolvers = new LinkedHashMap<>();

    private ZShopPlaceholders(ShopPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    /**
     * Initialize the placeholder integration. Called once during plugin
     * enable. When PlaceholderAPI is installed the expansion is registered;
     * otherwise the static facade falls back to local resolution.
     *
     * @param plugin            the zShop plugin instance
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
     * Resolve every placeholder occurrence inside {@code value}. When
     * PlaceholderAPI is installed the call is delegated to it (so any other
     * registered expansion also gets resolved); otherwise only zShop's own
     * placeholders are replaced and other tokens are left untouched.
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
        ZLevelManager manager = this.plugin.getLevelManager();
        if (manager == null) return null;

        LevelContext ctx = new LevelContext(player, manager);

        // Prefix resolvers run first because their keys are more specific
        // (e.g. "level_top_name_" subsumes any future "level_top" prefix).
        for (Map.Entry<String, PlaceholderResolver> entry : this.prefixResolvers.entrySet()) {
            String prefix = entry.getKey();
            if (params.startsWith(prefix)) {
                return entry.getValue().resolve(ctx, params.substring(prefix.length()));
            }
        }

        PlaceholderResolver exact = this.exactResolvers.get(params);
        return exact == null ? null : exact.resolve(ctx, "");
    }

    /**
     * Wire the placeholder registry. The order in which prefix entries are
     * added is preserved (the registry is a {@link LinkedHashMap}); when
     * adding new prefix placeholders, register the most specific ones first.
     */
    private void registerDefaults() {
        // %zshop_level%
        registerExact("level", (ctx, arg) -> {
            if (ctx.player() == null) return String.valueOf(ctx.manager().getConfig().getMinLevel());
            return String.valueOf(ctx.playerLevel().getLevel());
        });

        // %zshop_level_max%
        registerExact("level_max", (ctx, arg) -> String.valueOf(ctx.manager().getConfig().getMaxLevel()));

        // %zshop_level_bonus%
        registerExact("level_bonus", (ctx, arg) -> {
            if (ctx.player() == null) return DEFAULT_NUMERIC;
            double bonus = ctx.manager().getConfig().getBonusPercent(ctx.playerLevel().getLevel());
            return ZLevelManager.formatBonus(bonus);
        });

        // %zshop_level_progress%
        registerExact("level_progress", (ctx, arg) -> {
            if (ctx.player() == null) return DEFAULT_NUMERIC;
            return String.valueOf(ctx.playerLevel().getTotalItems());
        });

        // %zshop_level_progress_required%
        registerExact("level_progress_required", (ctx, arg) -> {
            if (ctx.player() == null) return DEFAULT_NUMERIC;
            PlayerLevel pl = ctx.playerLevel();
            return String.valueOf(ctx.manager().getConfig()
                    .getItemsForNextLevel(pl.getLevel())
                    .orElse(pl.getTotalItems()));
        });

        // %zshop_level_progress_remaining%
        registerExact("level_progress_remaining", (ctx, arg) -> {
            if (ctx.player() == null) return DEFAULT_NUMERIC;
            PlayerLevel pl = ctx.playerLevel();
            Optional<Long> next = ctx.manager().getConfig().getItemsForNextLevel(pl.getLevel());
            if (next.isEmpty()) return DEFAULT_NUMERIC;
            return String.valueOf(Math.max(0L, next.get() - pl.getTotalItems()));
        });

        // %zshop_level_progress_percent%
        registerExact("level_progress_percent", (ctx, arg) -> {
            if (ctx.player() == null) return DEFAULT_NUMERIC;
            return String.valueOf(ctx.manager().getProgressPercent(ctx.playerLevel()));
        });

        // %zshop_level_top_name_<rank>%
        registerPrefix("level_top_name_", (ctx, rank) -> {
            PlayerLevel top = parseTop(ctx.manager(), rank);
            if (top == null) return "";
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(top.getUniqueId());
            String name = offlinePlayer.getName();
            return name != null ? name : top.getUniqueId().toString();
        });

        // %zshop_level_top_level_<rank>%
        registerPrefix("level_top_level_", (ctx, rank) -> {
            PlayerLevel top = parseTop(ctx.manager(), rank);
            return top == null ? DEFAULT_NUMERIC : String.valueOf(top.getLevel());
        });

        // %zshop_level_top_items_<rank>%
        registerPrefix("level_top_items_", (ctx, rank) -> {
            PlayerLevel top = parseTop(ctx.manager(), rank);
            return top == null ? DEFAULT_NUMERIC : String.valueOf(top.getTotalItems());
        });
    }

    private void registerExact(String name, PlaceholderResolver resolver) {
        this.exactResolvers.put(name, resolver);
    }

    private void registerPrefix(String prefix, PlaceholderResolver resolver) {
        this.prefixResolvers.put(prefix, resolver);
    }

    private static PlayerLevel parseTop(ZLevelManager manager, String rank) {
        if (rank == null || rank.isEmpty()) return null;
        try {
            return manager.getTop(Integer.parseInt(rank.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Resolution context passed to every {@link PlaceholderResolver}. The
     * {@link #playerLevel()} accessor lazily fetches (or creates) the
     * {@link PlayerLevel} entry for the requesting player; callers must check
     * {@link #player()} for nullity before using it because PAPI calls
     * placeholders for offline contexts as well.
     */
    private record LevelContext(Player player, ZLevelManager manager) {

        PlayerLevel playerLevel() {
            return manager.getOrCreate(player.getUniqueId());
        }
    }

    @FunctionalInterface
    private interface PlaceholderResolver {
        /**
         * @param ctx      the resolution context
         * @param argument the placeholder argument for prefix resolvers
         *                 (e.g. the rank in {@code level_top_name_<rank>}),
         *                 or an empty string for exact resolvers
         * @return the placeholder value, or {@code null} to leave the token
         *         unchanged
         */
        String resolve(LevelContext ctx, String argument);
    }
}
