package net.tfminecraft.geminfusion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class UnsocketInventorySnapshot {
	private static final Map<UUID, Map<Integer, ItemStack>> PENDING = new ConcurrentHashMap<>();

	private UnsocketInventorySnapshot() {
	}

	public static void capture(Player player) {
		if (player == null) {
			return;
		}
		Map<Integer, ItemStack> slots = new HashMap<>();
		for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
			ItemStack item = player.getInventory().getItem(slot);
			if (item != null && !item.getType().isAir()) {
				slots.put(slot, item.clone());
			}
		}
		PENDING.put(player.getUniqueId(), slots);
	}

	public static Map<Integer, ItemStack> poll(Player player) {
		if (player == null) {
			return Map.of();
		}
		Map<Integer, ItemStack> snapshot = PENDING.remove(player.getUniqueId());
		return snapshot != null ? snapshot : Map.of();
	}

	public static void clear(Player player) {
		if (player == null) {
			return;
		}
		PENDING.remove(player.getUniqueId());
	}
}
