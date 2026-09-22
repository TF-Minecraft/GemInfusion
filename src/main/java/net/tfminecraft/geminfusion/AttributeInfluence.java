package net.tfminecraft.geminfusion;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.Indyuce.mmocore.api.player.PlayerData;

/**
 * Maps an MMOCore attribute onto a multiplicative delta in [-maxPenalty, +maxBonus].
 */
public final class AttributeInfluence {

	public static AttributeInfluence infusion = disabled();
	public static AttributeInfluence jewelry = disabled();

	private String mmocoreId = "intelligence";
	private double floor;
	private double neutral;
	private double full;
	private double maxBonus = 0.2;
	private double maxPenalty = 0.2;

	private AttributeInfluence() {
	}

	public static AttributeInfluence disabled() {
		AttributeInfluence a = new AttributeInfluence();
		a.floor = 0;
		a.neutral = 0;
		a.full = 0;
		return a;
	}

	public static AttributeInfluence from(ConfigurationSection section, String defaultId) {
		if (section == null) {
			AttributeInfluence a = disabled();
			a.mmocoreId = defaultId == null ? "intelligence" : defaultId;
			return a;
		}
		AttributeInfluence a = new AttributeInfluence();
		String id = section.getString("mmocore-id", defaultId);
		a.mmocoreId = (id == null || id.isBlank()) ? defaultId : id;
		a.floor = section.getDouble("floor", 0);
		a.neutral = section.getDouble("neutral", 10);
		a.full = section.getDouble("full", 20);
		a.maxBonus = section.getDouble("max-bonus", 0.2);
		a.maxPenalty = section.getDouble("max-penalty", 0.2);
		return a;
	}

	public double forPlayer(Player player) {
		return delta(readAttribute(player));
	}

	public double delta(double attr) {
		if (!(floor < neutral && neutral < full)) {
			return 0;
		}
		if (attr <= floor) {
			return -maxPenalty;
		}
		if (attr >= full) {
			return maxBonus;
		}
		if (attr < neutral) {
			double span = neutral - floor;
			double t = (attr - floor) / span;
			return lerp(-maxPenalty, 0, t);
		}
		double span = full - neutral;
		double t = (attr - neutral) / span;
		return lerp(0, maxBonus, t);
	}

	public double readAttribute(Player player) {
		if (player == null || mmocoreId == null || mmocoreId.isBlank()) {
			return 0;
		}
		if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) {
			return 0;
		}
		try {
			return PlayerData.get(player).getAttributes().getInstance(mmocoreId).getTotal();
		} catch (Exception ex) {
			return 0;
		}
	}

	private static double lerp(double a, double b, double t) {
		if (t < 0) {
			t = 0;
		}
		if (t > 1) {
			t = 1;
		}
		return a + (b - a) * t;
	}
}
