package net.tfminecraft.geminfusion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.geminfusion.goldsmith.GoldsmithLog;

/** One atomic snapshot per infusion table; rejected snapshots remain untouched. */
final class InfusionStationStore {
	private final Set<Path> rejected = new HashSet<>();

	Path folder() {
		return InfusionMain.plugin.getDataFolder().toPath().resolve("data/infusion-stations");
	}

	Path fileFor(Location location) {
		return folder().resolve(location.getWorld().getUID() + "_" + location.getBlockX() + "_"
				+ location.getBlockY() + "_" + location.getBlockZ() + ".yml");
	}

	List<InfusionBlock> loadAll() {
		List<InfusionBlock> stations = new ArrayList<>();
		rejected.clear();
		try {
			Files.createDirectories(folder());
			try (var paths = Files.list(folder())) {
				for (Path file : paths.filter(path -> path.toString().endsWith(".yml")).toList()) {
					try {
						stations.add(read(file));
					} catch (IOException | InvalidConfigurationException | IllegalArgumentException | NullPointerException ex) {
						rejected.add(file);
						GoldsmithLog.warn("Cannot load infusion table " + file.getFileName()
								+ "; retaining its saved gems: " + ex.getMessage());
					}
				}
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Cannot read infusion tables; refusing to accept gems", ex);
		}
		return stations;
	}

	boolean save(InfusionBlock station) {
		Path file = fileFor(station.getLocation());
		boolean replaced = false;
		try {
			if (rejected.contains(file)) throw new IOException("Repair the retained snapshot before using this table");
			YamlConfiguration yaml = new YamlConfiguration();
			Location location = station.getLocation();
			yaml.set("world", location.getWorld().getName());
			yaml.set("x", location.getBlockX());
			yaml.set("y", location.getBlockY());
			yaml.set("z", location.getBlockZ());
			yaml.set("particle-x", station.getParticleLocation().getX());
			yaml.set("particle-y", station.getParticleLocation().getY());
			yaml.set("particle-z", station.getParticleLocation().getZ());
			yaml.set("hits", station.getInfusionHits());
			yaml.set("gems", station.getCurrentItems().stream().map(Gemstone::getId).toList());
			for (int i = 0; i < station.pendingDrops.size(); i++) {
				InfusionBlock.Drop drop = station.pendingDrops.get(i);
				yaml.set("drops." + i + ".item", drop.item());
				yaml.set("drops." + i + ".announcement", drop.announcement());
			}
			Files.createDirectories(folder());
			Path temporary = Files.createTempFile(folder(), "infusion-", ".tmp");
			try {
				ByteBuffer bytes = ByteBuffer.wrap(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
				try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
					while (bytes.hasRemaining()) channel.write(bytes);
					channel.force(true);
				}
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				replaced = true;
				try (FileChannel directory = FileChannel.open(folder(), StandardOpenOption.READ)) {
					directory.force(true);
				}
			} finally {
				Files.deleteIfExists(temporary);
			}
			return true;
		} catch (IOException | IllegalArgumentException ex) {
			GoldsmithLog.warn("Cannot save infusion table " + file.getFileName() + ": " + ex.getMessage());
			// A post-rename sync failure must not roll back items already recorded on disk.
			return replaced;
		}
	}

	boolean delete(InfusionBlock station) {
		try {
			Files.deleteIfExists(fileFor(station.getLocation()));
			return true;
		} catch (IOException ex) {
			GoldsmithLog.warn("Cannot delete finished infusion table: " + ex.getMessage());
			return false;
		}
	}

	private InfusionBlock read(Path file) throws IOException, InvalidConfigurationException {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.load(file.toFile());
		World world = Objects.requireNonNull(Bukkit.getWorld(Objects.requireNonNull(yaml.getString("world"))), "Missing world");
		InfusionBlock station = new InfusionBlock();
		station.setLocation(new Location(world, yaml.getInt("x"), yaml.getInt("y"), yaml.getInt("z")));
		if (!file.equals(fileFor(station.getLocation()))) throw new IllegalArgumentException("Table location does not match filename");
		station.setParticleLocation(new Location(world, yaml.getDouble("particle-x"), yaml.getDouble("particle-y"), yaml.getDouble("particle-z")));
		int hits = yaml.getInt("hits", -1);
		if (hits < 0 || hits > 5) throw new IllegalArgumentException("Invalid infusion progress");
		station.setInfustionHits(hits);
		for (String id : yaml.getStringList("gems")) {
			Gemstone gem = ConfigLoader.loadedGems.stream().filter(candidate -> candidate.getId().equals(id)).findFirst().orElseThrow(
					() -> new IllegalArgumentException("Unknown gem " + id));
			station.addGem(gem);
		}
		if (station.currentGems.isEmpty() || station.currentGems.size() > 10) throw new IllegalArgumentException("Invalid gem count");
		if (yaml.isConfigurationSection("drops")) {
			for (String key : yaml.getConfigurationSection("drops").getKeys(false)) {
				station.pendingDrops.add(new InfusionBlock.Drop(
						Objects.requireNonNull(yaml.getItemStack("drops." + key + ".item"), "Missing infused gem"),
						yaml.getString("drops." + key + ".announcement", "")));
			}
		}
		if (station.pendingDrops.size() > station.currentGems.size()
				|| (hits < 5 && !station.pendingDrops.isEmpty())) throw new IllegalArgumentException("Invalid pending output");
		return station;
	}
}
