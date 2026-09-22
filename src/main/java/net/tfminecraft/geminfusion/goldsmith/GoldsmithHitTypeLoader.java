package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

public class GoldsmithHitTypeLoader {

	private static final LinkedHashMap<String, GoldsmithHitType> map = new LinkedHashMap<>();

	public static LinkedHashMap<String, GoldsmithHitType> get() {
		return map;
	}

	public static GoldsmithHitType getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			map.put(key, new GoldsmithHitType(key, config.getConfigurationSection(key)));
		}
	}
}
