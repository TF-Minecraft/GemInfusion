package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Infused gems are named after their rolled stat, not their drawn rarity. Each rarity's name goes to the same share
 * of a gem's possible results as its draw chance, best results first, so a better name never means a weaker gem.
 * Results that tie on one stat keep one name; when the tie would push a rarer name past its share, the whole tie gets
 * the lower name, so rarer names can fall slightly short of their chance but never go over it.
 * The shares are worked out at {@link D20Roll#nameReferenceModifier}; players above it get better names more often.
 */
public final class GemNameCutoffs {

	/** The lowest stat that earns a rarity's name; positive infinity when no result fits in its share. */
	record Cutoff(GemRarity rarity, double from) {
	}

	/** A gem's cutoffs, highest rarity first; stats below all of them get the lowest rarity's name. */
	record Names(List<Cutoff> cutoffs, GemRarity lowest) {
	}

	/** Gem id to its names. Gems missing here keep the drawn rarity's name. */
	private static Map<String, Names> names = new HashMap<>();

	private GemNameCutoffs() {
	}

	/** Rarities are listed lowest to highest; only the ones that can be drawn (finite positive chance) count. */
	public static void load(List<Gemstone> gems, List<GemRarity> rarities) {
		Map<String, Names> loaded = new HashMap<>();
		List<GemRarity> drawable = new ArrayList<>();
		double total = 0;
		for (GemRarity rarity : rarities) {
			double chance = rarity.getChance();
			if (Double.isFinite(chance) && chance > 0) {
				drawable.add(rarity);
				total += chance;
			}
		}
		if (!drawable.isEmpty()) {
			for (Gemstone gem : gems) {
				Names gemNames = forGem(gem, drawable, total);
				if (gemNames != null) {
					loaded.put(gem.getId(), gemNames);
				}
			}
		}
		names = loaded;
	}

	/** The rarity a gem with this stat is named after. A NaN stat (none was rolled) keeps the drawn rarity. */
	public static GemRarity name(Gemstone gem, GemRarity drawn, double stat) {
		Names gemNames = names.get(gem.getId());
		if (gemNames == null || Double.isNaN(stat)) {
			return drawn;
		}
		for (Cutoff cutoff : gemNames.cutoffs()) {
			if (stat >= cutoff.from()) {
				return cutoff.rarity();
			}
		}
		return gemNames.lowest();
	}

	private static Names forGem(Gemstone gem, List<GemRarity> drawable, double total) {
		// Every result the infusion can give: each drawable rarity with each face of the die, best stat first.
		TreeMap<Double, Double> chanceByStat = new TreeMap<>((a, b) -> Double.compare(b, a));
		String statId = null;
		for (GemRarity rarity : drawable) {
			GemStat block = block(gem, rarity);
			if (block == null || (statId != null && !statId.equalsIgnoreCase(block.getStatId()))) {
				warn("Gem '" + gem.getId() + "' needs one stat for every rarity to be named by its roll;"
						+ " it keeps the drawn rarity's name.");
				return null;
			}
			statId = block.getStatId();
			for (int natural = 1; natural <= 20; natural++) {
				double position = new D20Roll(natural, D20Roll.nameReferenceModifier).position(D20Roll.flawlessDc);
				chanceByStat.merge(D20Roll.gemStat(block, position), rarity.getChance() / total / 20, Double::sum);
			}
		}

		List<Cutoff> gemCutoffs = new ArrayList<>();
		StringBuilder summary = new StringBuilder("Gem '" + gem.getId() + "' names:");
		double share = 0;
		for (int i = drawable.size() - 1; i > 0; i--) {
			GemRarity rarity = drawable.get(i);
			share += rarity.getChance() / total;
			double from = cutoff(chanceByStat, share);
			gemCutoffs.add(new Cutoff(rarity, from));
			summary.append(' ').append(rarity.getId())
					.append(from == Double.POSITIVE_INFINITY ? " never" : " from " + from).append(',');
		}
		GemRarity lowest = drawable.getFirst();
		info(summary.append(' ').append(lowest.getId()).append(" below.").toString());
		return new Names(gemCutoffs, lowest);
	}

	/** The lowest stat whose results at or above it still fit in the share; a tie that would go over is left out. */
	private static double cutoff(TreeMap<Double, Double> chanceByStat, double share) {
		double cutoff = Double.POSITIVE_INFINITY;
		double atOrAbove = 0;
		for (Map.Entry<Double, Double> entry : chanceByStat.entrySet()) {
			atOrAbove += entry.getValue();
			if (atOrAbove <= share + 1e-9) {
				cutoff = entry.getKey();
			}
		}
		return cutoff;
	}

	private static GemStat block(Gemstone gem, GemRarity rarity) {
		for (GemStat block : gem.getStats()) {
			if (block.getId().equalsIgnoreCase(rarity.getId())) {
				return block;
			}
		}
		return null;
	}

	private static Logger logger() {
		return InfusionMain.plugin == null ? Logger.getLogger("GemInfusion") : InfusionMain.plugin.getLogger();
	}

	private static void info(String message) {
		logger().info(message);
	}

	private static void warn(String message) {
		logger().warning(message);
	}
}
