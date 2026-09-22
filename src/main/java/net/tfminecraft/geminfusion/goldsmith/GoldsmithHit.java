package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class GoldsmithHit {

	private final String id;
	private final String tool;
	private final String name;
	private final GoldsmithHitType type;

	public GoldsmithHit(String key, ConfigurationSection config) {
		this.id = key;
		this.tool = config.getString("tool");
		this.name = StringFormatter.formatHex(config.getString("name", key));
		this.type = GoldsmithHitTypeLoader.getByString(config.getString("type"));
	}

	public String getId() {
		return id;
	}

	public String getTool() {
		return tool;
	}

	public String getName() {
		return name;
	}

	public GoldsmithHitType getType() {
		return type;
	}
}
