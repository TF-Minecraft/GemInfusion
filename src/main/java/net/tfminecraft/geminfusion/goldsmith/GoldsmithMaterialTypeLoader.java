package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

public class GoldsmithMaterialTypeLoader {

	private static final LinkedHashMap<String, GoldsmithMaterialType> map = new LinkedHashMap<>();

	public static LinkedHashMap<String, GoldsmithMaterialType> get() {
		return map;
	}

	public static GoldsmithMaterialType getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public static String display(String typeId) {
		GoldsmithMaterialType type = getByString(typeId);
		if (type != null) return type.getName();
		if (typeId == null || typeId.isBlank()) return "Materials";
		return Character.toUpperCase(typeId.charAt(0)) + typeId.substring(1) + " Materials";
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			map.put(key, new GoldsmithMaterialType(key, config.getConfigurationSection(key)));
		}
	}
}
