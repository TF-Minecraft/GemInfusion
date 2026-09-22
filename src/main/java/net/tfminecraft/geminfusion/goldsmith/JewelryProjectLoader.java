package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class JewelryProjectLoader {

	private static final LinkedHashMap<String, JewelryProject> map = new LinkedHashMap<>();

	public static LinkedHashMap<String, JewelryProject> get() {
		return map;
	}

	public static JewelryProject getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null || !section.contains("item")) {
				GoldsmithLog.warn("Skipping non-project key '" + key + "' in projects.yml (missing item).");
				continue;
			}
			map.put(key, new JewelryProject(key, section));
		}
	}
}
