package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;

public class CommandShopLevel extends VCommand {

    public CommandShopLevel(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL);
        this.addSubCommand("level");
        this.setDescription(Message.DESCRIPTION_LEVEL);
        this.addSubCommand(new CommandShopLevelGet(plugin));
        this.addSubCommand(new CommandShopLevelSet(plugin));
        this.addSubCommand(new CommandShopLevelReset(plugin));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        syntaxMessage();
        return CommandType.SUCCESS;
    }
}
