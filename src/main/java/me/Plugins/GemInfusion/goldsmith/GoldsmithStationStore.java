package me.Plugins.GemInfusion.goldsmith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.Plugins.GemInfusion.InfusionMain;
import me.Plugins.TLibs.Objects.Utils.IntCounter;

/**
 * One JSON file per smithing table, named {@code world_x_y_z.json}.
 * Finished tables disappear on the next rewrite of this folder.
 */
public final class GoldsmithStationStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private GoldsmithStationStore() {
	}

	public static File folder() {
		return new File(InfusionMain.plugin.getDataFolder(), "data/goldsmith-stations");
	}

	public static String fileName(Location loc) {
		String world = loc.getWorld() == null ? "unknown" : loc.getWorld().getName();
		String safe = world.replaceAll("[^a-zA-Z0-9._-]", "_");
		return safe + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + ".json";
	}

	public static File fileFor(Location loc) {
		return new File(folder(), fileName(loc));
	}

	public static void delete(Location loc) {
		if (loc == null) return;
		File file = fileFor(loc);
		if (file.exists() && !file.delete()) {
			GoldsmithLog.warn("Failed to delete goldsmith station file " + file.getName());
		}
	}

	/** Writes every in-progress table and deletes JSON files that no longer match. */
	public static void saveAll(Collection<GoldsmithStation> stations) {
		File dir = folder();
		if (!dir.exists()) dir.mkdirs();

		Set<String> keep = new HashSet<>();
		if (stations != null) {
			for (GoldsmithStation station : stations) {
				if (station == null || !station.hasProject() || station.getLoc() == null) continue;
				File file = fileFor(station.getLoc());
				keep.add(file.getName());
				write(station, file);
			}
		}

		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (!file.isFile() || !file.getName().endsWith(".json")) continue;
			if (!keep.contains(file.getName()) && !file.delete()) {
				GoldsmithLog.warn("Failed to delete leftover goldsmith station file " + file.getName());
			}
		}
	}

	public static List<GoldsmithStation> loadAll() {
		List<GoldsmithStation> out = new ArrayList<>();
		File dir = folder();
		if (!dir.exists()) {
			dir.mkdirs();
			return out;
		}
		File[] files = dir.listFiles();
		if (files == null) return out;
		for (File file : files) {
			if (!file.isFile() || !file.getName().endsWith(".json")) continue;
			GoldsmithStation station = loadFile(file);
			if (station != null) out.add(station);
		}
		return out;
	}

	private static void write(GoldsmithStation station, File file) {
		StationData data = toData(station);
		if (data == null) return;
		file.getParentFile().mkdirs();
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			GSON.toJson(data, writer);
		} catch (IOException ex) {
			GoldsmithLog.warn("Failed to save goldsmith station " + file.getName() + ": " + ex.getMessage());
		}
	}

	private static GoldsmithStation loadFile(File file) {
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			StationData data = GSON.fromJson(reader, StationData.class);
			if (data == null || data.world == null || data.project == null) {
				GoldsmithLog.warn("Invalid goldsmith station file " + file.getName() + ", leaving it on disk.");
				return null;
			}
			World world = Bukkit.getWorld(data.world);
			if (world == null) {
				GoldsmithLog.warn("Goldsmith station file " + file.getName() + " world '" + data.world
						+ "' is missing, leaving it on disk.");
				return null;
			}
			JewelryProject project = JewelryProjectLoader.getByString(data.project);
			if (project == null) {
				GoldsmithLog.warn("Goldsmith station file " + file.getName() + " unknown project '" + data.project
						+ "', leaving it on disk.");
				return null;
			}
			Location loc = GoldsmithStationManager.key(new Location(world, data.x, data.y, data.z));
			GoldsmithStation station = new GoldsmithStation(loc);
			station.setProject(project);
			station.applySavedProgress(data.materials, data.hits, decodeItems(data.deposited), decodeItem(data.gem),
					data.overworkWarned);
			return station;
		} catch (IOException ex) {
			GoldsmithLog.warn("Failed to read goldsmith station file " + file.getName() + ": " + ex.getMessage());
			return null;
		}
	}

	private static StationData toData(GoldsmithStation station) {
		Location loc = station.getLoc();
		if (loc.getWorld() == null || station.getProject() == null) return null;
		StationData data = new StationData();
		data.world = loc.getWorld().getName();
		data.x = loc.getBlockX();
		data.y = loc.getBlockY();
		data.z = loc.getBlockZ();
		data.project = station.getProject().getId();
		data.materials = new LinkedHashMap<>();
		for (Map.Entry<GoldsmithMaterial, Integer> e : station.getDepositedByMaterial().entrySet()) {
			data.materials.put(e.getKey().getId(), e.getValue());
		}
		data.hits = new LinkedHashMap<>();
		for (Map.Entry<GoldsmithHit, IntCounter> e : station.getHits().entrySet()) {
			data.hits.put(e.getKey().getId(), e.getValue().getCurrent());
		}
		data.deposited = encodeItems(station.getDeposited());
		data.gem = encodeItem(station.getGem());
		data.overworkWarned = station.isOverworkWarned();
		return data;
	}

	private static List<String> encodeItems(List<ItemStack> items) {
		List<String> out = new ArrayList<>();
		if (items == null) return out;
		for (ItemStack item : items) {
			String encoded = encodeItem(item);
			if (encoded != null) out.add(encoded);
		}
		return out;
	}

	private static List<ItemStack> decodeItems(List<String> raw) {
		List<ItemStack> out = new ArrayList<>();
		if (raw == null) return out;
		for (String encoded : raw) {
			ItemStack item = decodeItem(encoded);
			if (item != null) out.add(item);
		}
		return out;
	}

	private static String encodeItem(ItemStack item) {
		if (item == null) return null;
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
			out.writeObject(item);
			out.flush();
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (IOException ex) {
			GoldsmithLog.warn("Failed to encode goldsmith item: " + ex.getMessage());
			return null;
		}
	}

	private static ItemStack decodeItem(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(raw));
				BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
			Object value = in.readObject();
			return value instanceof ItemStack stack ? stack : null;
		} catch (IOException | ClassNotFoundException | IllegalArgumentException ex) {
			GoldsmithLog.warn("Failed to decode goldsmith item: " + ex.getMessage());
			return null;
		}
	}

	static final class StationData {
		String world;
		int x;
		int y;
		int z;
		String project;
		Map<String, Integer> materials = new HashMap<>();
		Map<String, Integer> hits = new HashMap<>();
		List<String> deposited = new ArrayList<>();
		String gem;
		boolean overworkWarned;
	}
}
