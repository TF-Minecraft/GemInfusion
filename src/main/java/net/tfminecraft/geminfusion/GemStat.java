package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;

public class GemStat {
	/** {@code min-max}, or one number for a fixed stat. */
	private static final Pattern RANGE = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*-\\s*(\\d+(?:\\.\\d+)?))?");

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
	 * Reads a gem's stat for each rarity. Compact shape: one {@code stat} for the gem and {@code bands}, either the
	 * name of a set under the top-level {@code stat-bands} or a section of {@code <rarity>: min-max}. Older configs
	 * list {@code rarities.<rarity>} blocks instead (see {@link #parse}), which still load.
	 * Only keys written in this config count: Bukkit falls back to the bundled config.yml for missing keys, which
	 * would hand an older server config the bundled bands (or empty sections made from them).
	 */
	public static List<GemStat> parseGem(String gemId, ConfigurationSection gem, ConfigurationSection statBands) {
		List<GemStat> stats = new ArrayList<>();
		Object bandsValue = gem.get("bands", null);
		if (bandsValue != null) {
			if (gem.get("rarities", null) != null) {
				warn("Gem '" + gemId + "' has both bands and rarities; using bands.");
			}
			String statId = gem.getString("stat", null);
			if (statId == null || statId.isBlank()) {
				warn("Gem '" + gemId + "' has bands but no stat, skipping its stats.");
				return stats;
			}
			ConfigurationSection bands = bandsValue instanceof ConfigurationSection own ? own
					: ownSection(statBands, String.valueOf(bandsValue));
			if (bands == null) {
				warn("Gem '" + gemId + "' uses unknown stat-bands set '" + bandsValue + "', skipping its stats.");
				return stats;
			}
			for (String rarityId : bands.getKeys(false)) {
				GemStat stat = parseRange(gemId, rarityId, statId, bands.getString(rarityId, null));
				if (stat != null) {
					stats.add(stat);
				}
			}
			return stats;
		}
		ConfigurationSection rarities = ownSection(gem, "rarities");
		if (rarities == null) {
			warn("Gem '" + gemId + "' has neither bands nor rarities, skipping its stats.");
			return stats;
		}
		for (String rarityId : rarities.getKeys(false)) {
			GemStat stat = parse(gemId, rarityId, rarities.getConfigurationSection(rarityId));
			if (stat != null) {
				stats.add(stat);
			}
		}
		return stats;
	}

	/** The section written at this key of this config, ignoring the bundled defaults; null when there is none. */
	static ConfigurationSection ownSection(ConfigurationSection parent, String key) {
		return parent != null && parent.get(key, null) instanceof ConfigurationSection own ? own : null;
	}

	private static GemStat parseRange(String gemId, String rarityId, String statId, String text) {
		Matcher range = RANGE.matcher(String.valueOf(text).trim());
		if (!range.matches()) {
			warn("Gem '" + gemId + "' " + rarityId + " band '" + text + "' is not min-max, skipping.");
			return null;
		}
		double min = Double.parseDouble(range.group(1));
		double max = range.group(2) == null ? min : Double.parseDouble(range.group(2));
		return new GemStat(rarityId, statId, min, max);
	}

	/**
	 * Old per-rarity shape: {@code stat} / {@code min} / {@code max}.
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
