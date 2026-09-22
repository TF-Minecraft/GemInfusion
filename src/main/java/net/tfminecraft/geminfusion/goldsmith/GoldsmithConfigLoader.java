package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;

public class GoldsmithConfigLoader {

	public void load(File configFile) {
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		GoldsmithCache.station = config.getString("station");
		GoldsmithCache.brandingTool = config.getString("branding-tool");
		GoldsmithCache.permission = config.getString("permission");
		if (GoldsmithCache.permission != null && GoldsmithCache.permission.isBlank()) {
			GoldsmithCache.permission = null;
		}
		GoldsmithCache.minHitPercent = config.getDouble("min-hit-percent", 0.40);
		GoldsmithCache.hitOvershootWarnPercent = config.getDouble("hit-overshoot-warn-percent", 30.0);
		String overshootMessage = config.getString("hit-overshoot-warn-message");
		if (overshootMessage == null || overshootMessage.isBlank()) {
			overshootMessage = "§cYou have worked this piece too much";
		}
		GoldsmithCache.hitOvershootWarnMessage = overshootMessage;
	}
}
