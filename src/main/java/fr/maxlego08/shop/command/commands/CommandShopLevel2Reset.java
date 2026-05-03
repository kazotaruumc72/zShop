package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.ShopLevel2Manager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class CommandShopLevel2Reset extends VCommand {

    public CommandShopLevel2Reset(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL2_RESET);
        this.addSubCommand("reset");
        this.setDescription(Message.DESCRIPTION_LEVEL2_RESET);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        ShopLevel2Manager manager = plugin.getLevel2Manager();
        String name = this.argAsString(0);

        OfflinePlayer target = this.argAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message(plugin, sender, Message.LEVEL2_PLAYER_NOT_FOUND, "%player%", name);
            return CommandType.SUCCESS;
        }

        manager.reset(target.getUniqueId());
        message(plugin, sender, Message.LEVEL2_RESET_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name);
        return CommandType.SUCCESS;
    }
}