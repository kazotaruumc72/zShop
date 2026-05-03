package fr.maxlego08.shop.command.commands;

import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.command.VCommand;
import fr.maxlego08.shop.zcore.enums.Message;
import fr.maxlego08.shop.zcore.enums.Permission;
import fr.maxlego08.shop.zcore.utils.commands.CommandType;

public class CommandShopLevel2 extends VCommand {

    public CommandShopLevel2(ShopPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZSHOP_LEVEL2);
        this.addSubCommand("level2");
        this.setDescription(Message.DESCRIPTION_LEVEL2);
        this.addSubCommand(new CommandShopLevel2Get(plugin));
        this.addSubCommand(new CommandShopLevel2Set(plugin));
        this.addSubCommand(new CommandShopLevel2Add(plugin));
        this.addSubCommand(new CommandShopLevel2Remove(plugin));
        this.addSubCommand(new CommandShopLevel2Reset(plugin));
        this.addSubCommand(new CommandShopLevel2ExpAdd(plugin));
        this.addSubCommand(new CommandShopLevel2ExpRemove(plugin));
    }

    @Override
    protected CommandType perform(ShopPlugin plugin) {
        syntaxMessage();
        return CommandType.SUCCESS;
    }
}