package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.PlayerShopLevel2;
import fr.maxlego08.shop.level.ShopLevel2Config;
import fr.maxlego08.shop.level.ShopLevel2Manager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.stream.Collectors;

public class CommandShopLevel2Get extends VCommand {

    public CommandShopLevel2Get(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL2_GET);
        this.addSubCommand("get");
        this.setDescription(Message.DESCRIPTION_LEVEL2_GET);
        this.addOptionalArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        ShopLevel2Manager manager = plugin.getLevel2Manager();
        ShopLevel2Config config = manager.getConfig();
        String name = this.argAsString(0);

        if (name == null) {
            if (this.player == null) {
                return CommandType.SYNTAX_ERROR;
            }
            sendInfo(plugin, manager, config, manager.getOrCreate(this.player.getUniqueId()), null);
            return CommandType.SUCCESS;
        }

        OfflinePlayer target = this.argAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message(plugin, sender, Message.LEVEL2_PLAYER_NOT_FOUND, "%player%", name);
            return CommandType.SUCCESS;
        }

        sendInfo(plugin, manager, config, manager.getOrCreate(target.getUniqueId()),
                target.getName() != null ? target.getName() : name);
        return CommandType.SUCCESS;
    }

    private void sendInfo(ShopPlugin plugin, ShopLevel2Manager manager, ShopLevel2Config config,
                          PlayerShopLevel2 playerLevel, String targetName) {
        Optional<Long> nextOpt = config.getExpForNextLevel(playerLevel.getLevel());
        long next = nextOpt.orElse(playerLevel.getTotalExp());
        int percent = manager.getProgressPercent(playerLevel);
        if (targetName == null) {
            message(plugin, sender, Message.LEVEL2_GET_SELF,
                    "%level%", String.valueOf(playerLevel.getLevel()),
                    "%max%", String.valueOf(config.getMaxLevel()),
                    "%exp%", String.valueOf(playerLevel.getTotalExp()),
                    "%next%", String.valueOf(next),
                    "%percent%", String.valueOf(percent));
        } else {
            message(plugin, sender, Message.LEVEL2_GET_OTHER,
                    "%player%", targetName,
                    "%level%", String.valueOf(playerLevel.getLevel()),
                    "%max%", String.valueOf(config.getMaxLevel()),
                    "%exp%", String.valueOf(playerLevel.getTotalExp()),
                    "%next%", String.valueOf(next),
                    "%percent%", String.valueOf(percent));
        }
    }
}