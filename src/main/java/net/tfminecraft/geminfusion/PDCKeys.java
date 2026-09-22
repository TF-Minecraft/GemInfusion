package net.tfminecraft.geminfusion;

import org.bukkit.NamespacedKey;

public final class PDCKeys {
	private PDCKeys() {
	}

	public static NamespacedKey gemRarity() {
		return new NamespacedKey(InfusionMain.plugin, "gem_rarity");
	}

	public static NamespacedKey socketRarities() {
		return new NamespacedKey(InfusionMain.plugin, "socket_rarities");
	}
}
