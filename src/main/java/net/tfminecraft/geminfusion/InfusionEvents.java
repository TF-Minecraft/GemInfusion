package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
import org.bukkit.inventory.EquipmentSlot;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithLog;
import net.tfminecraft.geminfusion.goldsmith.InfusedGemValidator;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;


public class InfusionEvents implements Listener{
	public List<InfusionBlock> currentStations = new ArrayList<InfusionBlock>();
	private final InfusionStationStore store;
	private final List<BukkitRunnable> deliveries = new ArrayList<>();

	public InfusionEvents() {
		this(new InfusionStationStore());
	}

	InfusionEvents(InfusionStationStore store) {
		this.store = store;
	}

	public void loadPersisted() {
		for (BukkitRunnable delivery : deliveries) delivery.cancel();
		deliveries.clear();
		currentStations.clear();
		currentStations.addAll(store.loadAll());
		for (InfusionBlock station : currentStations) {
			if (station.getInfusionHits() == 5) scheduleDrops(station);
		}
	}

	public void flush() {
		for (InfusionBlock station : currentStations) store.save(station);
	}

	public void shutdown() {
		for (BukkitRunnable delivery : deliveries) delivery.cancel();
		deliveries.clear();
		flush();
	}

	private boolean deposit(Player player, InfusionBlock station, Gemstone gem) {
		station.addGem(gem);
		if (!store.save(station)) {
			station.getCurrentItems().removeLast();
			player.sendMessage(ChatColor.RED + "This infusion table could not be saved. Contact staff; your gem remains in your hand.");
			return false;
		}
		ItemStack hand = player.getInventory().getItemInMainHand();
		hand.setAmount(hand.getAmount() - 1);
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	@EventHandler
	public void addGemEvent(PlayerInteractEvent e) {
		if (e.getHand() == EquipmentSlot.OFF_HAND) return;
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
		if(InfusedGemValidator.isReset(item)) {
			// Infusing again would wipe the roll; give the gem its infused look back instead.
			ItemStack restored = InfusedGemBuilder.restoreReset(item);
			if(restored == null) return;
			p.getInventory().setItemInMainHand(restored);
			p.sendMessage(ChatColor.YELLOW + "This gem already holds an infusion. Its glow returns.");
			return;
		}
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
					if(loc.equals(b.getLocation()) && b.getCurrentItems().size() >= 10) {
						p.sendMessage(ChatColor.RED + "You can only infuse 10 gems at a time!");
						return;
					}
					if(loc.equals(b.getLocation())) {
						exists = true;
						if (!deposit(p, b, gem)) return;
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
					if (!deposit(p, iBlock, gem)) return;
					p.sendTitle(ChatColor.LIGHT_PURPLE + "Added " + gem.getColour() + gem.getName(), ChatColor.GRAY + "Current Gem Amount: " + ChatColor.YELLOW + iBlock.getCurrentItems().size()+"/10", 1, 40, 20);
					iBlock.getLocation().getWorld().spawnParticle(Particle.ENCHANT, iBlock.getParticleLocation(), 60);
					p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 2);
					currentStations.add(iBlock);
				}
			}
		}
	}
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	@EventHandler
	public void infuseHitEvent(PlayerInteractEvent e) {
		if (e.getHand() == EquipmentSlot.OFF_HAND) return;
		if(!e.getAction().equals(Action.LEFT_CLICK_BLOCK)) return;
		Material block = e.getClickedBlock().getType();
		if(!ConfigLoader.stations.contains(block)) return;
		Location loc = e.getClickedBlock().getLocation();
		for(int i = 0; i<currentStations.size(); i++) {
			InfusionBlock b = currentStations.get(i);
			if(b.getLocation().equals(loc)) {
				e.setCancelled(true);
				if (b.getInfusionHits() >= 5) return;
				if(b.getCurrentItems().size() > 0) {
					Player p = e.getPlayer();
					ItemStack item = p.getInventory().getItemInMainHand();
					NBTItem nbt = NBTItem.get(item);
					if(nbt.hasType() == false) return;
					String itemType = ConfigLoader.infusionStaff.split("\\.")[0];
					String itemID = ConfigLoader.infusionStaff.split("\\.")[1];
					if(nbt.getType().equalsIgnoreCase(itemType) && nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(itemID)) {
						// Build once while the player's attributes are available; persist the rolled items.
						List<InfusionBlock.Drop> prepared = new ArrayList<>();
						if (b.getInfusionHits() >= 4) {
							try {
								for (Gemstone gem : b.getCurrentItems()) {
									GemRarity rarity = getRarity();
									ItemStack output = Objects.requireNonNull(getInfusedGem(p, gem, b.getCurrentItems().size(), rarity), "Cannot build infused gem");
									String announcement = rarity.shouldAnnounce()
											? "§e" + p.getName() + " just infused a " + rarity.getName() + " " + gem.getColour() + gem.getName() + "§e Gemstone" : "";
									prepared.add(new InfusionBlock.Drop(output, announcement));
								}
							} catch (RuntimeException invalidBatch) {
								GoldsmithLog.warn(invalidBatch.getMessage());
								p.sendMessage(ChatColor.RED + "Infusion could not be prepared. Contact staff; your gems remain on the bench.");
								return;
							}
						}
						b.setInfustionHits(b.getInfusionHits()+1);
						b.pendingDrops.addAll(prepared);
						if (!store.save(b)) {
							b.setInfustionHits(b.getInfusionHits()-1);
							b.pendingDrops.clear();
							p.sendMessage(ChatColor.RED + "Infusion progress could not be saved. Contact staff; your gems remain on the bench.");
							return;
						}
						p.sendTitle(ChatColor.LIGHT_PURPLE + "Infusing...", ChatColor.GREEN + "Progress: " + ChatColor.YELLOW + b.getInfusionHits() + "/5", 1, 40, 20);
						b.getLocation().getWorld().spawnParticle(Particle.FLAME, b.getParticleLocation(), 60);
						b.getLocation().getWorld().spawnParticle(Particle.CRIT, b.getParticleLocation(), 60);
						p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 0.2f*b.getInfusionHits());
						p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.2f, 0.2f*b.getInfusionHits());
						if(b.getInfusionHits() >= 5) {
							p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount()-1);
							b.getParticleLocation().getWorld().spawnEntity(b.getParticleLocation(), EntityType.LIGHTNING_BOLT);
							b.getLocation().getWorld().spawnParticle(Particle.FLAME, b.getParticleLocation(), 600);
							p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
							scheduleDrops(b);
						}
					}
				}
			}
		}
	}
	private void scheduleDrops(InfusionBlock station) {
		BukkitRunnable delivery = new BukkitRunnable() {
			@Override
			public void run() {
				// Retry failed checkpoints before delivering another item.
				if (!store.save(station)) return;
				if (station.pendingDrops.isEmpty()) {
					if (!store.delete(station)) return;
					currentStations.remove(station);
					deliveries.remove(this);
					cancel();
					return;
				}
				InfusionBlock.Drop output = station.pendingDrops.getFirst();
				Location dropLocation = station.getLocation().clone().add(0.5, 1.1, 0.5);
				Item dropped = dropLocation.getWorld().dropItem(dropLocation, output.item());
				station.pendingDrops.removeFirst();
				store.save(station);
				if (!output.announcement().isEmpty()) {
					for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(output.announcement());
				}
				dropped.setVelocity(new Vector((Math.random() - 0.5) * 0.2, 0.2 + Math.random() * 0.2, (Math.random() - 0.5) * 0.2));
				dropLocation.getWorld().playSound(dropLocation, Sound.ENTITY_GLOW_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
				dropLocation.getWorld().spawnParticle(Particle.FLAME, station.getParticleLocation(), 20);
			}
		};
		delivery.runTaskTimer(InfusionMain.plugin, 30L, 2L);
		deliveries.add(delivery);
	}

	public GemRarity getRarity() {
		return getRarity(Math.random());
	}

	GemRarity getRarity(double draw) {
		List<GemRarity> valid = new ArrayList<>();
		double largest = 0;
		for (GemRarity rarity : ConfigLoader.loadedRarities) {
			double weight = rarity.getChance();
			if (Double.isFinite(weight) && weight > 0) {
				valid.add(rarity);
				largest = Math.max(largest, weight);
			}
		}
		if (valid.isEmpty()) {
			throw new IllegalStateException("Gem infusion requires at least one finite positive rarity weight.");
		}
		// Scale before summing so individually valid large weights cannot overflow.
		double total = 0;
		for (GemRarity rarity : valid) total += rarity.getChance() / largest;
		double roll = draw * total;
		for (GemRarity rarity : valid) {
			roll -= rarity.getChance() / largest;
			if (roll < 0) return rarity;
		}
		return valid.getLast(); // Floating-point rounding at the upper boundary.
	}
	

	public ItemStack getInfusedGem(Player player, Gemstone gem, Integer amount, GemRarity r) {
		return InfusedGemBuilder.buildInfusedGem(gem, r, amount, player);
	}
}
