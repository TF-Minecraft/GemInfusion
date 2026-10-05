package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.tfminecraft.geminfusion.goldsmith.InfusedGemValidator;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class InfusionEventsTest {
  InfusionEvents events;
  World world;
  Player player;
  PlayerInventory inventory;
  Block block;
  ItemStack hand;
  NBTItem nbt;
  Gemstone gem;
  MockedStatic<NBTItem> nbtApi;
  Location location;

  @BeforeEach
  void setup() {
    events = new InfusionEvents();
    world = mock(World.class);
    player = mock(Player.class);
    inventory = mock(PlayerInventory.class);
    block = mock(Block.class);
    location = new Location(world, 1, 2, 3);
    hand = mock(ItemStack.class);
    nbt = mock(NBTItem.class);
    gem = mock(Gemstone.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(hand);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(location);
    when(block.getLocation()).thenReturn(location);
    when(block.getType()).thenReturn(Material.ENCHANTING_TABLE);
    when(hand.getAmount()).thenReturn(5);
    when(hand.getType()).thenReturn(Material.AMETHYST_SHARD);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("GEM_STONE");
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("RUBY");
    when(gem.getMMOItemString()).thenReturn("GEM_STONE.RUBY");
    MMOItem mmo = mock(MMOItem.class);
    when(mmo.getId()).thenReturn("RUBY");
    when(gem.getMMOItem()).thenReturn(mmo);
    when(gem.getColour()).thenReturn(ChatColor.RED);
    when(gem.getName()).thenReturn("Ruby");
    ConfigLoader.stations.clear();
    ConfigLoader.stations.add(Material.ENCHANTING_TABLE);
    ConfigLoader.locations.clear();
    ConfigLoader.useLocations = false;
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedGems.add(gem);
    ConfigLoader.loadedRarities.clear();
    ConfigLoader.infusionStaff = "STAFF.INFUSER";
    nbtApi = mockStatic(NBTItem.class);
    nbtApi.when(() -> NBTItem.get(hand)).thenReturn(nbt);
  }

  @AfterEach
  void cleanup() {
    nbtApi.close();
    ConfigLoader.stations.clear();
    ConfigLoader.locations.clear();
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedRarities.clear();
  }

  PlayerInteractEvent event(Action action, EquipmentSlot slot) {
    return new PlayerInteractEvent(
        player, action, hand, block, org.bukkit.block.BlockFace.UP, slot);
  }

  InfusionBlock station(Location loc, int size, int hits) {
    InfusionBlock s = new InfusionBlock();
    s.setLocation(loc);
    s.setParticleLocation(loc.clone().add(.5, 1, .5));
    s.setInfustionHits(hits);
    for (int i = 0; i < size; i++) s.addGem(gem);
    events.currentStations.add(s);
    return s;
  }

  @Test
  void addsOneGemToNewAndExistingStationAndCancelsVanillaInteraction() {
    PlayerInteractEvent click = event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
    events.addGemEvent(click);
    assertTrue(click.isCancelled());
    assertEquals(1, events.currentStations.size());
    InfusionBlock s = events.currentStations.getFirst();
    assertEquals(location, s.getLocation());
    assertEquals(1, s.getCurrentItems().size());
    events.addGemEvent(click);
    assertEquals(2, s.getCurrentItems().size());
    verify(hand, times(2)).setAmount(4);
  }

  @Test
  void nonStationWrongActionAndNonGemItemsDoNotConsumeAnything() {
    events.addGemEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(block.getType()).thenReturn(Material.DIRT);
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(block.getType()).thenReturn(Material.ENCHANTING_TABLE);
    when(nbt.hasType()).thenReturn(false);
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Sword");
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    when(nbt.getType()).thenReturn("SWORD");
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(nbt.getType()).thenReturn("GEM_STONE");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("OTHER");
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertTrue(events.currentStations.isEmpty());
    verify(hand, never()).setAmount(anyInt());
  }

  @Test
  void configuredStationAndGemLocationsMustMatch() {
    ConfigLoader.useLocations = true;
    ConfigLoader.locations.add("9,2,3");
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertTrue(events.currentStations.isEmpty());
    ConfigLoader.locations.add("1,2,3");
    when(gem.isLocationSpecific()).thenReturn(true);
    when(gem.getLocation()).thenReturn("9,2,3");
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertTrue(events.currentStations.isEmpty());
    when(gem.getLocation()).thenReturn("1,2,3");
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertEquals(
        location.clone().add(.5, 1, .5), events.currentStations.getFirst().getParticleLocation());
  }

  @Test
  void resetInfusedGemIsRestoredInHandInsteadOfInfusedAgain() {
    ItemStack restored = mock(ItemStack.class);
    try (var valid = mockStatic(InfusedGemValidator.class);
        var builder = mockStatic(InfusedGemBuilder.class)) {
      valid.when(() -> InfusedGemValidator.isReset(hand)).thenReturn(true);
      builder.when(() -> InfusedGemBuilder.restoreReset(hand)).thenReturn(restored);
      PlayerInteractEvent click = event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
      events.addGemEvent(click);
      assertTrue(click.isCancelled());
    }
    verify(inventory).setItemInMainHand(restored);
    verify(player).sendMessage(contains("already holds an infusion"));
    assertTrue(events.currentStations.isEmpty());
    verify(hand, never()).setAmount(anyInt());
  }

  @Test
  void startedAndFullStationsRejectFurtherGems() {
    InfusionBlock s = station(location, 1, 1);
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertEquals(1, s.getCurrentItems().size());
    events.currentStations.clear();
    s = station(location, 10, 0);
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertEquals(10, s.getCurrentItems().size());
    verify(hand, never()).setAmount(anyInt());
  }

  @Test
  void unrelatedFullStationMustNotBlockDifferentBench() {
    station(location.clone().add(10, 0, 0), 10, 0);
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertEquals(
        2, events.currentStations.size(), "A full distant bench must not block this bench");
    verify(hand).setAmount(4);
  }

  @Test
  void offhandDuplicateMustNotConsumeMainHandGem() {
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND));
    assertTrue(events.currentStations.isEmpty(), "Offhand event must not consume main-hand items");
    verify(hand, never()).setAmount(anyInt());
  }

  @Test
  void infusionHitsRequireCorrectStaffAndOnlyAffectMatchingNonemptyStation() {
    InfusionBlock s = station(location, 1, 0);
    station(location.clone().add(10, 0, 0), 1, 0);
    events.infuseHitEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(block.getType()).thenReturn(Material.DIRT);
    events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(block.getType()).thenReturn(Material.ENCHANTING_TABLE);
    when(nbt.hasType()).thenReturn(false);
    events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(nbt.hasType()).thenReturn(true);
    events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(nbt.getType()).thenReturn("STAFF");
    events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("INFUSER");
    PlayerInteractEvent click = event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
    events.infuseHitEvent(click);
    assertTrue(click.isCancelled());
    assertEquals(1, s.getInfusionHits());
    assertEquals(0, events.currentStations.get(1).getInfusionHits());
    s.getCurrentItems().clear();
    events.infuseHitEvent(click);
    assertEquals(1, s.getInfusionHits());
  }

  @Test
  void fifthHitConsumesStaffAndScheduledTaskDropsBuiltGemThenStops() {
    InfusionBlock s = station(location, 2, 4);
    when(nbt.getType()).thenReturn("STAFF");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("INFUSER");
    GemRarity rarity = mock(GemRarity.class);
    when(rarity.getChance()).thenReturn(1.0);
    when(rarity.shouldAnnounce()).thenReturn(true, false);
    ConfigLoader.loadedRarities.add(rarity);
    InfusionMain previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    BukkitTask task = mock(BukkitTask.class);
    when(task.getTaskId()).thenReturn(7);
    Item dropped = mock(Item.class);
    ItemStack built = mock(ItemStack.class);
    when(world.dropItem(any(Location.class), same(built))).thenReturn(dropped);
    try (var bukkit = mockStatic(Bukkit.class);
        var builder = mockStatic(InfusedGemBuilder.class)) {
      bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
      builder
          .when(() -> InfusedGemBuilder.buildInfusedGem(gem, rarity, 2, player))
          .thenReturn(built);
      org.mockito.ArgumentCaptor<Runnable> capture =
          org.mockito.ArgumentCaptor.forClass(Runnable.class);
      when(scheduler.runTaskTimer(same(InfusionMain.plugin), capture.capture(), eq(30L), eq(2L)))
          .thenReturn(task);
      events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
      assertTrue(events.currentStations.isEmpty());
      assertEquals(5, s.getInfusionHits());
      verify(hand).setAmount(4);
      ConfigLoader.loadedRarities
          .clear(); // Reload after consumption cannot invalidate the resolved batch.
      capture.getValue().run();
      verify(world).dropItem(location.clone().add(.5, 1.1, .5), built);
      verify(dropped).setVelocity(any());
      capture.getValue().run();
      verify(world, times(2)).dropItem(location.clone().add(.5, 1.1, .5), built);
      capture.getValue().run();
      verify(scheduler).cancelTask(7);
      verify(player).sendMessage(contains("just infused"));
    } finally {
      InfusionMain.plugin = previous;
    }
  }

  @Test
  void unrelatedNonfullStationIsNotChangedByAnotherBench() {
    InfusionBlock unrelated = station(location.clone().add(10, 0, 0), 1, 0);
    events.addGemEvent(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
    assertEquals(2, events.currentStations.size());
    assertEquals(1, unrelated.getCurrentItems().size());
  }

  @Test
  void emptyRarityConfigurationFailsPromptlyInIsolatedJvm() throws Exception {
    Process process =
        new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-cp",
                System.getProperty("java.class.path"),
                EmptyRarityProbe.class.getName())
            .redirectErrorStream(true)
            .start();
    try {
      assertTrue(
          process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS),
          "Empty rarity configuration must not hang the server thread");
      assertEquals(
          0,
          process.exitValue(),
          new String(
              process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    } finally {
      process.destroyForcibly();
      process.waitFor();
    }
  }

  public static class EmptyRarityProbe {
    public static void main(String[] args) {
      ConfigLoader.loadedRarities.clear();
      try {
        new InfusionEvents().getRarity();
        throw new AssertionError("Expected invalid configuration rejection");
      } catch (IllegalStateException expected) {
      }
    }
  }

  @Test
  void invalidRaritiesRejectPromptlyAndMixedWeightsKeepPositiveRatios() {
    assertThrows(IllegalStateException.class, events::getRarity);
    for (double weight :
        new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      GemRarity rarity = mock(GemRarity.class);
      when(rarity.getChance()).thenReturn(weight);
      ConfigLoader.loadedRarities.add(rarity);
    }
    assertThrows(IllegalStateException.class, events::getRarity);
    GemRarity small = mock(GemRarity.class), large = mock(GemRarity.class);
    when(small.getChance()).thenReturn(1.0);
    when(large.getChance()).thenReturn(3.0);
    ConfigLoader.loadedRarities.add(small);
    ConfigLoader.loadedRarities.add(large);
    assertSame(small, events.getRarity(0));
    assertSame(small, events.getRarity(Math.nextDown(.25)));
    assertSame(large, events.getRarity(.25));
    assertSame(large, events.getRarity(Math.nextDown(1.0)));
    when(small.getChance()).thenReturn(Double.MAX_VALUE);
    when(large.getChance()).thenReturn(Double.MAX_VALUE);
    assertSame(small, events.getRarity(0));
    assertSame(large, events.getRarity(.5));
  }

  @Test
  void invalidRaritiesLeaveCompletedBenchAndStaffAvailableForRetry() {
    InfusionBlock station = station(location, 2, 4);
    when(nbt.getType()).thenReturn("STAFF");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("INFUSER");
    try (var log = mockStatic(net.tfminecraft.geminfusion.goldsmith.GoldsmithLog.class)) {
      events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
      assertEquals(List.of(station), events.currentStations);
      assertEquals(2, station.getCurrentItems().size());
      assertEquals(4, station.getInfusionHits());
      verify(hand, never()).setAmount(anyInt());
      verify(player).sendMessage(contains("your gems remain"));
      log.verify(
          () ->
              net.tfminecraft.geminfusion.goldsmith.GoldsmithLog.warn(
                  contains("positive rarity weight")));
    }
  }

  @Test
  void offhandHitDoesNotAdvanceMainHandStaffProgress() {
    InfusionBlock station = station(location, 1, 0);
    when(nbt.getType()).thenReturn("STAFF");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("INFUSER");
    events.infuseHitEvent(event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.OFF_HAND));
    assertEquals(0, station.getInfusionHits());
  }

  @Test
  void upperBoundaryRoundingStillSelectsLastValidRarity() {
    GemRarity last = null;
    for (double weight :
        new double[] {
          0.00015979636207798768, 2.1443702942086142, 3259.817615131147, 0.11210730216548585
        }) {
      last = mock(GemRarity.class);
      when(last.getChance()).thenReturn(weight);
      ConfigLoader.loadedRarities.add(last);
    }
    assertSame(last, events.getRarity(Math.nextDown(1.0)));
  }

  @Test
  void positiveRarityWeightsAlwaysSelectConfiguredRarity() {
    GemRarity zero = mock(GemRarity.class), positive = mock(GemRarity.class);
    when(positive.getChance()).thenReturn(5.0);
    ConfigLoader.loadedRarities.add(zero);
    ConfigLoader.loadedRarities.add(positive);
    assertSame(positive, events.getRarity());
  }
}
