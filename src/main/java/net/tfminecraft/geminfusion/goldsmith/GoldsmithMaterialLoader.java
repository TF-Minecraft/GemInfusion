package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

public class GoldsmithMaterialLoader {

	private static final LinkedHashMap<String, GoldsmithMaterial> map = new LinkedHashMap<>();

	public static LinkedHashMap<String, GoldsmithMaterial> get() {
		return map;
	}

	public static GoldsmithMaterial getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			GoldsmithMaterial material = new GoldsmithMaterial(key, config.getConfigurationSection(key));
			if (material.getPath() == null) {
				GoldsmithLog.warn("Material '" + key + "' has no path set, skipping.");
				continue;
			}
			map.put(key, material);
		}
	}
}
