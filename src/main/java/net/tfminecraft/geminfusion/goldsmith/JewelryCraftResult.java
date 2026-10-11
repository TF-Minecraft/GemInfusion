package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.inventory.ItemStack;

public final class JewelryCraftResult {

	private final ItemStack item;
	private final double recipePercent;
	private final double hitPercent;
	private final double finishedTotal;
	private final double statCarryPercent;
	private final Quality quality;

	public JewelryCraftResult(ItemStack item, double recipePercent, double hitPercent, double finishedTotal,
			double statCarryPercent, Quality quality) {
		this.item = item;
		this.recipePercent = recipePercent;
		this.hitPercent = hitPercent;
		this.finishedTotal = finishedTotal;
		this.statCarryPercent = statCarryPercent;
		this.quality = quality;
	}

	public ItemStack getItem() {
		return item;
	}

	public double getRecipePercent() {
		return recipePercent;
	}

	public double getHitPercent() {
		return hitPercent;
	}

	public double getFinishedTotal() {
		return finishedTotal;
	}

	public double getStatCarryPercent() {
		return statCarryPercent;
	}

	public Quality getQuality() {
		return quality;
	}
}
