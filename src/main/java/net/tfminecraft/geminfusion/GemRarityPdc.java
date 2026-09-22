package net.tfminecraft.geminfusion;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class GemRarityPdc {
	private GemRarityPdc() {
	}

	public static void write(ItemStack item, String rarityId) {
		if (item == null || rarityId == null || !item.hasItemMeta()) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(PDCKeys.gemRarity(), PersistentDataType.STRING, rarityId);
		item.setItemMeta(meta);
	}

	public static String read(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		return item.getItemMeta().getPersistentDataContainer().get(PDCKeys.gemRarity(), PersistentDataType.STRING);
	}
}
