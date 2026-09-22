package net.tfminecraft.geminfusion.goldsmith;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class JewelryProject {

	private final String id;
	private final String name;
	private final String item;
	private final String tierId;
	private final boolean requiresGem;
	private final LinkedHashMap<GoldsmithMaterial, Integer> recipe = new LinkedHashMap<>();

	public JewelryProject(String key, ConfigurationSection config) {
		this.id = key;
		this.name = StringFormatter.formatHex(config.getString("name", key));
		this.item = config.getString("item");
		this.requiresGem = config.getInt("gem", 0) > 0;

		String tier = config.getString("tier");
		if (tier == null || tier.isBlank()) {
			GoldsmithLog.warn("Project '" + id + "' has no tier set, defaulting to greater.");
			this.tierId = "greater";
		} else {
			this.tierId = tier.toLowerCase();
		}

		for (String s : config.getStringList("recipe")) {
			String[] parts = split(s);
			if (parts == null) {
				warn("recipe entry '" + s + "' is not in the id.amount format");
				continue;
			}
			GoldsmithMaterial material = GoldsmithMaterialLoader.getByString(parts[0]);
			if (material == null) {
				warn("unknown material '" + parts[0] + "'");
				continue;
			}
			recipe.merge(material, Integer.parseInt(parts[1]), Integer::sum);
		}
	}

	private static String[] split(String entry) {
		if (entry == null) return null;
		int i = entry.lastIndexOf('.');
		if (i <= 0 || i == entry.length() - 1) return null;
		String amount = entry.substring(i + 1);
		try {
			if (Integer.parseInt(amount) <= 0) return null;
		} catch (NumberFormatException e) {
			return null;
		}
		return new String[] { entry.substring(0, i), amount };
	}

	private void warn(String message) {
		GoldsmithLog.warn("Project '" + id + "': " + message + ", skipping entry.");
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getItem() {
		return item;
	}

	public String getTierId() {
		return tierId;
	}

	public double getTierMultiplier() {
		return ProjectTierLoader.getMultiplier(tierId);
	}

	public boolean requiresGem() {
		return requiresGem;
	}

	public Map<GoldsmithMaterial, Integer> getRecipe() {
		return Collections.unmodifiableMap(recipe);
	}

	public Map<String, Integer> getMaterialsByType() {
		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		for (Map.Entry<GoldsmithMaterial, Integer> e : recipe.entrySet()) {
			map.merge(e.getKey().getType(), e.getValue(), Integer::sum);
		}
		return map;
	}
}
