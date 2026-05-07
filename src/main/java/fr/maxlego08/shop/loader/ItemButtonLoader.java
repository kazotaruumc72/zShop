package fr.maxlego08.shop.loader;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.shop.ShopPlugin;
import fr.maxlego08.shop.api.economy.ShopEconomy;
import fr.maxlego08.shop.api.limit.Limit;
import fr.maxlego08.shop.api.limit.LimitType;
import fr.maxlego08.shop.buttons.PriceExpression;
import fr.maxlego08.shop.buttons.ZItemButton;
import fr.maxlego08.shop.exceptions.EconomyNotFoundException;
import fr.maxlego08.shop.save.Config;
import fr.maxlego08.shop.zcore.utils.loader.Loader;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Optional;

public class ItemButtonLoader extends ButtonLoader {

    private final ShopPlugin plugin;

    public ItemButtonLoader(ShopPlugin plugin) {
        super(plugin, "zshop_item");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {

        String defaultEconomy = configuration.getString("economy", Config.defaultEconomy);

        PriceExpression sellPrice = new PriceExpression(configuration.get(path + "sellPrice", 0.0));
        PriceExpression buyPrice = new PriceExpression(configuration.get(path + "buyPrice", 0.0));

        int maxStack = configuration.getInt(path + "maxStack", 64);
        List<String> lore = configuration.getStringList(path + "lore");
        boolean giveItem = configuration.getBoolean(path + "giveItem", true);
        boolean enableLog = configuration.getBoolean(path + "enableLog", true);
        boolean affectByPriceModifier = configuration.getBoolean(path + "affectByPriceModifier", true);
        boolean unstackable = configuration.getBoolean(path + "unstackable", false);
        List<String> buyCommands = configuration.getStringList(path + "buyCommands");
        List<String> sellCommands = configuration.getStringList(path + "sellCommands");
        String mob = configuration.getString(path + "mob", null);

        String economyName = configuration.getString(path + "economy", defaultEconomy);
        Optional<ShopEconomy> optional = plugin.getEconomyManager().getEconomy(economyName);
        if (!optional.isPresent()) {
            throw new EconomyNotFoundException("Economy " + economyName + " was not found for button " + path);
        }
        ShopEconomy shopEconomy = optional.get();

        if (lore.isEmpty()) lore = this.plugin.getShopManager().getDefaultLore();

        Limit serverSellLimit = null;
        Limit serverBuyLimit = null;
        Limit playerSellLimit = null;
        Limit playerBuyLimit = null;
        String material = configuration.getString(path + "item.material", "STONE");

        if (configuration.contains(path + "serverSellLimit")) {
            Loader<Limit> loader = new LimitLoader(plugin, material, LimitType.SERVER_SELL);
            serverSellLimit = loader.load(configuration, path + "serverSellLimit.");
            this.plugin.getLimiterManager().create(serverSellLimit);
        }

        if (configuration.contains(path + "serverBuyLimit")) {
            Loader<Limit> loader = new LimitLoader(plugin, material, LimitType.SERVER_BUY);
            serverBuyLimit = loader.load(configuration, path + "serverBuyLimit.");
            this.plugin.getLimiterManager().create(serverBuyLimit);
        }

        if (configuration.contains(path + "playerSellLimit")) {
            Loader<Limit> loader = new LimitLoader(plugin, material, LimitType.PLAYER_SELL);
            playerSellLimit = loader.load(configuration, path + "playerSellLimit.");
            this.plugin.getLimiterManager().create(playerSellLimit);
        }

        if (configuration.contains(path + "playerBuyLimit")) {
            Loader<Limit> loader = new LimitLoader(plugin, material, LimitType.PLAYER_BUY);
            playerBuyLimit = loader.load(configuration, path + "playerBuyLimit.");
            this.plugin.getLimiterManager().create(playerBuyLimit);
        }

        String inventoryBuy = configuration.getString(path + "inventoryBuy", null);
        String inventorySell = configuration.getString(path + "inventorySell", null);
        String depositReason = configuration.getString(path + "deposit-reason", plugin.getConfig().getString("deposit-reason"));
        String withdrawReason = configuration.getString(path + "withdraw-reason", plugin.getConfig().getString("withdraw-reason"));

        return new ZItemButton(plugin, sellPrice, buyPrice, maxStack, lore, shopEconomy, buyCommands, sellCommands, giveItem, serverSellLimit, serverBuyLimit, playerSellLimit, playerBuyLimit, enableLog, affectByPriceModifier, mob, inventoryBuy, inventorySell, unstackable, depositReason, withdrawReason);
    }
}
