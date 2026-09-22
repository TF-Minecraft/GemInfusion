package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.manager.ItemManager;

public class Gemstone {
	public String Id;
	public String mmoitem;
	public String name;
	public Boolean locationSpecific;
	public String location;
	public String socketColour;
	public ChatColor socketNameColour;
	public ChatColor colour;
	public List<GemStat> stats = new ArrayList<GemStat>();
	
	//Setters
	public void setId(String Id) {
		this.Id = Id;
	}
	public void setMMOItem(String item) {
		this.mmoitem = item;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setLocationSpecific(Boolean b) {
		this.locationSpecific = b;
	}
	public void setLocation(String loc) {
		this.location = loc;
	}
	public void setSocketColour(String colour) {
		this.socketColour = colour;
	}
	public void setSocketNameColour(ChatColor colour) {
		this.socketNameColour = colour;
	}
	public void setColour(ChatColor colour) {
		this.colour = colour;
	}
	public void setStats(List<GemStat> list) {
		this.stats = list;
	}
	public void addStat(GemStat stat) {
		if (stat != null) {
			this.stats.add(stat);
		}
	}
	
	//Getters
	public String getId() {
		return this.Id;
	}
	public String getMMOItemString() {
		return this.mmoitem;
	}
	@SuppressWarnings("deprecation")
	public MMOItem getMMOItem() {
		String itemType = mmoitem.toString().split("\\.")[0];
		String itemID = mmoitem.toString().split("\\.")[1];
		ItemManager itemManager = MMOItems.plugin.getItems();
		return itemManager.getMMOItem(MMOItems.plugin.getTypes().get(itemType.toUpperCase()), itemID.toUpperCase());
	}
	public String getName() {
		return this.name;
	}
	public Boolean isLocationSpecific() {
		return this.locationSpecific;
	}
	public String getLocation() {
		return this.location;
	}
	public ChatColor getColour() {
		return colour;
	}
	public String getSocketColour() {
		return this.socketColour;
	}
	public ChatColor getSocketNameColour() {
		return this.socketNameColour;
	}
	public List<GemStat> getStats() {
		return this.stats;
	}
}
