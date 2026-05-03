package fr.maxlego08.shop.zcore.enums;

public enum Permission {
	
	ZSHOP_HELP,
	ZSHOP_RELOAD,
	ZSHOP_CONVERT,
	ZSHOP_LOGS,
	ZSHOP_SELL_ALL,
	ZSHOP_SELL_HAND,
	ZSHOP_SELL_HAND_ALL,
	ZSHOP_RESET_LIMIT,
    ZSHOP_RESET_LIMIT_ALL,
    ZSHOP_RESET_LIMIT_PLAYER,
    ZSHOP_RESET_LIMIT_SERVER,
    ZSHOP_LEVEL,
    ZSHOP_LEVEL_GET,
    ZSHOP_LEVEL_SET,
    ZSHOP_LEVEL_ADD,
    ZSHOP_LEVEL_REMOVE,
    ZSHOP_LEVEL_RESET,
    ZSHOP_LEVEL2,
    ZSHOP_LEVEL2_GET,
    ZSHOP_LEVEL2_SET,
    ZSHOP_LEVEL2_ADD,
    ZSHOP_LEVEL2_REMOVE,
    ZSHOP_LEVEL2_RESET,
    ZSHOP_LEVEL2_EXP_ADD,
    ZSHOP_LEVEL2_EXP_REMOVE

	;

	private final String permission;

	private Permission() {
		this.permission = this.name().toLowerCase().replace("_", ".");
	}

	public String getPermission() {
		return permission;
	}

}
