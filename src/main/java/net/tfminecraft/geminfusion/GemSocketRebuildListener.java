package net.tfminecraft.geminfusion;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;
import net.tfminecraft.tlibs.socket.GemSocketsNbtEditor;

public class GemSocketRebuildListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onRebuild(MMOItemRebuildEvent event) {
		ItemStack oldItem = event.getOldItem();
		ItemStack newItem = event.getNewItem();
		if (oldItem == null || newItem == null) {
			return;
		}

		SocketRarityStore.mergeOnto(newItem, oldItem);

		if (event.getReason() == RebuildReason.GEM_APPLY) {
			handleGemApply(event, oldItem, newItem);
		} else if (event.getReason() == RebuildReason.GEM_UNSOCKET) {
			handleGemUnsocket(event, oldItem, newItem);
		}
	}

	private void handleGemApply(MMOItemRebuildEvent event, ItemStack oldItem, ItemStack newItem) {
		ItemStack cursorSnapshot = event.getAppliedCursorSnapshot();
		String rarityId = cursorSnapshot == null ? null : GemRarityPdc.read(cursorSnapshot);

		Set<UUID> oldGems = GemSocketsNbtEditor.getGemstoneUuids(oldItem);
		UUID newGemUuid = null;
		for (UUID uuid : GemSocketsNbtEditor.getGemstoneUuids(newItem)) {
			if (!oldGems.contains(uuid)) {
				newGemUuid = uuid;
				break;
			}
		}

		if (rarityId != null && newGemUuid != null) {
			SocketRarityStore.put(newItem, newGemUuid, rarityId);
		}

		event.setNewItem(newItem);
	}

	private void handleGemUnsocket(MMOItemRebuildEvent event, ItemStack oldItem, ItemStack newItem) {
		Set<UUID> oldGems = GemSocketsNbtEditor.getGemstoneUuids(oldItem);
		Set<UUID> newGems = GemSocketsNbtEditor.getGemstoneUuids(newItem);
		Set<UUID> removed = new HashSet<>(oldGems);
		removed.removeAll(newGems);

		if (removed.isEmpty()) {
			event.setNewItem(newItem);
			return;
		}

		ItemStack fixed = newItem;
		Player player = event.getPlayer();

		for (UUID removedUuid : removed) {
			String rarityId = SocketRarityStore.get(newItem, removedUuid);
			if (rarityId == null) {
				rarityId = SocketRarityStore.get(oldItem, removedUuid);
			}
			SocketRarityStore.remove(fixed, removedUuid);

			if (rarityId != null) {
				Gemstone gem = findGemFromSocketed(oldItem, removedUuid);
				if (gem != null) {
					String capturedRarity = rarityId;
					Gemstone capturedGem = gem;
					var beforeInventory = UnsocketInventorySnapshot.poll(player);
					Bukkit.getScheduler().runTask(InfusionMain.plugin, () -> UnsocketedGemRestorer.restore(player,
							capturedRarity, capturedGem, beforeInventory));
				}
			}
		}

		event.setNewItem(fixed);
	}

	private Gemstone findGemFromSocketed(ItemStack hostItem, UUID removedUuid) {
		if (GemSocketsNbtEditor.getSockets(hostItem) == null) {
			return null;
		}
		for (var gemData : GemSocketsNbtEditor.getSockets(hostItem).getGems()) {
			if (!gemData.getHistoricUUID().equals(removedUuid)) {
				continue;
			}
			return ConfigLoader.findGemByMmoItem(gemData.getMMOItemType(), gemData.getMMOItemID());
		}
		return null;
	}
}
