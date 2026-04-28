package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.LevelConfig;
import fr.maxlego08.shop.level.PlayerLevel;
import fr.maxlego08.shop.level.ZLevelManager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.OfflinePlayer;

public class CommandShopLevelSet extends VCommand {

    public CommandShopLevelSet(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL_SET);
        this.addSubCommand("set");
        this.setDescription(Message.DESCRIPTION_LEVEL_SET);
        this.addRequireArg("player");
        this.addRequireArg("level");
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        ZLevelManager levelManager = plugin.getLevelManager();
        LevelConfig levelConfig = levelManager.getConfig();
        String name = this.argAsString(0);

        OfflinePlayer target = this.argAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message(plugin, sender, Message.LEVEL_PLAYER_NOT_FOUND, "%player%", name);
            return CommandType.SUCCESS;
        }

        int level;
        try {
            level = this.argAsInteger(1);
        } catch (NumberFormatException ex) {
            message(plugin, sender, Message.LEVEL_INVALID,
                    "%min%", String.valueOf(levelConfig.getMinLevel()),
                    "%max%", String.valueOf(levelConfig.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        if (level < levelConfig.getMinLevel() || level > levelConfig.getMaxLevel()) {
            message(plugin, sender, Message.LEVEL_INVALID,
                    "%min%", String.valueOf(levelConfig.getMinLevel()),
                    "%max%", String.valueOf(levelConfig.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        PlayerLevel playerLevel = levelManager.setLevel(target.getUniqueId(), level);
        message(plugin, sender, Message.LEVEL_SET_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name,
                "%level%", String.valueOf(playerLevel.getLevel()));
        return CommandType.SUCCESS;
    }
}
