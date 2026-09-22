package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.geminfusion.ConfigLoader;
import net.tfminecraft.geminfusion.Gemstone;

public final class InfusedGemValidator {

	private static final String DISPLAY_BLANK = "Blank Gemstone";
	private static final String DISPLAY_INFUSED = "Infused Gemstone";

	private InfusedGemValidator() {
	}

	public static boolean isInfused(ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) return false;
		if (!DISPLAY_INFUSED.equalsIgnoreCase(nbt.getString("MMOITEMS_DISPLAYED_TYPE"))) return false;
		Gemstone gem = ConfigLoader.findGemByMmoItem(nbt.getType(), nbt.getString("MMOITEMS_ITEM_ID"));
		return gem != null;
	}

	public static boolean isGemstoneCandidate(ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) return false;
		String displayed = nbt.getString("MMOITEMS_DISPLAYED_TYPE");
		if (DISPLAY_BLANK.equalsIgnoreCase(displayed) || DISPLAY_INFUSED.equalsIgnoreCase(displayed)) {
			return true;
		}
		return ConfigLoader.findGemByMmoItem(nbt.getType(), nbt.getString("MMOITEMS_ITEM_ID")) != null;
	}
}
