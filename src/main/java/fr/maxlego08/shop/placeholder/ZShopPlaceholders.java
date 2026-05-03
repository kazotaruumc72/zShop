package fr.maxlego08.shop.placeholder;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.level.PlayerLevel;
import fr.maxlego08.shop.level.PlayerShopLevel2;
import fr.maxlego08.shop.level.ShopLevel2Config;
import fr.maxlego08.shop.level.ShopLevel2Manager;
import fr.maxlego08.shop.level.ZLevelManager;
import fr.maxlego08.shop.zcore.logger.Logger;
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
 *   <li>{@code %zshop_level2%} – current rarity level</li>
 *   <li>{@code %zshop_level2_max%} – maximum rarity level configured in {@code shop_levels.yml}</li>
 *   <li>{@code %zshop_level2_exp%} – cumulated rarity experience</li>
 *   <li>{@code %zshop_level2_progress_required%} – exp required to reach the next rarity level</li>
 *   <li>{@code %zshop_level2_progress_remaining%} – exp accumulated within the current rarity bracket</li>
 *   <li>{@code %zshop_level2_progress_percent%} – progression toward the next rarity level (0–100)</li>
 *   <li>{@code %zshop_level2_top_name_<rank>%} – player name at the rarity leaderboard rank</li>
 *   <li>{@code %zshop_level2_top_level_<rank>%} – rarity level at leaderboard rank</li>
 *   <li>{@code %zshop_level2_top_exp_<rank>%} – total rarity exp at leaderboard rank</li>
 * </ul>
 * <p>
 * Resolution is driven by two registries: {@link #exactResolvers} for fixed
 * placeholder names and {@link #prefixResolvers} for placeholders that carry a
 * dynamic suffix (e.g. the leaderboard rank). Adding or removing a placeholder
 * is a single line in {@link #registerDefaults()}.
 * <p>
 * The class also exposes a static {@link #setPlaceholders(Player, String)} /
 * {@link #setPlaceholders(Player, List)} facade that delegates to PlaceholderAPI
 * when it is installed on the server, or falls back to a local resolver
 * otherwise so configurations keep working without PAPI.
 */
public class ZShopPlaceholders extends PlaceholderExpansion {

    public static final String IDENTIFIER = "zshop";

    /**
     * When {@code true}, every placeholder resolution call is logged to the
     * console with the requesting player, the requested parameters and the
     * computed value. Toggle with {@link #setDebug(boolean)} or via the
     * {@code /zshoplugin debug} command.
     */
    private static volatile boolean debug = false;

    private static final String DEFAULT_NUMERIC = "0";
    private static final String EMPTY_TOP_PLACEHOLDER = "-";
    private static final Pattern LOCAL_PATTERN = Pattern.compile("%([^%]+)%");

    private static volatile ZShopPlaceholders instance;
    private static volatile boolean papiAvailable;

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static boolean isDebug() {
        return debug;
    }

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
        // Direct stdout so the message is visible even if the custom Logger
        // is not set up yet, or if console formatting strips it.
        System.out.println("[zShop][PAPI-INIT] hasPlaceholderApi=" + hasPlaceholderApi
                + " jarBuildSentinel=BUILD-2 plugin=" + plugin.getDescription().getFullName());

        instance = new ZShopPlaceholders(plugin);
        papiAvailable = hasPlaceholderApi;

        if (!hasPlaceholderApi) {
            System.out.println("[zShop][PAPI-INIT] PlaceholderAPI not detected, aborting registration.");
            Logger.info("PlaceholderAPI not detected. zShop placeholders (%zshop_*%) will only be resolved inside zShop's own configs.",
                    Logger.LogType.WARNING);
            return;
        }

        // PlaceholderAPI silently refuses to register an expansion when one
        // with the same identifier is already registered (e.g. the legacy
        // "zshop" expansion downloaded from the eCloud). Forcefully unregister
        // any pre-existing expansion so our in-plugin one always wins.
        try {
            PlaceholderExpansion existing = PlaceholderAPIPlugin.getInstance()
                    .getLocalExpansionManager()
                    .getExpansion(IDENTIFIER);
            if (existing != null && existing != instance) {
                System.out.println("[zShop][PAPI-INIT] Found pre-existing expansion '" + IDENTIFIER
                        + "' class=" + existing.getClass().getName()
                        + " author=" + existing.getAuthor()
                        + " version=" + existing.getVersion()
                        + " - unregistering it.");
                Logger.info("Unregistering a pre-existing PlaceholderAPI expansion '" + IDENTIFIER
                        + "' (class " + existing.getClass().getName() + ") so zShop's built-in one can take over. "
                        + "If this comes from plugins/PlaceholderAPI/expansions/, delete that JAR to avoid the conflict.",
                        Logger.LogType.WARNING);
                existing.unregister();
            } else {
                System.out.println("[zShop][PAPI-INIT] No pre-existing expansion '" + IDENTIFIER + "' detected.");
            }
        } catch (LinkageError | Exception ex) {
            System.out.println("[zShop][PAPI-INIT] Could not query LocalExpansionManager: " + ex);
            // Older PAPI versions may not expose getLocalExpansionManager;
            // the register() call below will simply be a no-op if PAPI rejects it.
        }

        boolean registered = instance.register();
        System.out.println("[zShop][PAPI-INIT] register() returned " + registered
                + " (exact=" + instance.exactResolvers.size()
                + ", prefix=" + instance.prefixResolvers.size() + ")");
        if (registered) {
            Logger.info("PlaceholderAPI expansion '" + IDENTIFIER + "' registered ("
                    + instance.exactResolvers.size() + " exact + " + instance.prefixResolvers.size()
                    + " prefix placeholders). Test with: /papi parse <player> %zshop_level_max%",
                    Logger.LogType.SUCCESS);
        } else {
            Logger.info("Failed to register the PlaceholderAPI expansion '" + IDENTIFIER
                    + "'. Another plugin/expansion is probably holding the identifier; "
                    + "remove plugins/PlaceholderAPI/expansions/Expansion-zShop.jar (if present) and run /papi reload.",
                    Logger.LogType.ERROR);
        }

        // PAPI may load *.jar expansions from plugins/PlaceholderAPI/expansions/
        // *after* zShop's onEnable, which silently overwrites our in-plugin
        // expansion with the legacy eCloud one (e.g. version 1.0.2 by
        // Maxlego08). Re-check periodically and reclaim the identifier so
        // %zshop_*% placeholders always go through the up-to-date code.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> reclaimIfHijacked(), 40L, 40L);
    }

    /**
     * Re-register zShop's placeholder expansion if PlaceholderAPI currently
     * holds another instance under the {@code zshop} identifier (typically a
     * legacy eCloud {@code Expansion-zShop.jar} loaded after our onEnable).
     */
    private static void reclaimIfHijacked() {
        if (instance == null || !papiAvailable) return;
        try {
            PlaceholderExpansion current = PlaceholderAPIPlugin.getInstance()
                    .getLocalExpansionManager()
                    .getExpansion(IDENTIFIER);
            if (current == instance) return;
            System.out.println("[zShop][PAPI-RECLAIM] Detected another expansion holding '" + IDENTIFIER
                    + "' (class=" + (current == null ? "null" : current.getClass().getName())
                    + ", version=" + (current == null ? "?" : current.getVersion())
                    + "). Reclaiming the identifier.");
            if (current != null) current.unregister();
            boolean ok = instance.register();
            System.out.println("[zShop][PAPI-RECLAIM] re-register() returned " + ok);
        } catch (LinkageError | Exception ex) {
            System.out.println("[zShop][PAPI-RECLAIM] Could not reclaim: " + ex);
        }
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
                ? "Maxlego08, Kazotaruu_"
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
        String result = resolve(player, params);
        if (debug) {
            System.out.println("[zShop][PAPI-DEBUG] onPlaceholderRequest player="
                    + (player == null ? "null" : player.getName())
                    + " params='" + params + "' -> "
                    + (result == null ? "<null/unhandled>" : "'" + result + "'"));
        }
        return result;
    }

    /**
     * Some PlaceholderAPI lookups (e.g. when another plugin caches a value for
     * an offline target, or when the top placeholders are read for an empty
     * context) provide only an {@link OfflinePlayer}. Resolve those too so
     * leaderboard placeholders work even when the player on screen is offline.
     */
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Player online = player == null ? null : player.getPlayer();
        String result = (online != null) ? resolve(online, params) : resolveOffline(player, params);
        if (debug) {
            System.out.println("[zShop][PAPI-DEBUG] onRequest player="
                    + (player == null ? "null" : player.getName() + "/" + player.getUniqueId())
                    + " online=" + (online != null)
                    + " params='" + params + "' -> "
                    + (result == null ? "<null/unhandled>" : "'" + result + "'"));
        }
        return result;
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

        LevelContext ctx = new LevelContext(player, player == null ? null : player.getUniqueId(), manager);
        return resolveWith(ctx, params);
    }

    /**
     * Offline counterpart of {@link #resolve(Player, String)}. The {@link Player}
     * accessor in the context is null but the {@link PlayerLevel} can still be
     * fetched through the player's UUID, so per-player placeholders
     * ({@code %zshop_level%}, {@code %zshop_level_progress%}, …) keep working
     * for offline targets.
     */
    private String resolveOffline(OfflinePlayer player, String params) {
        if (params == null) return null;
        ZLevelManager manager = this.plugin.getLevelManager();
        if (manager == null) return null;

        LevelContext ctx = new LevelContext(null, player == null ? null : player.getUniqueId(), manager);
        return resolveWith(ctx, params);
    }

    private String resolveWith(LevelContext ctx, String params) {
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
            if (ctx.uuid() == null) return String.valueOf(ctx.manager().getConfig().getMinLevel());
            return String.valueOf(ctx.playerLevel().getLevel());
        });

        // %zshop_level_max%
        registerExact("level_max", (ctx, arg) -> String.valueOf(ctx.manager().getConfig().getMaxLevel()));

        // %zshop_level_bonus%
        registerExact("level_bonus", (ctx, arg) -> {
            if (ctx.uuid() == null) return DEFAULT_NUMERIC;
            double bonus = ctx.manager().getConfig().getBonusPercent(ctx.playerLevel().getLevel());
            return ZLevelManager.formatBonus(bonus);
        });

        // %zshop_level_progress%
        registerExact("level_progress", (ctx, arg) -> {
            if (ctx.uuid() == null) return DEFAULT_NUMERIC;
            return String.valueOf(ctx.playerLevel().getTotalItems());
        });

        // %zshop_level_progress_required%
        registerExact("level_progress_required", (ctx, arg) -> {
            if (ctx.uuid() == null) return DEFAULT_NUMERIC;
            PlayerLevel pl = ctx.playerLevel();
            return String.valueOf(ctx.manager().getConfig()
                    .getItemsForNextLevel(pl.getLevel())
                    .orElse(pl.getTotalItems()));
        });

        // %zshop_level_progress_remaining%
        // Items already accumulated within the current level bracket (counts
        // up from 0 toward %zshop_level_progress_required%).
        // Formula: totalItems - itemsRequiredForCurrentLevel (clamped to 0).
        // Example: progression has '2:5000', the player has totalItems=30 at
        //          level 1 (level 1 threshold = 0) -> remaining = 30 - 0 = 30.
        //          At level 2 (threshold = 5000) with totalItems=7500
        //          -> remaining = 7500 - 5000 = 2500.
        registerExact("level_progress_remaining", (ctx, arg) -> {
            if (ctx.uuid() == null) return DEFAULT_NUMERIC;
            PlayerLevel pl = ctx.playerLevel();
            long currentLevelThreshold = ctx.manager().getConfig().getItemsForLevel(pl.getLevel());
            long progressInLevel = pl.getTotalItems() - currentLevelThreshold;
            return String.valueOf(Math.max(0L, progressInLevel));
        });

        // %zshop_level_progress_percent%
        registerExact("level_progress_percent", (ctx, arg) -> {
            if (ctx.uuid() == null) return DEFAULT_NUMERIC;
            return String.valueOf(ctx.manager().getProgressPercent(ctx.playerLevel()));
        });

        // %zshop_level_top_name_<rank>%
        // Returns "-" when there is no player at the requested rank yet.
        registerPrefix("level_top_name_", (ctx, rank) -> {
            PlayerLevel top = parseTop(ctx.manager(), rank);
            if (top == null) return EMPTY_TOP_PLACEHOLDER;
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(top.getUniqueId());
            String name = offlinePlayer.getName();
            return name != null ? name : top.getUniqueId().toString();
        });

        // %zshop_level_top_level_<rank>%
        registerPrefix("level_top_level_", (ctx, rank) -> {
            PlayerLevel top = parseTop(ctx.manager(), rank);
            return top == null ? EMPTY_TOP_PLACEHOLDER : String.valueOf(top.getLevel());
        });

        // %zshop_level_top_items_<rank>%
        registerPrefix("level_top_items_", (ctx, rank) -> {
            PlayerLevel top = parseTop(ctx.manager(), rank);
            return top == null ? EMPTY_TOP_PLACEHOLDER : String.valueOf(top.getTotalItems());
        });

        // ----- Second (rarity) level system -----
        // Prefix resolvers must come before any future "level2_" exact-key
        // collision; the registry walks prefixes first, but we still register
        // the more specific keys first to mirror the level1 ordering.

        // %zshop_level2_top_name_<rank>%
        registerPrefix("level2_top_name_", (ctx, rank) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null) return EMPTY_TOP_PLACEHOLDER;
            PlayerShopLevel2 top = parseTop2(m, rank);
            if (top == null) return EMPTY_TOP_PLACEHOLDER;
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(top.getUniqueId());
            String name = offlinePlayer.getName();
            return name != null ? name : top.getUniqueId().toString();
        });

        // %zshop_level2_top_level_<rank>%
        registerPrefix("level2_top_level_", (ctx, rank) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null) return EMPTY_TOP_PLACEHOLDER;
            PlayerShopLevel2 top = parseTop2(m, rank);
            return top == null ? EMPTY_TOP_PLACEHOLDER : String.valueOf(top.getLevel());
        });

        // %zshop_level2_top_exp_<rank>%
        registerPrefix("level2_top_exp_", (ctx, rank) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null) return EMPTY_TOP_PLACEHOLDER;
            PlayerShopLevel2 top = parseTop2(m, rank);
            return top == null ? EMPTY_TOP_PLACEHOLDER : String.valueOf(top.getTotalExp());
        });

        // %zshop_level2%
        registerExact("level2", (ctx, arg) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null) return DEFAULT_NUMERIC;
            if (ctx.uuid() == null) return String.valueOf(m.getConfig().getMinLevel());
            return String.valueOf(m.getOrCreate(ctx.uuid()).getLevel());
        });

        // %zshop_level2_max%
        registerExact("level2_max", (ctx, arg) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null) return DEFAULT_NUMERIC;
            return String.valueOf(m.getConfig().getMaxLevel());
        });

        // %zshop_level2_exp%
        registerExact("level2_exp", (ctx, arg) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null || ctx.uuid() == null) return DEFAULT_NUMERIC;
            return String.valueOf(m.getOrCreate(ctx.uuid()).getTotalExp());
        });

        // %zshop_level2_progress_required%
        // Cumulated exp required to reach the *next* rarity level. Falls back
        // to the player's current totalExp when they are at the maximum level
        // so the value can still be rendered as a denominator.
        registerExact("level2_progress_required", (ctx, arg) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null || ctx.uuid() == null) return DEFAULT_NUMERIC;
            PlayerShopLevel2 pl = m.getOrCreate(ctx.uuid());
            return String.valueOf(m.getConfig().getExpForNextLevel(pl.getLevel())
                    .orElse(pl.getTotalExp()));
        });

        // %zshop_level2_progress_remaining%
        // Exp already accumulated within the current level bracket
        // (totalExp - threshold of current level, clamped to 0). Mirrors the
        // level1 placeholder's semantics for consistency.
        registerExact("level2_progress_remaining", (ctx, arg) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null || ctx.uuid() == null) return DEFAULT_NUMERIC;
            PlayerShopLevel2 pl = m.getOrCreate(ctx.uuid());
            ShopLevel2Config cfg = m.getConfig();
            long currentLevelThreshold = cfg.getExpForLevel(pl.getLevel());
            long progressInLevel = pl.getTotalExp() - currentLevelThreshold;
            return String.valueOf(Math.max(0L, progressInLevel));
        });

        // %zshop_level2_progress_percent%
        registerExact("level2_progress_percent", (ctx, arg) -> {
            ShopLevel2Manager m = level2Manager();
            if (m == null || ctx.uuid() == null) return DEFAULT_NUMERIC;
            return String.valueOf(m.getProgressPercent(m.getOrCreate(ctx.uuid())));
        });
    }

    private ShopLevel2Manager level2Manager() {
        return this.plugin.getLevel2Manager();
    }

    private static PlayerShopLevel2 parseTop2(ShopLevel2Manager manager, String rank) {
        if (rank == null || rank.isEmpty()) return null;
        try {
            return manager.getTop(Integer.parseInt(rank.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
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
    private record LevelContext(Player player, java.util.UUID uuid, ZLevelManager manager) {

        PlayerLevel playerLevel() {
            return manager.getOrCreate(uuid);
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
