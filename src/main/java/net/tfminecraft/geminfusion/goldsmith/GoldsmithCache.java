package net.tfminecraft.geminfusion.goldsmith;

import java.util.Map;
import java.util.Locale;

public final class GoldsmithCache {

	public static String station;
	public static String brandingTool;
	public static String permission;
	public static double minHitPercent = 0.40;
	public static double hitOvershootWarnPercent = 30.0;
	public static String hitOvershootWarnMessage = "§cYou have worked this piece too much";
	public static double jewelryGemStatBoost = 10.0;
	public static Map<String, Double> jewelryGemStatBoostChances = Map.of(
			"common", 5.0,
			"rare", 15.0,
			"epic", 30.0,
			"legendary", 50.0);

	public static double jewelryGemStatBoostChance(String rarityId) {
		if (rarityId == null) return 0;
		return jewelryGemStatBoostChances.getOrDefault(rarityId.toLowerCase(Locale.ROOT), 0.0);
	}

	private GoldsmithCache() {
	}
}
