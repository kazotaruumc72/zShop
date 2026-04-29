package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.LevelConfig;
import fr.maxlego08.shop.level.PlayerLevel;
import fr.maxlego08.shop.level.ZLevelManager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code /shop level remove <player> <amount>} – removes {@code amount}
 * shop levels from the target. Negative or zero amounts are rejected. The
 * resulting level is clamped to the {@code [minLevel, maxLevel]} range
 * defined in {@code levels.yml}.
 */
public class CommandShopLevelRemove extends VCommand {

    public CommandShopLevelRemove(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL_REMOVE);
        this.addSubCommand("remove");
        this.setDescription(Message.DESCRIPTION_LEVEL_REMOVE);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
        this.addRequireArg("amount", (sender, args) -> Arrays.asList("1", "2", "3", "5", "10"));
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

        int amount;
        try {
            amount = this.argAsInteger(1);
        } catch (NumberFormatException ex) {
            message(plugin, sender, Message.LEVEL_INVALID,
                    "%min%", String.valueOf(levelConfig.getMinLevel()),
                    "%max%", String.valueOf(levelConfig.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        if (amount <= 0) {
            message(plugin, sender, Message.LEVEL_INVALID,
                    "%min%", String.valueOf(levelConfig.getMinLevel()),
                    "%max%", String.valueOf(levelConfig.getMaxLevel()));
            return CommandType.SUCCESS;
        }

        PlayerLevel playerLevel = levelManager.getOrCreate(target.getUniqueId());
        int target_level = playerLevel.getLevel() - amount;
        playerLevel = levelManager.setLevel(target.getUniqueId(), target_level);

        message(plugin, sender, Message.LEVEL_REMOVE_SUCCESS,
                "%player%", target.getName() != null ? target.getName() : name,
                "%amount%", String.valueOf(amount),
                "%level%", String.valueOf(playerLevel.getLevel()));
        return CommandType.SUCCESS;
    }
}



