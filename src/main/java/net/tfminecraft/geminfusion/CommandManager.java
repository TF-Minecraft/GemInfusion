package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.geminfusion.goldsmith.GoldsmithStation;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithStationManager;
import net.tfminecraft.geminfusion.goldsmith.JewelryProject;
import net.tfminecraft.geminfusion.goldsmith.JewelryProjectLoader;

public class CommandManager implements CommandExecutor, TabCompleter {

	public String cmd1 = "geminfusion";
	private static final List<String> SUB_COMMANDS = Arrays.asList("reload", "select");

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase(cmd1)) return false;

		if (args.length == 0) {
			sender.sendMessage("§e/geminfusion reload");
			sender.sendMessage("§e/geminfusion select <projectId>");
			return true;
		}

		if (args[0].equalsIgnoreCase("reload")) {
			if (!Permissions.requireAdmin(sender)) return true;
			if (sender instanceof Player p) {
				InfusionMain.plugin.reloadConfigPCommand(p);
			} else {
				InfusionMain.plugin.reloadConfigCommand();
			}
			return true;
		}

		if (args[0].equalsIgnoreCase("select")) {
			if (!Permissions.requireAdmin(sender)) return true;
			if (!(sender instanceof Player p)) {
				sender.sendMessage("§cOnly players can select a project on a bench.");
				return true;
			}
			if (!Permissions.requireUseGoldsmith(p)) return true;
			if (args.length < 2) {
				p.sendMessage("§cUsage: /geminfusion select <projectId>");
				return true;
			}
			handleSelect(p, args[1]);
			return true;
		}

		sender.sendMessage("§e/geminfusion reload");
		sender.sendMessage("§e/geminfusion select <projectId>");
		return true;
	}

	private void handleSelect(Player p, String projectId) {
		JewelryProject project = JewelryProjectLoader.getByString(projectId);
		if (project == null) {
			p.sendMessage("§cUnknown project '" + projectId + "'.");
			return;
		}

		Block target = p.getTargetBlockExact(6);
		GoldsmithStationManager stations = InfusionMain.plugin.getGoldsmithStations();
		if (target == null || !stations.isGoldsmithStation(target)) {
			p.sendMessage("§cLook at a goldsmithing table within 6 blocks.");
			return;
		}

		GoldsmithStation station = stations.getOrCreate(target.getLocation());
		if (station.hasProject()) {
			p.sendMessage("§cThis bench already has a project. SHIFT + LEFT CLICK with the branding tool to cancel first.");
			return;
		}

		station.setProject(project);
		stations.markDirty();
		p.sendMessage("§aSelected " + project.getName() + " §aas the current goldsmithing project");
		p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 2f);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase(cmd1)) return null;

		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String s : SUB_COMMANDS) {
				if (s.startsWith(prefix)) out.add(s);
			}
			return out;
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
			if (!Permissions.isAdmin(sender)) return new ArrayList<>();
			String prefix = args[1].toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			for (String id : JewelryProjectLoader.get().keySet()) {
				if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(id);
				if (out.size() >= 40) break;
			}
			return out;
		}

		return new ArrayList<>();
	}
}
