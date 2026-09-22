package net.tfminecraft.geminfusion;

import org.bukkit.configuration.ConfigurationSection;

public class GemRarity {
	private String id;
	private String name;
	private double chance;
	private boolean announce;
	public boolean shouldAnnounce() {
		return announce;
	}
	public void setAnnounce(boolean b) {
		this.announce = b;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public double getChance() {
		return chance;
	}
	public void setChance(double chance) {
		this.chance = chance;
	}
	public GemRarity(String key, ConfigurationSection config) {
		this.id = key;
		this.name = config.getString("name");
		this.chance = config.getDouble("chance");
		if(config.contains("announce")) {
			this.announce = config.getBoolean("announce");
		} else {
			this.announce = false;
		}
	}
}
