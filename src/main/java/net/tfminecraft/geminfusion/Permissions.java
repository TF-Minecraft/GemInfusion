package net.tfminecraft.geminfusion;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.geminfusion.goldsmith.GoldsmithCache;

public class Permissions {
	public static final String ADMIN = "geminfusion.admin";

	public static boolean isAdmin(final CommandSender commandSender) {
		return commandSender.hasPermission(ADMIN);
	}

	public static boolean canUseGoldsmith(CommandSender sender) {
		if (isAdmin(sender)) return true;
		String perm = GoldsmithCache.permission;
		if (perm == null || perm.isBlank()) return true;
		return sender.hasPermission(perm);
	}

	public static boolean requireAdmin(CommandSender sender) {
		if (isAdmin(sender)) return true;
		sender.sendMessage("§cYou do not have access to this command!");
		return false;
	}

	public static boolean requireUseGoldsmith(Player player) {
		if (canUseGoldsmith(player)) return true;
		player.sendMessage("§cYou do not have permission to use goldsmithing.");
		return false;
	}
}
