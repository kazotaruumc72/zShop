package fr.maxlego08.shop.zcore.utils;

import fr.maxlego08.shop.placeholder.ZShopPlaceholders;
import org.bukkit.entity.Player;

import java.util.List;

public class PapiUtils {

    public String papi(String placeHolder, Player player) {
        return ZShopPlaceholders.setPlaceholders(player, placeHolder);
    }

    public List<String> papi(List<String> placeHolder, Player player) {
        return ZShopPlaceholders.setPlaceholders(player, placeHolder);
    }

}