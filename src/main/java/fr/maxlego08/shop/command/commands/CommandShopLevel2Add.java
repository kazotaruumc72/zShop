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

import java.util.Arrays;
import java.util.stream.Collectors;

public class CommandShopLevel2Add extends VCommand {

    public CommandShopLevel2Add(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL2_ADD);
        this.addSubCommand("add");
        this.setDescription(Message.DESCRIPTION_LEVEL2_ADD);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
        this.addRequireArg("amount", (sender, args) -> Arrays.asList("1", "2", "3", "5"));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        ShopLevel2Manager manager = plugin.getLevel2Manager();
        ShopLevel2Config config = manager.getConfig();
        String name = this.argAsString(0);

        OfflinePlayer target = this.argAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message(plugin, sender, Message.LEVEL2_PLAYER_NOT_FOUND, "%player%", name);
            return CommandType.SUCCESS;
        }

        int amount;
        try {
            amount = this.argAsInteger(1);
        } catch (NumberFormatException ex) {
            message(plugin, sender, Message.LEVEL2_INVALID,
                    "%min%", String.valueOf(config.getMinLevel()),
                    "%max%", String.valueOf(config.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        if (amount <= 0) {
            message(plugin, sender, Message.LEVEL2_INVALID,
                    "%min%", String.valueOf(config.getMinLevel()),
                    "%max%", String.valueOf(config.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        PlayerShopLevel2 playerLevel = manager.getOrCreate(target.getUniqueId());
        int target_level = playerLevel.getLevel() + amount;
        playerLevel = manager.setLevel(target.getUniqueId(), target_level);

        message(plugin, sender, Message.LEVEL2_ADD_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name,
                "%amount%", String.valueOf(amount),
                "%level%", String.valueOf(playerLevel.getLevel()));
        return CommandType.SUCCESS;
    }
}