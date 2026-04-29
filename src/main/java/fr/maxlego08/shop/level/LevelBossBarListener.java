package fr.maxlego08.shop.level;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up per-player {@link LevelBossBarManager} state when a player
 * disconnects so we never leak BossBar references.
 */
public class LevelBossBarListener implements Listener {

    private final LevelBossBarManager manager;

    public LevelBossBarListener(LevelBossBarManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.manager.remove(event.getPlayer().getUniqueId());
    }
}

