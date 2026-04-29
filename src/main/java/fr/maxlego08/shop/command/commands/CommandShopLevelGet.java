package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.level.PlayerLevel;
import fr.maxlego08.shop.level.ZLevelManager;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class CommandShopLevelGet extends VCommand {

    public CommandShopLevelGet(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL_GET);
        this.addSubCommand("get");
        this.setDescription(Message.DESCRIPTION_LEVEL_GET);
        this.addOptionalArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList()));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        ZLevelManager levelManager = plugin.getLevelManager();
        String name = this.argAsString(0);
        if (name == null) {
            if (this.player == null) {
                return CommandType.SYNTAX_ERROR;
            }
            PlayerLevel playerLevel = levelManager.getOrCreate(this.player.getUniqueId());
            double bonus = levelManager.getConfig().getBonusPercent(playerLevel.getLevel());
            message(plugin, sender, Message.LEVEL_GET_SELF,
                    "%level%", String.valueOf(playerLevel.getLevel()),
                    "%items%", String.valueOf(playerLevel.getTotalItems()),
                    "%bonus%", ZLevelManager.formatBonus(bonus));
            return CommandType.SUCCESS;
        }

        OfflinePlayer target = this.argAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            message(plugin, sender, Message.LEVEL_PLAYER_NOT_FOUND, "%player%", name);
            return CommandType.SUCCESS;
        }
        PlayerLevel playerLevel = levelManager.getOrCreate(target.getUniqueId());
        double bonus = levelManager.getConfig().getBonusPercent(playerLevel.getLevel());
        message(plugin, sender, Message.LEVEL_GET_OTHER,
                "%player%", target.getName() != null ? target.getName() : name,
                "%level%", String.valueOf(playerLevel.getLevel()),
                "%items%", String.valueOf(playerLevel.getTotalItems()),
                "%bonus%", ZLevelManager.formatBonus(bonus));
        return CommandType.SUCCESS;
    }
}
