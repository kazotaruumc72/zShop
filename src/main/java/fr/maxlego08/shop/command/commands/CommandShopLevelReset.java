package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.ZLevelManager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class CommandShopLevelReset extends VCommand {

    public CommandShopLevelReset(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL_RESET);
        this.addSubCommand("reset");
        this.setDescription(Message.DESCRIPTION_LEVEL_RESET);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        ZLevelManager levelManager = plugin.getLevelManager();
        String name = this.argAsString(0);

        OfflinePlayer target = this.argAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message(plugin, sender, Message.LEVEL_PLAYER_NOT_FOUND, "%player%", name);
            return CommandType.SUCCESS;
        }

        levelManager.reset(target.getUniqueId());
        message(plugin, sender, Message.LEVEL_RESET_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name);
        return CommandType.SUCCESS;
    }
}
