package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithLog;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class InfusionStationStoreTest {
  @TempDir Path temp;
  ServerMock server;
  World world;
  InfusionMain previous;
  InfusionStationStore store;
  Gemstone onyx, pearl, sunstone, green;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.getDataFolder()).thenReturn(temp.toFile());
    when(InfusionMain.plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
    ConfigLoader.loadedGems.clear();
    onyx = gem("onyx");
    pearl = gem("pearl");
    sunstone = gem("sunstone");
    green = gem("malachite");
    store = new InfusionStationStore();
  }

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
    InfusionMain.plugin = previous;
    ConfigLoader.loadedGems.clear();
  }

  Gemstone gem(String id) {
    Gemstone gem = new Gemstone();
    gem.setId(id);
    ConfigLoader.loadedGems.add(gem);
    return gem;
  }

  InfusionBlock station(int hits) {
    InfusionBlock station = new InfusionBlock();
    station.setLocation(new Location(world, -1, 64, 3));
    station.setParticleLocation(new Location(world, -.5, 65, 3.5));
    station.setInfustionHits(hits);
    station.setCurrentGems(new ArrayList<>(List.of(onyx, onyx, onyx, pearl, pearl, pearl, pearl, sunstone, green, green)));
    return station;
  }

  @Test
  void savesAndRestoresReportedMixedBatchAndProgressInSeparateWorlds() {
    assertTrue(store.loadAll().isEmpty());
    InfusionBlock original = station(3);
    InfusionBlock otherWorld = station(0);
    World second = server.addSimpleWorld("second");
    otherWorld.getLocation().setWorld(second);
    otherWorld.getParticleLocation().setWorld(second);
    assertTrue(store.save(original));
    assertTrue(store.save(otherWorld));
    // Recreate the configured definitions, as startup does.
    ConfigLoader.loadedGems.clear();
    for (String id : List.of("onyx", "pearl", "sunstone", "malachite")) gem(id);
    List<InfusionBlock> restored = new InfusionStationStore().loadAll();
    assertEquals(2, restored.size());
    InfusionBlock loaded = restored.stream().filter(s -> s.getLocation().getWorld().equals(world)).findFirst().orElseThrow();
    assertEquals(original.getLocation(), loaded.getLocation());
    assertEquals(original.getParticleLocation(), loaded.getParticleLocation());
    assertEquals(3, loaded.getInfusionHits());
    assertEquals(original.currentGems.stream().map(Gemstone::getId).toList(), loaded.currentGems.stream().map(Gemstone::getId).toList());
    assertSame(ConfigLoader.loadedGems.getFirst(), loaded.currentGems.getFirst());
    assertTrue(loaded.pendingDrops.isEmpty());
  }

  @Test
  void preservesRolledItemMetadataAndOnlyRemainingDropsAndDeletesFinishedSnapshots() {
    InfusionBlock completed = station(5);
    ItemStack item = new ItemStack(Material.EMERALD);
    var meta = item.getItemMeta();
    NamespacedKey key = new NamespacedKey("geminfusion", "test-roll");
    meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, 1.234);
    item.setItemMeta(meta);
    completed.pendingDrops.add(new InfusionBlock.Drop(item, "announcement"));
    completed.pendingDrops.add(new InfusionBlock.Drop(new ItemStack(Material.DIAMOND), ""));
    assertTrue(store.save(completed));
    InfusionBlock restored = new InfusionStationStore().loadAll().getFirst();
    assertEquals(completed.pendingDrops, restored.pendingDrops);
    assertEquals(1.234, restored.pendingDrops.getFirst().item().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE));
    restored.pendingDrops.removeFirst();
    assertTrue(store.save(restored));
    assertEquals(List.of(new InfusionBlock.Drop(new ItemStack(Material.DIAMOND), "")), new InfusionStationStore().loadAll().getFirst().pendingDrops);
    restored.pendingDrops.clear();
    assertTrue(store.save(restored));
    assertTrue(new InfusionStationStore().loadAll().getFirst().pendingDrops.isEmpty());
    store.delete(restored);
    store.delete(restored);
    assertTrue(store.loadAll().isEmpty());
  }

  @Test
  void rejectedFilesRemainIntactAndBlockNewDepositsAtThatTable() throws Exception {
    InfusionBlock original = station(0);
    assertTrue(store.save(original));
    Path file = store.fileFor(original.getLocation());
    List<java.util.function.Consumer<YamlConfiguration>> invalid = List.of(
        y -> y.set("world", "missing"),
        y -> y.set("world", null),
        y -> y.set("x", 10),
        y -> y.set("hits", -1),
        y -> y.set("hits", 6),
        y -> y.set("gems", List.of("unknown")),
        y -> y.set("gems", List.of()),
        y -> y.set("gems", Collections.nCopies(11, "onyx")),
        y -> y.set("drops.0.announcement", "missing item"),
        y -> y.set("drops.0.item", new ItemStack(Material.EMERALD)),
        y -> {
          y.set("hits", 5);
          for (int i = 0; i < 11; i++) y.set("drops." + i + ".item", new ItemStack(Material.EMERALD));
        });
    String valid = Files.readString(file);
    try (var log = mockStatic(GoldsmithLog.class)) {
      for (var corrupt : invalid) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(valid);
        corrupt.accept(yaml);
        yaml.save(file.toFile());
        String rejected = Files.readString(file);
        InfusionStationStore reloaded = new InfusionStationStore();
        assertTrue(reloaded.loadAll().isEmpty());
        assertFalse(reloaded.save(original));
        assertEquals(rejected, Files.readString(file));
      }
      Files.writeString(file, "world: [");
      assertTrue(store.loadAll().isEmpty());
      assertFalse(store.save(original));
      assertEquals("world: [", Files.readString(file));
      Files.writeString(file, valid);
      assertEquals(1, store.loadAll().size());
      assertTrue(store.save(original));
    }
  }

  @Test
  void reportsIoFailuresWithoutReplacingLastGoodSnapshotOrLeakingTemporaryFiles() throws Exception {
    InfusionBlock original = station(0);
    assertTrue(store.save(original));
    Path file = store.fileFor(original.getLocation());
    String saved = Files.readString(file);
    var permissions = Files.getPosixFilePermissions(store.folder());
    try (var log = mockStatic(GoldsmithLog.class)) {
      try {
        Files.setPosixFilePermissions(store.folder(), Set.of());
        assertThrows(IllegalStateException.class, store::loadAll);
        assertFalse(store.save(original));
        store.delete(original);
      } finally {
        Files.setPosixFilePermissions(store.folder(), permissions);
      }
      assertEquals(saved, Files.readString(file));
      var readable = Files.getPosixFilePermissions(file);
      try {
        Files.setPosixFilePermissions(file, Set.of());
        assertTrue(store.loadAll().isEmpty());
      } finally {
        Files.setPosixFilePermissions(file, readable);
      }
      store.loadAll();
      Files.delete(file);
      Files.createDirectory(file);
      Files.writeString(file.resolve("occupied"), "keep");
      assertFalse(store.save(original));
      try (var paths = Files.list(store.folder())) {
        assertEquals(List.of(file), paths.toList());
      }
      Files.writeString(store.folder().resolve("ignored.tmp"), "ignored");
      assertTrue(store.loadAll().isEmpty());
    }
  }
}
