package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;


public class InfusionEvents implements Listener{
	public List<InfusionBlock> currentStations = new ArrayList<InfusionBlock>();
	@EventHandler
	public void addGemEvent(PlayerInteractEvent e) {
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
		Material block = e.getClickedBlock().getType();
		if(!ConfigLoader.stations.contains(block)) return;
		Location loc = e.getClickedBlock().getLocation();
		Location particleLoc = loc;
		if(ConfigLoader.useLocations == true) {
			Boolean correctLoc = false;
			for(String s : ConfigLoader.locations) {
				Double xPos = Double.parseDouble(s.split("\\,")[0]);
				Double yPos = Double.parseDouble(s.split("\\,")[1]);
				Double zPos = Double.parseDouble(s.split("\\,")[2]);
				Location configLoc = new Location(e.getPlayer().getWorld(), xPos, yPos, zPos);
				if(configLoc.equals(loc)) {
					correctLoc = true;
					particleLoc = new Location(e.getPlayer().getWorld(), xPos+0.5, yPos+1.0, zPos+0.5);
				}
			}
			if(correctLoc == false) return;
		}
		e.setCancelled(true);
		Player p = e.getPlayer();
		ItemStack item = p.getInventory().getItemInMainHand();
		NBTItem nbt = NBTItem.get(item);
		if(nbt.hasType() == false) return;
		if(!nbt.getString("MMOITEMS_DISPLAYED_TYPE").equalsIgnoreCase("Blank Gemstone")) return;
		for(Gemstone gem : ConfigLoader.loadedGems) {
			if(nbt.getType().equalsIgnoreCase(gem.getMMOItemString().split("\\.")[0]) && nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(gem.getMMOItem().getId())) {
				if(gem.isLocationSpecific() == true) {
					Double xPos = Double.parseDouble(gem.getLocation().split("\\,")[0]);
					Double yPos = Double.parseDouble(gem.getLocation().split("\\,")[1]);
					Double zPos = Double.parseDouble(gem.getLocation().split("\\,")[2]);
					Location configLoc = new Location(e.getPlayer().getWorld(), xPos, yPos, zPos);
					if(!loc.equals(configLoc)) {
						p.sendMessage(ChatColor.RED + "This Gem Type cannot be infused here!");
						return;
					}
				}
				Boolean exists = false;
				for(InfusionBlock b : currentStations) {
					if(loc.equals(b.getLocation()) && b.getInfusionHits() > 0) {
						p.sendMessage(ChatColor.RED + "Infusion has already started, you cannot add more gems at this point!");
						return;
					}
					if(b.getCurrentItems().size() >= 10) {
						p.sendMessage(ChatColor.RED + "You can only infuse 10 gems at a time!");
						return;
					}
					if(loc.equals(b.getLocation())) {
						exists = true;
						p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount()-1);
						b.addGem(gem);
						p.sendTitle(ChatColor.LIGHT_PURPLE + "Added " + gem.getColour() + gem.getName(), ChatColor.GRAY + "Current Gem Amount: " + ChatColor.YELLOW + b.getCurrentItems().size()+"/10", 1, 40, 20);
						b.getLocation().getWorld().spawnParticle(Particle.ENCHANT, b.getParticleLocation(), 60);
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 2);
					}
				}
				if(exists == false) {
					InfusionBlock iBlock = new InfusionBlock();
					iBlock.setLocation(loc);
					iBlock.setParticleLocation(particleLoc);
					iBlock.setInfustionHits(0);
					p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount()-1);
					iBlock.addGem(gem);
					p.sendTitle(ChatColor.LIGHT_PURPLE + "Added " + gem.getColour() + gem.getName(), ChatColor.GRAY + "Current Gem Amount: " + ChatColor.YELLOW + iBlock.getCurrentItems().size()+"/10", 1, 40, 20);
					iBlock.getLocation().getWorld().spawnParticle(Particle.ENCHANT, iBlock.getParticleLocation(), 60);
					p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 2);
					currentStations.add(iBlock);
				}
			}
		}
	}
	@EventHandler
	public void infuseHitEvent(PlayerInteractEvent e) {
		if(!e.getAction().equals(Action.LEFT_CLICK_BLOCK)) return;
		Material block = e.getClickedBlock().getType();
		if(!ConfigLoader.stations.contains(block)) return;
		Location loc = e.getClickedBlock().getLocation();
		for(int i = 0; i<currentStations.size(); i++) {
			InfusionBlock b = currentStations.get(i);
			if(b.getLocation().equals(loc)) {
				e.setCancelled(true);
				if(b.getCurrentItems().size() > 0) {
					Player p = e.getPlayer();
					ItemStack item = p.getInventory().getItemInMainHand();
					NBTItem nbt = NBTItem.get(item);
					if(nbt.hasType() == false) return;
					String itemType = ConfigLoader.infusionStaff.split("\\.")[0];
					String itemID = ConfigLoader.infusionStaff.split("\\.")[1];
					if(nbt.getType().equalsIgnoreCase(itemType) && nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(itemID)) {
						b.setInfustionHits(b.getInfusionHits()+1);
						p.sendTitle(ChatColor.LIGHT_PURPLE + "Infusing...", ChatColor.GREEN + "Progress: " + ChatColor.YELLOW + b.getInfusionHits() + "/5", 1, 40, 20);
						b.getLocation().getWorld().spawnParticle(Particle.FLAME, b.getParticleLocation(), 60);
						b.getLocation().getWorld().spawnParticle(Particle.CRIT, b.getParticleLocation(), 60);
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 0.2f*b.getInfusionHits());
						p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.2f, 0.2f*b.getInfusionHits());
						if(b.getInfusionHits() >= 5) {
							currentStations.remove(b);
							p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount()-1);
							b.getParticleLocation().getWorld().spawnEntity(b.getParticleLocation(), EntityType.LIGHTNING_BOLT);
							b.getLocation().getWorld().spawnParticle(Particle.FLAME, b.getParticleLocation(), 600);
							p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
							new BukkitRunnable()
							{
								Integer i = 0;
								public void run()
								   {
									if(i < b.getCurrentItems().size()) {
										Gemstone gem = b.getCurrentItems().get(i);
										GemRarity r = getRarity();
										if(r.shouldAnnounce()) {
											for(Player player : Bukkit.getOnlinePlayers()) {
												player.sendMessage("§e"+p.getName()+" just infused a "+r.getName()+" "+gem.getColour()+gem.getName()+"§e Gemstone");
											}
										}
										ItemStack dropGem = getInfusedGem(p, gem, b.getCurrentItems().size(), r);
										Location dropLocation = b.getLocation().clone().add(0.5, 1.1, 0.5); // Center-top of the block
										Item dropped = p.getWorld().dropItem(dropLocation, dropGem);

										// Random upward and outward velocity
										Vector velocity = new Vector(
											(Math.random() - 0.5) * 0.2,  // X: between -0.3 and +0.3
											0.2 + Math.random() * 0.2,    // Y: between 0.2 and 0.4
											(Math.random() - 0.5) * 0.2   // Z: between -0.3 and +0.3
										);
										dropped.setVelocity(velocity);
										p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GLOW_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
										b.getLocation().getWorld().spawnParticle(Particle.FLAME, b.getParticleLocation(), 20);
									} else {
										this.cancel();
									}
									i++;
								   }
							}.runTaskTimer(InfusionMain.plugin, 30L, 2L);
						}
					}
				}
			}
		}
	}
	public GemRarity getRarity() {
		Double maxWeight = 0.0;
		for(GemRarity r : ConfigLoader.loadedRarities) {
			maxWeight = maxWeight+r.getChance();
		}
		Integer dropped = 0;
		GemRarity rarity = null;
		while(dropped < 1) {
			Double random = Math.random();
			Double previous = 0.0;
			for(GemRarity r : ConfigLoader.loadedRarities) {
				if(maxWeight <= 0) {
					maxWeight = 1.0;
				}
				Double chance = r.getChance() / maxWeight;
				Double max = previous+chance;
				if(random <= max && random > previous) {
					dropped++;
					rarity = r;
				}
				previous = max;
			}
		}
		return rarity;
	}
	
	public ItemStack getInfusedGem(Player player, Gemstone gem, Integer amount, GemRarity r) {
		return InfusedGemBuilder.buildInfusedGem(gem, r, amount, player);
	}
}
