package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.tfminecraft.geminfusion.ConfigLoader;
import net.tfminecraft.geminfusion.GemStat;
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
		Gemstone gem = ConfigLoader.findGemByMmoItem(nbt.getType(), nbt.getString("MMOITEMS_ITEM_ID"));
		if (gem == null) return false;
		String displayed = nbt.getString("MMOITEMS_DISPLAYED_TYPE");
		if (DISPLAY_INFUSED.equalsIgnoreCase(displayed)) return true;
		return DISPLAY_BLANK.equalsIgnoreCase(displayed) && hasInfusionStat(nbt, gem);
	}

	/**
	 * An infused gem that an MMOItems rebuild (e.g. unsocketing) turned back into a
	 * "Blank Gemstone": it keeps the rolled infusion stat, which blank templates never have.
	 */
	public static boolean isReset(ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) return false;
		if (!DISPLAY_BLANK.equalsIgnoreCase(nbt.getString("MMOITEMS_DISPLAYED_TYPE"))) return false;
		Gemstone gem = ConfigLoader.findGemByMmoItem(nbt.getType(), nbt.getString("MMOITEMS_ITEM_ID"));
		return gem != null && hasInfusionStat(nbt, gem);
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

	private static boolean hasInfusionStat(NBTItem nbt, Gemstone gem) {
		for (GemStat block : gem.getStats()) {
			ItemStat<?, ?> stat = MMOItems.plugin.getStats().get(block.getStatId().toUpperCase());
			if (stat == null) continue;
			String path = stat.getNBTPath();
			if (nbt.hasTag(path) && nbt.getDouble(path) != 0) return true;
		}
		return false;
	}
}
