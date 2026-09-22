package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class Quality {

	private final String id;
	private final double amount;
	private final int value;
	private final String name;
	private final double statMin;
	private final double statMax;

	public Quality(String key, ConfigurationSection config) {
		id = key;
		amount = config.getDouble("amount");
		value = config.getInt("value");
		name = StringFormatter.formatHex(config.getString("name", key));
		statMin = config.getDouble("stat-min");
		statMax = config.getDouble("stat-max");
	}

	public String getId() {
		return id;
	}

	public double getAmount() {
		return amount;
	}

	public int getValue() {
		return value;
	}

	public String getName() {
		return name;
	}

	public double getStatMin() {
		return statMin;
	}

	public double getStatMax() {
		return statMax;
	}

	public boolean isValid(double score) {
		return score >= amount;
	}
}
