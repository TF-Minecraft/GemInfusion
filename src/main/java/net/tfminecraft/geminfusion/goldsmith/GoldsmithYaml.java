package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

final class GoldsmithYaml {

	private GoldsmithYaml() {
	}

	static FileConfiguration read(File configFile) {
		if (configFile == null || !configFile.exists()) {
			GoldsmithLog.warn("Config file " + (configFile == null ? "null" : configFile.getName()) + " is missing.");
			return null;
		}
		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);
		} catch (IOException | InvalidConfigurationException e) {
			GoldsmithLog.warn("Could not read " + configFile.getName() + ": " + e.getMessage());
			return null;
		}
		return config;
	}
}
