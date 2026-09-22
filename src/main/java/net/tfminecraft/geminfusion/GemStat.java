package net.tfminecraft.geminfusion;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

public class GemStat {
	private final String id;
	private final String statId;
	private final double min;
	private final double max;

	private GemStat(String id, String statId, double min, double max) {
		this.id = id;
		this.statId = statId;
		this.min = min;
		this.max = max;
	}

	public String getId() {
		return id;
	}

	public String getStatId() {
		return statId;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	/**
	 * New shape: {@code stat} / {@code min} / {@code max}.
	 * Legacy: first entry of {@code stats: - type(min-max)}.
	 * Returns null when the block cannot be used.
	 */
	public static GemStat parse(String gemId, String rarityId, ConfigurationSection config) {
		if (config == null) {
			warn("Gem '" + gemId + "' " + rarityId + " has no rarity section, skipping.");
			return null;
		}

		if (config.contains("stat")) {
			String statId = config.getString("stat");
			if (statId == null || statId.isBlank()) {
				warn("Gem '" + gemId + "' " + rarityId + " has empty stat, skipping.");
				return null;
			}
			double min = config.getDouble("min");
			double max = config.contains("max") ? config.getDouble("max") : min;
			return new GemStat(rarityId, statId, min, max);
		}

		List<String> stats = config.getStringList("stats");
		if (stats != null && !stats.isEmpty()) {
			GemStat parsed = parseLegacyEntry(rarityId, stats.get(0));
			if (parsed == null) {
				warn("Gem '" + gemId + "' " + rarityId + " has an unreadable stats: entry, skipping.");
				return null;
			}
			warn("Gem '" + gemId + "' " + rarityId + " still uses stats: list; using first entry only.");
			return parsed;
		}

		warn("Gem '" + gemId + "' " + rarityId + " has neither stat nor stats, skipping.");
		return null;
	}

	private static GemStat parseLegacyEntry(String rarityId, String entry) {
		if (entry == null) {
			return null;
		}
		int open = entry.indexOf('(');
		int close = entry.lastIndexOf(')');
		if (open <= 0 || close <= open) {
			return null;
		}
		String statId = entry.substring(0, open).trim();
		String range = entry.substring(open + 1, close);
		int dash = range.indexOf('-');
		if (dash < 0) {
			return null;
		}
		try {
			double min = Double.parseDouble(range.substring(0, dash).trim());
			double max = Double.parseDouble(range.substring(dash + 1).trim());
			return new GemStat(rarityId, statId, min, max);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static void warn(String message) {
		Logger logger = InfusionMain.plugin == null
				? Logger.getLogger("GemInfusion")
				: InfusionMain.plugin.getLogger();
		logger.warning(message);
	}
}
