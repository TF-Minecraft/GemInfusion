package net.tfminecraft.geminfusion;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.geminfusion.goldsmith.GoldsmithConfigLoader;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithHitLoader;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithHitTypeLoader;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithLog;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithMaterialLoader;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithMaterialTypeLoader;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithStationManager;
import net.tfminecraft.geminfusion.goldsmith.JewelryProjectLoader;
import net.tfminecraft.geminfusion.goldsmith.ProjectTierLoader;
import net.tfminecraft.geminfusion.goldsmith.QualityLoader;

public class InfusionMain extends JavaPlugin {
	public static InfusionMain plugin;
	public FileConfiguration config = getConfig();

	private final ConfigLoader loader = new ConfigLoader();
	private final InfusionEvents events = new InfusionEvents();
	private final CommandManager commands = new CommandManager();
	private final GoldsmithStationManager goldsmithStations = new GoldsmithStationManager();
	private final GoldsmithConfigLoader goldsmithConfig = new GoldsmithConfigLoader();
	private final GoldsmithHitTypeLoader goldsmithHitTypes = new GoldsmithHitTypeLoader();
	private final GoldsmithHitLoader goldsmithHits = new GoldsmithHitLoader();
	private final GoldsmithMaterialLoader goldsmithMaterials = new GoldsmithMaterialLoader();
	private final GoldsmithMaterialTypeLoader goldsmithMaterialTypes = new GoldsmithMaterialTypeLoader();
	private final ProjectTierLoader projectTiers = new ProjectTierLoader();
	private final QualityLoader qualities = new QualityLoader();
	private final JewelryProjectLoader jewelryProjects = new JewelryProjectLoader();

	@Override
	public void onEnable() {
		plugin = this;

		saveDefaultConfig();
		createGoldsmithFiles();
		reloadConfig();
		config = getConfig();
		loader.loadConfig(config);
		loadGoldsmithConfigs();

		getServer().getPluginManager().registerEvents(events, this);
		getServer().getPluginManager().registerEvents(goldsmithStations, this);
		if (Bukkit.getPluginManager().getPlugin("TLibs") != null) {
			getServer().getPluginManager().registerEvents(new GemSocketRebuildListener(), this);
			getServer().getPluginManager().registerEvents(new GemUnsocketSnapshotListener(), this);
		}
		getCommand(commands.cmd1).setExecutor(commands);
		getCommand(commands.cmd1).setTabCompleter(commands);
		goldsmithStations.loadPersisted();
		getServer().getScheduler().runTaskTimer(this, () -> goldsmithStations.flush(false), 20L * 60, 20L * 60);
		GoldsmithLog.info("Loaded " + JewelryProjectLoader.get().size() + " goldsmithing projects");
	}

	@Override
	public void onDisable() {
		goldsmithStations.flush(true);
	}

	public GoldsmithStationManager getGoldsmithStations() {
		return goldsmithStations;
	}

	public void reloadConfigCommand() {
		goldsmithStations.flush(true);
		goldsmithStations.clear();
		reloadConfig();
		config = getConfig();
		loader.loadConfig(config);
		loadGoldsmithConfigs();
		goldsmithStations.loadPersisted();
	}

	public void reloadConfigPCommand(Player p) {
		p.sendMessage(ChatColor.GREEN + "[GemInfusion]" + ChatColor.YELLOW + " Reloading plugin...");
		reloadConfigCommand();
		p.sendMessage(ChatColor.GREEN + "[GemInfusion]" + ChatColor.YELLOW + " Reloading complete!");
		p.sendMessage(ChatColor.GRAY + "Goldsmithing projects: " + JewelryProjectLoader.get().size());
	}

	private void loadGoldsmithConfigs() {
		File folder = new File(getDataFolder(), "goldsmithing");
		goldsmithHitTypes.load(new File(folder, "hit-types.yml"));
		goldsmithHits.load(new File(folder, "hits.yml"));
		goldsmithMaterialTypes.load(new File(folder, "material-types.yml"));
		goldsmithMaterials.load(new File(folder, "materials.yml"));
		projectTiers.load(new File(folder, "tiers.yml"));
		qualities.load(new File(folder, "qualities.yml"));
		jewelryProjects.load(new File(folder, "projects.yml"));
		goldsmithConfig.load(new File(getDataFolder(), "goldsmithing.yml"));
	}

	private void createGoldsmithFiles() {
		String[] files = {
				"goldsmithing.yml",
				"goldsmithing/hit-types.yml",
				"goldsmithing/hits.yml",
				"goldsmithing/materials.yml",
				"goldsmithing/material-types.yml",
				"goldsmithing/tiers.yml",
				"goldsmithing/qualities.yml",
				"goldsmithing/projects.yml"
		};
		for (String s : files) {
			File out = new File(getDataFolder(), s);
			if (!out.exists()) {
				out.getParentFile().mkdirs();
				saveResource(s, false);
			}
		}
	}
}
