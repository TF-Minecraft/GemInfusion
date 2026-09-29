package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.stat.data.*;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;
import net.tfminecraft.tlibs.socket.GemSocketsNbtEditor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class SocketIntegrationTest {
  ServerMock server;
  InfusionMain previous;
  Player player;
  io.lumine.mythic.lib.MythicLib previousLib;
  net.Indyuce.mmoitems.MMOItems previousMmo;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    previousLib = io.lumine.mythic.lib.MythicLib.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    previousMmo = net.Indyuce.mmoitems.MMOItems.plugin;
    net.Indyuce.mmoitems.MMOItems.plugin =
        mock(net.Indyuce.mmoitems.MMOItems.class, RETURNS_DEEP_STUBS);
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.namespace()).thenReturn("geminfusion");
    player = server.addPlayer();
  }

  @AfterEach
  void cleanup() {
    UnsocketInventorySnapshot.clear(player);
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedRarities.clear();
    InfusionMain.plugin = previous;
    io.lumine.mythic.lib.MythicLib.plugin = previousLib;
    net.Indyuce.mmoitems.MMOItems.plugin = previousMmo;
    MockBukkit.unmock();
  }

  ItemStack tagged(Material type) {
    ItemStack s = new ItemStack(type);
    var m = s.getItemMeta();
    m.setDisplayName("Original");
    s.setItemMeta(m);
    return s;
  }

  @Test
  void pdcRarityAndSocketMapsRoundtripMergeRemoveAndProtectViews() {
    ItemStack host = tagged(Material.DIAMOND_SWORD),
        other = tagged(Material.DIAMOND_SWORD),
        plain = new ItemStack(Material.DIAMOND);
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    assertNull(GemRarityPdc.read(null));
    assertNull(GemRarityPdc.read(plain));
    ItemStack withoutMeta = mock(ItemStack.class);
    when(withoutMeta.hasItemMeta()).thenReturn(false);
    assertNull(GemRarityPdc.read(withoutMeta));
    assertTrue(SocketRarityStore.read(withoutMeta).isEmpty());
    GemRarityPdc.write(null, "rare");
    GemRarityPdc.write(host, null);
    GemRarityPdc.write(plain, "rare");
    GemRarityPdc.write(host, "rare");
    assertEquals("rare", GemRarityPdc.read(host));
    assertTrue(SocketRarityStore.read(null).isEmpty());
    assertTrue(SocketRarityStore.read(plain).isEmpty());
    assertTrue(SocketRarityStore.read(host).isEmpty());
    var meta = host.getItemMeta();
    meta.getPersistentDataContainer().set(PDCKeys.socketRarities(), PersistentDataType.STRING, " ");
    host.setItemMeta(meta);
    assertTrue(SocketRarityStore.read(host).isEmpty());
    SocketRarityStore.put(host, first, "rare");
    SocketRarityStore.put(other, first, "common");
    SocketRarityStore.put(other, second, "epic");
    SocketRarityStore.mergeOnto(host, other);
    assertEquals(
        Map.of(first.toString(), "common", second.toString(), "epic"),
        SocketRarityStore.read(host));
    assertEquals("common", SocketRarityStore.get(host, first));
    assertThrows(
        UnsupportedOperationException.class,
        () -> SocketRarityStore.unmodifiableView(host).clear());
    SocketRarityStore.remove(host, first);
    assertNull(SocketRarityStore.get(host, first));
    SocketRarityStore.remove(host, first);
    SocketRarityStore.remove(host, second);
    assertTrue(SocketRarityStore.read(host).isEmpty());
    SocketRarityStore.write(host, null);
    SocketRarityStore.write(null, Map.of());
    SocketRarityStore.write(new ItemStack(Material.AIR), Map.of());
    SocketRarityStore.mergeOnto(null, host);
    SocketRarityStore.mergeOnto(host, null);
    SocketRarityStore.put(null, first, "rare");
    SocketRarityStore.put(host, null, "rare");
    SocketRarityStore.put(host, first, null);
    SocketRarityStore.remove(null, first);
    SocketRarityStore.remove(host, null);
    assertNull(SocketRarityStore.get(null, first));
    assertNull(SocketRarityStore.get(host, null));
  }

  @Test
  void snapshotsCloneOccupiedSlotsPollOnceAndClear() {
    player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 2));
    UnsocketGemStoneEvent event = mock(UnsocketGemStoneEvent.class);
    when(event.getPlayer()).thenReturn(player);
    new GemUnsocketSnapshotListener().onUnsocketInventorySnapshot(event);
    player.getInventory().getItem(0).setAmount(3);
    Map<Integer, ItemStack> snapshot = UnsocketInventorySnapshot.poll(player);
    assertEquals(2, snapshot.get(0).getAmount());
    assertTrue(UnsocketInventorySnapshot.poll(player).isEmpty());
    UnsocketInventorySnapshot.capture(player);
    UnsocketInventorySnapshot.clear(player);
    assertTrue(UnsocketInventorySnapshot.poll(player).isEmpty());
    UnsocketInventorySnapshot.capture(null);
    UnsocketInventorySnapshot.clear(null);
    assertTrue(UnsocketInventorySnapshot.poll(null).isEmpty());
  }

  @Test
  void snapshotsIgnoreExplicitAirSlots() {
    Player actor = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(actor.getInventory()).thenReturn(inventory);
    when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
    when(inventory.getSize()).thenReturn(1);
    when(inventory.getItem(0)).thenReturn(new ItemStack(Material.AIR));
    UnsocketInventorySnapshot.capture(actor);
    assertTrue(UnsocketInventorySnapshot.poll(actor).isEmpty());
  }

  @Test
  void socketApplyCopiesOldMapAndRecordsOnlyNewSocketUuidWithCursorRarity() {
    ItemStack old = tagged(Material.DIAMOND_SWORD),
        rebuilt = tagged(Material.DIAMOND_SWORD),
        cursor = tagged(Material.DIAMOND);
    UUID existing = UUID.randomUUID(), added = UUID.randomUUID();
    SocketRarityStore.put(old, existing, "common");
    GemRarityPdc.write(cursor, "rare");
    GemSocketRebuildListener listener = new GemSocketRebuildListener();
    listener.onRebuild(new MMOItemRebuildEvent(player, null, rebuilt, RebuildReason.GEM_APPLY));
    listener.onRebuild(new MMOItemRebuildEvent(player, old, null, RebuildReason.GEM_APPLY));
    try (var sockets = mockStatic(GemSocketsNbtEditor.class)) {
      sockets
          .when(() -> GemSocketsNbtEditor.getGemstoneUuids(same(old)))
          .thenReturn(Set.of(existing));
      sockets
          .when(() -> GemSocketsNbtEditor.getGemstoneUuids(same(rebuilt)))
          .thenReturn(new LinkedHashSet<>(List.of(existing, added)));
      MMOItemRebuildEvent event =
          new MMOItemRebuildEvent(player, old, rebuilt, RebuildReason.GEM_APPLY);
      event.setAppliedCursorSnapshot(cursor);
      listener.onRebuild(event);
      assertEquals("rare", SocketRarityStore.get(rebuilt, added));
      assertEquals("common", SocketRarityStore.get(rebuilt, existing));
      event.setAppliedCursorSnapshot(null);
      listener.onRebuild(event);
      event.setAppliedCursorSnapshot(new ItemStack(Material.DIAMOND));
      listener.onRebuild(event);
      sockets
          .when(() -> GemSocketsNbtEditor.getGemstoneUuids(same(rebuilt)))
          .thenReturn(Set.of(existing));
      event.setAppliedCursorSnapshot(cursor);
      listener.onRebuild(event);
      listener.onRebuild(new MMOItemRebuildEvent(player, old, rebuilt, null));
    }
  }

  @Test
  void unsocketRemovesRarityAndSchedulesRestorationFromSnapshot() {
    ItemStack old = tagged(Material.DIAMOND_SWORD), rebuilt = tagged(Material.DIAMOND_SWORD);
    UUID removed = UUID.randomUUID(), other = UUID.randomUUID();
    SocketRarityStore.put(old, removed, "rare");
    Gemstone gem = new Gemstone();
    gem.setMMOItem("GEM_STONE.RUBY");
    ConfigLoader.loadedGems.add(gem);
    GemSocketsData data = mock(GemSocketsData.class);
    GemstoneData unrelated = mock(GemstoneData.class), entry = mock(GemstoneData.class);
    when(unrelated.getHistoricUUID()).thenReturn(other);
    when(entry.getHistoricUUID()).thenReturn(removed);
    when(entry.getMMOItemType()).thenReturn("GEM_STONE");
    when(entry.getMMOItemID()).thenReturn("RUBY");
    when(data.getGems()).thenReturn(List.of(unrelated, entry));
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    try (var sockets = mockStatic(GemSocketsNbtEditor.class);
        var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var restorer = mockStatic(UnsocketedGemRestorer.class)) {
      bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
      sockets
          .when(() -> GemSocketsNbtEditor.getGemstoneUuids(same(old)))
          .thenReturn(Set.of(removed));
      sockets.when(() -> GemSocketsNbtEditor.getGemstoneUuids(same(rebuilt))).thenReturn(Set.of());
      sockets.when(() -> GemSocketsNbtEditor.getSockets(same(old))).thenReturn(data);
      UnsocketInventorySnapshot.capture(player);
      MMOItemRebuildEvent event =
          new MMOItemRebuildEvent(player, old, rebuilt, RebuildReason.GEM_UNSOCKET);
      new GemSocketRebuildListener().onRebuild(event);
      assertNull(SocketRarityStore.get(event.getNewItem(), removed));
      var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
      verify(scheduler).runTask(same(InfusionMain.plugin), task.capture());
      task.getValue().run();
      restorer.verify(
          () -> UnsocketedGemRestorer.restore(eq(player), eq("rare"), same(gem), anyMap()));
      sockets.when(() -> GemSocketsNbtEditor.getSockets(same(old))).thenReturn(null);
      new GemSocketRebuildListener().onRebuild(event);
      sockets.when(() -> GemSocketsNbtEditor.getSockets(same(old))).thenReturn(data);
      when(data.getGems()).thenReturn(List.of(unrelated));
      new GemSocketRebuildListener().onRebuild(event);
      SocketRarityStore.write(old, Map.of());
      new GemSocketRebuildListener().onRebuild(event);
      sockets.when(() -> GemSocketsNbtEditor.getGemstoneUuids(same(old))).thenReturn(Set.of());
      new GemSocketRebuildListener().onRebuild(event);
    }
  }

  @Test
  void restorerTargetsNewOrIncreasedStackWithoutTouchingPreexistingCandidates() {
    Player actor = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(actor.getInventory()).thenReturn(inventory);
    when(inventory.getSize()).thenReturn(3);
    ItemStack first = tagged(Material.DIAMOND),
        second = tagged(Material.DIAMOND),
        fixed = tagged(Material.EMERALD);
    second.setAmount(2);
    Gemstone gem = new Gemstone();
    gem.setMMOItem("GEM_STONE.RUBY");
    GemRarity rarity = mock(GemRarity.class);
    when(rarity.getId()).thenReturn("rare");
    ConfigLoader.loadedRarities.add(rarity);
    NBTItem nbt = mock(NBTItem.class);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("GEM_STONE");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("RUBY");
    try (var api = mockStatic(NBTItem.class);
        var builder = mockStatic(InfusedGemBuilder.class)) {
      api.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      builder
          .when(() -> InfusedGemBuilder.applyCosmeticsToItem(any(), same(gem), same(rarity)))
          .thenReturn(fixed);
      UnsocketedGemRestorer.restore(null, "rare", gem, Map.of());
      UnsocketedGemRestorer.restore(actor, null, gem, Map.of());
      UnsocketedGemRestorer.restore(actor, "rare", null, Map.of());
      UnsocketedGemRestorer.restore(actor, "missing", gem, Map.of());
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
      verify(inventory, never()).setItem(anyInt(), any());
      when(inventory.getItem(0)).thenReturn(first);
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
      verify(inventory).setItem(0, fixed);
      when(inventory.getItem(1)).thenReturn(second);
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of(0, first));
      verify(inventory).setItem(1, fixed);
      UnsocketedGemRestorer.restore(
          actor, "rare", gem, Map.of(0, first, 1, new ItemStack(Material.AIR)));
      verify(inventory, times(2)).setItem(1, fixed);
      UnsocketedGemRestorer.restore(
          actor, "rare", gem, Map.of(0, first, 1, new ItemStack(Material.DIAMOND)));
      verify(inventory, times(3)).setItem(1, fixed);
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of(0, first, 1, second));
      verify(inventory, times(3)).setItem(1, fixed);
      for (String display : Arrays.asList(null, " ", "Blank Gemstone", "Infused Gemstone")) {
        when(nbt.hasTag(ItemStats.DISPLAYED_TYPE.getNBTPath())).thenReturn(true);
        when(nbt.getString(ItemStats.DISPLAYED_TYPE.getNBTPath())).thenReturn(display);
        UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
      }
      when(inventory.getItem(0)).thenReturn(new ItemStack(Material.AIR));
      when(inventory.getItem(1)).thenReturn(null);
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
      when(inventory.getItem(0)).thenReturn(first);
      when(nbt.hasType()).thenReturn(false);
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
      when(nbt.hasType()).thenReturn(true);
      when(nbt.getType()).thenReturn("SWORD");
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
      when(nbt.getType()).thenReturn("GEM_STONE");
      when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("OTHER");
      UnsocketedGemRestorer.restore(actor, "rare", gem, Map.of());
    }
  }
}
