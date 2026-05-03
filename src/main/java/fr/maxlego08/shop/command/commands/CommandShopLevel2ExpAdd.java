package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.PlayerShopLevel2;
import fr.maxlego08.shop.level.ShopLevel2Manager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

public class CommandShopLevel2ExpAdd extends VCommand {

    public CommandShopLevel2ExpAdd(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL2_EXP_ADD);
        this.addSubCommand("expadd");
        this.setDescription(Message.DESCRIPTION_LEVEL2_EXP_ADD);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
        this.addRequireArg("amount", (sender, args) -> Arrays.asList("100", "1000", "5000", "25000"));
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

        long amount;
        try {
            amount = Long.parseLong(this.argAsString(1));
        } catch (NumberFormatException ex) {
            message(plugin, sender, Message.LEVEL2_INVALID,
                    "%min%", "1",
                    "%max%", String.valueOf(Long.MAX_VALUE));
            return CommandType.SUCCESS;
        }

        if (amount <= 0L) {
            message(plugin, sender, Message.LEVEL2_INVALID,
                    "%min%", "1",
                    "%max%", String.valueOf(Long.MAX_VALUE));
            return CommandType.SUCCESS;
        }

        PlayerShopLevel2 playerLevel = manager.addExp(target.getUniqueId(), amount);
        message(plugin, sender, Message.LEVEL2_EXP_ADD_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name,
                "%amount%", String.valueOf(amount),
                "%total%", String.valueOf(playerLevel.getTotalExp()),
                "%level%", String.valueOf(playerLevel.getLevel()));
        return CommandType.SUCCESS;
    }
}