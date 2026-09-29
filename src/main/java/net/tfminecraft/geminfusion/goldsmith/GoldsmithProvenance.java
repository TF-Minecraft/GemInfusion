package net.tfminecraft.geminfusion.goldsmith;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.tfminecraft.geminfusion.PDCKeys;

/**
 * Records the materials a finished jewelry piece was actually made from, as item path to amount.
 * A slot accepts any material of its type, so this can differ from the project recipe.
 */
public final class GoldsmithProvenance {

	private static final Gson GSON = new Gson();
	private static final Type INPUTS_TYPE = new TypeToken<LinkedHashMap<String, Integer>>() {
	}.getType();

	private GoldsmithProvenance() {
	}

	public static void stamp(ItemStack item, Map<GoldsmithMaterial, Integer> deposited) {
		LinkedHashMap<String, Integer> inputs = new LinkedHashMap<>();
		for (Map.Entry<GoldsmithMaterial, Integer> entry : deposited.entrySet()) {
			inputs.merge(entry.getKey().getPath(), entry.getValue(), Integer::sum);
		}
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(PDCKeys.goldsmithInputs(), PersistentDataType.STRING,
				GSON.toJson(inputs));
		item.setItemMeta(meta);
	}

	/**
	 * @return deposited material paths and amounts, or null for jewelry made before inputs were recorded
	 */
	public static Map<String, Integer> read(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		String json = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.goldsmithInputs(),
				PersistentDataType.STRING);
		if (json == null) {
			return null;
		}
		try {
			return GSON.fromJson(json, INPUTS_TYPE);
		} catch (JsonParseException ex) {
			return null;
		}
	}
}
