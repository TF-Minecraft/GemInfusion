package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class InfusionBlock {
	public Location loc;
	public boolean token;
	public Location particleLoc;
	public Integer infusionHits;
	public List<Gemstone> currentGems = new ArrayList<Gemstone>();
	public List<Drop> pendingDrops = new ArrayList<>();

	public record Drop(ItemStack item, String announcement) {}
	
	//Setters
	public void setToken(boolean b) {
		this.token = b;
	}
	public void setLocation(Location loc) {
		this.loc = loc;
	}
	public void setParticleLocation(Location loc) {
		this.particleLoc = loc;
	}
	public void setInfustionHits(Integer hits) {
		this.infusionHits = hits;
	}
	public void setCurrentGems(List<Gemstone> gems) {
		this.currentGems = gems;
	}
	public void addGem(Gemstone gem) {
		this.currentGems.add(gem);
	}
	
	//Getters
	public boolean hasToken() {
		return this.token;
	}
	public Location getLocation() {
		return this.loc;
	}
	public Location getParticleLocation() {
		return this.particleLoc;
	}
	public Integer getInfusionHits() {
		return this.infusionHits;
	}
	public List<Gemstone> getCurrentItems() {
		return this.currentGems;
	}
}
