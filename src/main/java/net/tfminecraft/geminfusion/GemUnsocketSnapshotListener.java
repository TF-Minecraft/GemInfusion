package net.tfminecraft.geminfusion;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;

public class GemUnsocketSnapshotListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public void onUnsocketInventorySnapshot(UnsocketGemStoneEvent event) {
		UnsocketInventorySnapshot.capture(event.getPlayer());
	}
}
