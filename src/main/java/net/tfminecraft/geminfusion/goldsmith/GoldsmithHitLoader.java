package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

public class GoldsmithHitLoader {

	private static final LinkedHashMap<String, GoldsmithHit> map = new LinkedHashMap<>();

	public static LinkedHashMap<String, GoldsmithHit> get() {
		return map;
	}

	public static GoldsmithHit getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public static GoldsmithHit getByTool(String path) {
		if (path == null) return null;
		for (GoldsmithHit hit : map.values()) {
			if (path.equalsIgnoreCase(hit.getTool())) return hit;
		}
		return null;
	}

	public static GoldsmithHit getByItem(ItemStack item) {
		if (item == null || item.getType().isAir()) return null;
		for (GoldsmithHit hit : map.values()) {
			if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, hit.getTool())) {
				return hit;
			}
		}
		return null;
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			GoldsmithHit hit = new GoldsmithHit(key, config.getConfigurationSection(key));
			if (hit.getType() == null) {
				GoldsmithLog.warn("Hit '" + key + "' has an unknown type, skipping. Check hit-types.yml.");
				continue;
			}
			if (hit.getTool() == null) {
				GoldsmithLog.warn("Hit '" + key + "' has no tool set, skipping.");
				continue;
			}
			map.put(key, hit);
		}
	}
}
