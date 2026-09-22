package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class ProjectTierLoader {

	private static final LinkedHashMap<String, Double> map = new LinkedHashMap<>();

	public static Map<String, Double> get() {
		return map;
	}

	public static double getMultiplier(String tierId) {
		if (tierId == null || tierId.isBlank()) {
			return 1.0;
		}
		Double multiplier = map.get(tierId.toLowerCase());
		if (multiplier == null) {
			GoldsmithLog.warn("Unknown project tier '" + tierId + "', using multiplier 1.0.");
			return 1.0;
		}
		return multiplier;
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section != null) {
				continue;
			}
			map.put(key.toLowerCase(), config.getDouble(key));
		}
	}
}
