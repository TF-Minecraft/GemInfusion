package net.tfminecraft.geminfusion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.gson.JsonObject;
import io.lumine.mythic.lib.gson.JsonParser;

public final class SocketRarityStore {
	private SocketRarityStore() {
	}

	public static Map<String, String> read(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return new HashMap<>();
		}
		String json = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.socketRarities(),
				PersistentDataType.STRING);
		if (json == null || json.isBlank()) {
			return new HashMap<>();
		}
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		Map<String, String> map = new HashMap<>();
		for (String key : parsed.keySet()) {
			map.put(key, parsed.get(key).getAsString());
		}
		return map;
	}

	public static void write(ItemStack item, Map<String, String> rarities) {
		if (item == null) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return;
		}
		if (rarities == null || rarities.isEmpty()) {
			meta.getPersistentDataContainer().remove(PDCKeys.socketRarities());
		} else {
			JsonObject json = new JsonObject();
			for (Map.Entry<String, String> entry : rarities.entrySet()) {
				json.addProperty(entry.getKey(), entry.getValue());
			}
			meta.getPersistentDataContainer().set(PDCKeys.socketRarities(), PersistentDataType.STRING,
					json.toString());
		}
		item.setItemMeta(meta);
	}

	public static void mergeOnto(ItemStack target, ItemStack source) {
		if (target == null || source == null) {
			return;
		}
		Map<String, String> merged = read(target);
		merged.putAll(read(source));
		write(target, merged);
	}

	public static void put(ItemStack item, UUID historicId, String rarityId) {
		if (item == null || historicId == null || rarityId == null) {
			return;
		}
		Map<String, String> map = read(item);
		map.put(historicId.toString(), rarityId);
		write(item, map);
	}

	public static void remove(ItemStack item, UUID historicId) {
		if (item == null || historicId == null) {
			return;
		}
		Map<String, String> map = read(item);
		if (map.remove(historicId.toString()) != null) {
			write(item, map);
		}
	}

	public static String get(ItemStack item, UUID historicId) {
		if (item == null || historicId == null) {
			return null;
		}
		return read(item).get(historicId.toString());
	}

	public static Map<String, String> unmodifiableView(ItemStack item) {
		return Collections.unmodifiableMap(read(item));
	}
}
