package fr.maxlego08.shop.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public interface Placeholder {

    static Placeholder getPlaceholder() {
        if (PlaceHolders.PLACEHOLDER != null) return PlaceHolders.PLACEHOLDER;
        return PlaceHolders.PLACEHOLDER = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? new Api() : new Local());
    }

    String setPlaceholders(Player player, String string);

    List<String> setPlaceholders(Player player, List<String> list);

    class PlaceHolders {
        static Placeholder PLACEHOLDER = null;
    }

    class Api implements Placeholder {

        public Api() {
            String identifier = LocalPlaceholder.getInstance().getPrefix();
            // PlaceholderAPI silently refuses to register an expansion when
            // another one with the same identifier is already registered. This
            // happens when the eCloud auto-downloaded the legacy "zshop" v1.0.2
            // expansion before this plugin enabled. Forcefully unregister any
            // existing expansion with our identifier so our in-plugin one
            // (which is in sync with the current zShop API) always wins.
            try {
                PlaceholderExpansion existing = PlaceholderAPIPlugin.getInstance()
                        .getLocalExpansionManager()
                        .getExpansion(identifier);
                if (existing != null) {
                    existing.unregister();
                }
            } catch (LinkageError | Exception ignored) {
                // Older PAPI versions may not expose getLocalExpansionManager.
                // The pinned PAPI (2.11.x) always provides it, so there is
                // nothing else to try here; the register() call below will
                // simply be a no-op if registration is rejected.
            }

            PlaceholderExpansion expansion = new DistantPlaceholder(LocalPlaceholder.getInstance());
            expansion.register();
        }

        @Override
        public String setPlaceholders(Player player, String string) {
            return PlaceholderAPI.setPlaceholders(player, string);
        }

        @Override
        public List<String> setPlaceholders(Player player, List<String> list) {
            return PlaceholderAPI.setPlaceholders(player, list);
        }

    }

    class Local implements Placeholder {

        @Override
        public String setPlaceholders(Player player, String string) {
            return LocalPlaceholder.getInstance().setPlaceholders(player, string);
        }

        @Override
        public List<String> setPlaceholders(Player player, List<String> list) {
            return LocalPlaceholder.getInstance().setPlaceholders(player, list);
        }

    }

}
