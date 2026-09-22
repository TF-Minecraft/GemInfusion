package net.tfminecraft.geminfusion.goldsmith;

import java.util.logging.Logger;

import net.tfminecraft.geminfusion.InfusionMain;

public final class GoldsmithLog {

	private static final Logger FALLBACK = Logger.getLogger("GemInfusion");

	private GoldsmithLog() {
	}

	public static Logger get() {
		return InfusionMain.plugin == null ? FALLBACK : InfusionMain.plugin.getLogger();
	}

	public static void warn(String message) {
		get().warning(message);
	}

	public static void info(String message) {
		get().info(message);
	}
}
