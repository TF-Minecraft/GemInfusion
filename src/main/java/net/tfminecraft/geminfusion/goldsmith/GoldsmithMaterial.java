package net.tfminecraft.geminfusion.goldsmith;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class GoldsmithMaterial {

	private final String id;
	private final String name;
	private final String path;
	private final String type;
	private final LinkedHashMap<GoldsmithHit, Integer> hits = new LinkedHashMap<>();

	public GoldsmithMaterial(String key, ConfigurationSection config) {
		this.id = key;
		this.name = StringFormatter.formatHex(config.getString("name", key));
		this.path = config.getString("path");
		this.type = config.getString("type", "gold");

		for (String s : config.getStringList("hits")) {
			String[] parts = split(s);
			if (parts == null) {
				warn("hit entry '" + s + "' is not in the id.amount format");
				continue;
			}
			GoldsmithHit hit = GoldsmithHitLoader.getByString(parts[0]);
			if (hit == null) {
				warn("unknown hit '" + parts[0] + "'");
				continue;
			}
			hits.merge(hit, Integer.parseInt(parts[1]), Integer::sum);
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
		GoldsmithLog.warn("Material '" + id + "': " + message + ", skipping entry.");
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public String getType() {
		return type;
	}

	public Map<GoldsmithHit, Integer> getHits() {
		return Collections.unmodifiableMap(hits);
	}
}
