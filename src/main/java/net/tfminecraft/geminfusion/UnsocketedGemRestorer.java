package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;

public final class UnsocketedGemRestorer {
	private UnsocketedGemRestorer() {
	}

	public static void restore(Player player, String rarityId, Gemstone gem, Map<Integer, ItemStack> beforeInventory) {
		if (player == null || rarityId == null || gem == null) {
			return;
		}
		GemRarity rarity = ConfigLoader.findRarityById(rarityId);
		if (rarity == null) {
			return;
		}

		Integer slot = findTargetSlot(player, gem, beforeInventory);
		if (slot == null) {
			return;
		}

		PlayerInventory inventory = player.getInventory();
		ItemStack item = inventory.getItem(slot);
		if (item == null || item.getType().isAir()) {
			return;
		}
		inventory.setItem(slot, InfusedGemBuilder.applyCosmeticsToItem(item, gem, rarity));
	}

	private static Integer findTargetSlot(Player player, Gemstone gem, Map<Integer, ItemStack> beforeInventory) {
		PlayerInventory inventory = player.getInventory();
		String gemType = gem.getMMOItemString().split("\\.")[0];
		String gemId = gem.getMMOItemString().split("\\.")[1];
		List<Integer> needsRestore = new ArrayList<>();

		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack item = inventory.getItem(slot);
			if (!matchesGem(item, gemType, gemId) || !needsRestore(item)) {
				continue;
			}
			needsRestore.add(slot);
		}

		if (needsRestore.isEmpty()) {
			return null;
		}
		if (needsRestore.size() == 1) {
			return needsRestore.get(0);
		}

		for (int slot : needsRestore) {
			ItemStack before = beforeInventory.get(slot);
			if (before == null || before.getType().isAir()) {
				return slot;
			}
		}

		for (int slot : needsRestore) {
			ItemStack item = inventory.getItem(slot);
			ItemStack before = beforeInventory.get(slot);
			if (before != null && !before.getType().isAir() && item.getAmount() > before.getAmount()) {
				return slot;
			}
		}

		return null;
	}

	private static boolean matchesGem(ItemStack item, String gemType, String gemId) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) {
			return false;
		}
		return nbt.getType().equalsIgnoreCase(gemType)
				&& nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(gemId);
	}

	private static boolean needsRestore(ItemStack item) {
		NBTItem nbt = NBTItem.get(item);
		if (nbt.hasTag(ItemStats.DISPLAYED_TYPE.getNBTPath())) {
			String displayed = nbt.getString(ItemStats.DISPLAYED_TYPE.getNBTPath());
			if (displayed != null && !displayed.isBlank() && !displayed.equalsIgnoreCase("Blank Gemstone")) {
				return false;
			}
		}
		return true;
	}
}
