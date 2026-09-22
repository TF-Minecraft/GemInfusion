package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class GoldsmithHitType {

	private final String id;
	private final String name;

	public GoldsmithHitType(String key, ConfigurationSection config) {
		this.id = key;
		this.name = StringFormatter.formatHex(config.getString("name", key));
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
