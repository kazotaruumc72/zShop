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

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CommandShopLevel2Set extends VCommand {

    public CommandShopLevel2Set(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL2_SET);
        this.addSubCommand("set");
        this.setDescription(Message.DESCRIPTION_LEVEL2_SET);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
        this.addRequireArg("level", (sender, args) -> {
            ShopLevel2Config config = plugin.getLevel2Manager().getConfig();
            return IntStream.rangeClosed(config.getMinLevel(), config.getMaxLevel())
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toList());
        });
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

        int level;
        try {
            level = this.argAsInteger(1);
        } catch (NumberFormatException ex) {
            message(plugin, sender, Message.LEVEL2_INVALID,
                    "%min%", String.valueOf(config.getMinLevel()),
                    "%max%", String.valueOf(config.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        if (level < config.getMinLevel() || level > config.getMaxLevel()) {
            message(plugin, sender, Message.LEVEL2_INVALID,
                    "%min%", String.valueOf(config.getMinLevel()),
                    "%max%", String.valueOf(config.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        PlayerShopLevel2 playerLevel = manager.setLevel(target.getUniqueId(), level);
        message(plugin, sender, Message.LEVEL2_SET_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name,
                "%level%", String.valueOf(playerLevel.getLevel()));
        return CommandType.SUCCESS;
    }
}