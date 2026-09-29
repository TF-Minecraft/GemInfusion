package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.geminfusion.InfusionMain;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class GoldsmithStationStoreTest {
  @TempDir Path temp;
  ServerMock server;
  World world;
  InfusionMain previous;
  JewelryProject project;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.getDataFolder()).thenReturn(temp.toFile());
    when(InfusionMain.plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
    project = mock(JewelryProject.class);
    when(project.getId()).thenReturn("ring");
    when(project.getMaterialsByType()).thenReturn(Map.of("gold", 1));
  }

  @AfterEach
  void cleanup() {
    InfusionMain.plugin = previous;
    MockBukkit.unmock();
  }

  Path file(String name, String contents) throws IOException {
    Path folder = GoldsmithStationStore.folder().toPath();
    Files.createDirectories(folder);
    return Files.writeString(folder.resolve(name), contents);
  }

  String data(String rest) {
    return "{\"world\":\"world\",\"project\":\"ring\",\"x\":1,\"y\":2,\"z\":3" + rest + "}";
  }

  @Test
  void filenamesNormalizeWorldAndBlockCoordinatesAndDeleteIsIdempotent() throws Exception {
    World oddlyNamed = mock(World.class);
    when(oddlyNamed.getName()).thenReturn("world / !");
    assertEquals(
        "world_____-1_2_3.json",
        GoldsmithStationStore.fileName(new Location(oddlyNamed, -.1, 2.9, 3)));
    Location loc = new Location(null, 1, 2, 3);
    assertEquals("unknown_1_2_3.json", GoldsmithStationStore.fileName(loc));
    Path path = file("unknown_1_2_3.json", "{}");
    GoldsmithStationStore.delete(null);
    GoldsmithStationStore.delete(loc);
    assertFalse(Files.exists(path));
    GoldsmithStationStore.delete(loc);
  }

  @Test
  void savesAndRestoresRealItemStacksProgressAndGem() throws Exception {
    GoldsmithMaterial material = mock(GoldsmithMaterial.class);
    GoldsmithHit hit = mock(GoldsmithHit.class);
    GoldsmithHitType type = mock(GoldsmithHitType.class);
    when(material.getId()).thenReturn("gold");
    when(material.getType()).thenReturn("gold");
    when(material.getHits()).thenReturn(Map.of(hit, 2));
    when(hit.getId()).thenReturn("hammer");
    when(hit.getType()).thenReturn(type);
    when(project.requiresGem()).thenReturn(true);
    GoldsmithStation station = new GoldsmithStation(new Location(world, 1, 2, 3));
    station.setProject(project);
    ItemStack metal = new ItemStack(Material.GOLD_INGOT, 12),
        gem = new ItemStack(Material.DIAMOND, 3);
    try (var projects = mockStatic(JewelryProjectLoader.class);
        var materials = mockStatic(GoldsmithMaterialLoader.class);
        var hits = mockStatic(GoldsmithHitLoader.class);
        var validator = mockStatic(InfusedGemValidator.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      materials.when(() -> GoldsmithMaterialLoader.getByString("gold")).thenReturn(material);
      hits.when(() -> GoldsmithHitLoader.getByString("hammer")).thenReturn(hit);
      validator.when(() -> InfusedGemValidator.isInfused(any())).thenReturn(true);
      station.addMaterial(material, metal);
      station.addGem(gem);
      station.hit(hit);
      GoldsmithStationStore.saveAll(List.of(station));
      Path stored = GoldsmithStationStore.fileFor(station.getLoc()).toPath();
      assertTrue(Files.exists(stored));
      assertTrue(Files.readString(stored).contains("hammer"));
      List<GoldsmithStation> loaded = GoldsmithStationStore.loadAll();
      assertEquals(1, loaded.size());
      GoldsmithStation restored = loaded.getFirst();
      assertEquals(station.getLoc(), restored.getLoc());
      assertSame(project, restored.getProject());
      assertEquals(1, restored.getTotalHitCount());
      assertEquals(1, restored.getDepositedByMaterial().get(material));
      assertEquals(List.of(new ItemStack(Material.GOLD_INGOT)), restored.getDeposited());
      assertEquals(new ItemStack(Material.DIAMOND), restored.getGem());
      assertEquals(12, metal.getAmount());
      assertEquals(3, gem.getAmount());
    }
  }

  @Test
  void savesSkipInactiveStationsAndRemoveOnlyObsoleteJsonFiles() throws Exception {
    assertTrue(GoldsmithStationStore.loadAll().isEmpty());
    Path old = file("old.json", "{}"), note = file("note.txt", "keep");
    Path sub = GoldsmithStationStore.folder().toPath().resolve("sub.json");
    Files.createDirectory(sub);
    GoldsmithStation empty = new GoldsmithStation(new Location(world, 0, 0, 0));
    GoldsmithStation noLocation = new GoldsmithStation(null);
    noLocation.setProject(project);
    GoldsmithStation noWorld = new GoldsmithStation(new Location(null, 0, 0, 0));
    noWorld.setProject(project);
    GoldsmithStationStore.saveAll(Arrays.asList(null, empty, noLocation, noWorld));
    assertFalse(Files.exists(old));
    assertTrue(Files.exists(note));
    assertTrue(Files.isDirectory(sub));
    GoldsmithStationStore.saveAll(null);
    assertTrue(GoldsmithStationStore.loadAll().isEmpty());
  }

  @Test
  void incompleteOrUnavailableRecordsStayOnDiskAndDoNotRestore() throws Exception {
    List<Path> paths =
        List.of(
            file("null.json", "null"),
            file("missing-world.json", "{\"project\":\"ring\"}"),
            file("missing-project.json", "{\"world\":\"world\"}"),
            file("unknown-world.json", "{\"world\":\"absent\",\"project\":\"ring\"}"),
            file("unknown-project.json", data("")));
    try (var log = mockStatic(GoldsmithLog.class);
        var projects = mockStatic(JewelryProjectLoader.class)) {
      assertTrue(GoldsmithStationStore.loadAll().isEmpty());
      for (Path path : paths) assertTrue(Files.exists(path));
      log.verify(() -> GoldsmithLog.warn(anyString()), times(5));
    }
  }

  @Test
  void invalidAndNonItemSerializedValuesAreSkippedWithoutLosingStation() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject("not an item");
    }
    String nonItem = Base64.getEncoder().encodeToString(bytes.toByteArray());
    file(
        "station.json",
        data(
            ",\"deposited\":[null,\"\",\"not-base64!\",\"AA==\",\""
                + nonItem
                + "\"],\"gem\":\" \""));
    try (var projects = mockStatic(JewelryProjectLoader.class);
        var log = mockStatic(GoldsmithLog.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      List<GoldsmithStation> loaded = GoldsmithStationStore.loadAll();
      assertEquals(1, loaded.size());
      assertTrue(loaded.getFirst().getDeposited().isEmpty());
      assertFalse(loaded.getFirst().hasGem());
      file("station.json", data(",\"deposited\":null"));
      assertEquals(1, GoldsmithStationStore.loadAll().size());
    }
  }

  @Test
  void nonDirectoryStoragePathDoesNotThrow() throws Exception {
    Files.createDirectories(GoldsmithStationStore.folder().toPath().getParent());
    Files.writeString(GoldsmithStationStore.folder().toPath(), "not a directory");
    assertTrue(GoldsmithStationStore.loadAll().isEmpty());
    GoldsmithStation station = new GoldsmithStation(new Location(world, 1, 2, 3));
    station.setProject(project);
    try (var log = mockStatic(GoldsmithLog.class)) {
      GoldsmithStationStore.saveAll(List.of(station));
      log.verify(() -> GoldsmithLog.warn(contains("Failed to save")));
    }
  }

  @Test
  void failedDeletionIsLoggedAndDoesNotRemoveContents() throws Exception {
    Location loc = new Location(world, 1, 2, 3);
    Path dir = GoldsmithStationStore.fileFor(loc).toPath();
    Files.createDirectories(dir);
    Path child = Files.writeString(dir.resolve("child"), "keep");
    try (var log = mockStatic(GoldsmithLog.class)) {
      GoldsmithStationStore.delete(loc);
      assertTrue(Files.exists(child));
      log.verify(() -> GoldsmithLog.warn(contains("Failed to delete goldsmith station")));
    }
  }

  @Test
  void unserializableDepositedItemIsLoggedAndSkipped() throws Exception {
    GoldsmithStation station = mock(GoldsmithStation.class);
    ItemStack broken = mock(ItemStack.class);
    when(station.hasProject()).thenReturn(true);
    when(station.getProject()).thenReturn(project);
    when(station.getLoc()).thenReturn(new Location(world, 1, 2, 3));
    when(broken.serialize()).thenReturn(Map.of("invalid", new Object()));
    when(station.getDeposited()).thenReturn(Arrays.asList(null, broken));
    try (var log = mockStatic(GoldsmithLog.class)) {
      GoldsmithStationStore.saveAll(List.of(station));
      assertTrue(GoldsmithStationStore.fileFor(station.getLoc()).exists());
      log.verify(() -> GoldsmithLog.warn(contains("Failed to encode goldsmith item")));
    }
  }

  @Test
  void filesystemPermissionsProtectFailedReadAndReportFailedCleanup() throws Exception {
    Path denied = file("denied.json", data(""));
    var original = Files.getPosixFilePermissions(denied);
    try (var log = mockStatic(GoldsmithLog.class)) {
      try {
        Files.setPosixFilePermissions(denied, Set.of());
        assertTrue(GoldsmithStationStore.loadAll().isEmpty());
        log.verify(() -> GoldsmithLog.warn(contains("Failed to read")));
      } finally {
        Files.setPosixFilePermissions(denied, original);
      }
      Files.delete(denied);
      GoldsmithStationStore.loadAll();
      Path stale = file("stale.json", "{}");
      Path dir = stale.getParent();
      var permissions = Files.getPosixFilePermissions(dir);
      try {
        Files.setPosixFilePermissions(
            dir, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
        GoldsmithStationStore.saveAll(List.of());
        assertTrue(Files.exists(stale));
        log.verify(() -> GoldsmithLog.warn(contains("Failed to delete leftover")));
      } finally {
        Files.setPosixFilePermissions(dir, permissions);
      }
    }
  }

  @Test
  void malformedJsonIsIgnoredSoOtherStationsCanStillLoad() throws Exception {
    Path broken = file("broken.json", "{");
    file("valid.json", data(""));
    try (var projects = mockStatic(JewelryProjectLoader.class);
        var log = mockStatic(GoldsmithLog.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      assertEquals(1, GoldsmithStationStore.loadAll().size());
      assertTrue(Files.exists(broken));
      GoldsmithStationStore.saveAll(List.of());
      assertEquals("{", Files.readString(broken));
      Files.writeString(broken, data(""));
      assertEquals(1, GoldsmithStationStore.loadAll().size());
      GoldsmithStationStore.saveAll(List.of());
      assertFalse(Files.exists(broken));
    }
  }

  @Test
  void replacementStationQuarantinesRejectedBytesAndSurvivesDeleteRecreate() throws Exception {
    Location loc = new Location(world, 4, 5, 6);
    byte[] rejected = new byte[] {'{', 0, (byte) 0xff};
    Path stored = file(GoldsmithStationStore.fileName(loc), "");
    Files.write(stored, rejected);
    GoldsmithMaterial material = mock(GoldsmithMaterial.class);
    when(material.getId()).thenReturn("gold");
    when(material.getType()).thenReturn("gold");
    try (var projects = mockStatic(JewelryProjectLoader.class);
        var materials = mockStatic(GoldsmithMaterialLoader.class);
        var log = mockStatic(GoldsmithLog.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      materials.when(() -> GoldsmithMaterialLoader.getByString("gold")).thenReturn(material);
      assertTrue(GoldsmithStationStore.loadAll().isEmpty());
      GoldsmithStationStore.delete(loc);
      assertArrayEquals(rejected, Files.readAllBytes(stored));
      GoldsmithStation replacement = new GoldsmithStation(loc);
      replacement.setProject(project);
      replacement.addMaterial(material, new ItemStack(Material.GOLD_INGOT));
      GoldsmithStationStore.saveAll(List.of(replacement));
      List<Path> backups;
      try (var paths = Files.list(stored.getParent())) {
        backups = paths.filter(path -> !path.equals(stored)).toList();
      }
      assertEquals(1, backups.size());
      Path backup = backups.getFirst();
      assertFalse(backup.getFileName().toString().endsWith(".json"));
      assertArrayEquals(rejected, Files.readAllBytes(backup));
      GoldsmithStation restored = GoldsmithStationStore.loadAll().getFirst();
      assertEquals(loc, restored.getLoc());
      assertEquals(List.of(new ItemStack(Material.GOLD_INGOT)), restored.getDeposited());
      assertEquals(1, restored.getDepositedByMaterial().get(material));
      GoldsmithStationStore.delete(loc);
      assertFalse(Files.exists(stored));
      GoldsmithStationStore.saveAll(List.of(replacement));
      assertEquals(1, GoldsmithStationStore.loadAll().size());
      assertArrayEquals(rejected, Files.readAllBytes(backup));
      // A later rejected file at the same coordinates gets its own backup.
      Files.writeString(stored, "{ second rejected record");
      assertTrue(GoldsmithStationStore.loadAll().isEmpty());
      GoldsmithStationStore.saveAll(List.of(replacement));
      try (var paths = Files.list(stored.getParent())) {
        assertEquals(3, paths.count());
      }
      assertArrayEquals(rejected, Files.readAllBytes(backup));
      assertEquals(1, GoldsmithStationStore.loadAll().size());
      GoldsmithStationStore.saveAll(List.of());
      assertFalse(Files.exists(stored));
      assertTrue(Files.exists(backup));
    }
  }

  @Test
  void failedQuarantinePreservesRejectedFileAndRetriesAfterPermissionsRestore() throws Exception {
    Location loc = new Location(world, 1, 2, 3);
    Path stored = file(GoldsmithStationStore.fileName(loc), "{ original");
    GoldsmithStation station = new GoldsmithStation(loc);
    station.setProject(project);
    Path dir = stored.getParent();
    var permissions = Files.getPosixFilePermissions(dir);
    try (var log = mockStatic(GoldsmithLog.class);
        var projects = mockStatic(JewelryProjectLoader.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      assertTrue(GoldsmithStationStore.loadAll().isEmpty());
      try {
        Files.setPosixFilePermissions(
            dir, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
        GoldsmithStationStore.saveAll(List.of(station));
        assertEquals("{ original", Files.readString(stored));
        log.verify(() -> GoldsmithLog.warn(contains("Failed to quarantine")));
        GoldsmithStationStore.delete(loc);
        assertEquals("{ original", Files.readString(stored));
      } finally {
        Files.setPosixFilePermissions(dir, permissions);
      }
      GoldsmithStationStore.saveAll(List.of(station));
      assertEquals(1, GoldsmithStationStore.loadAll().size());
      try (var paths = Files.list(dir)) {
        Path backup = paths.filter(path -> !path.equals(stored)).findFirst().orElseThrow();
        assertEquals("{ original", Files.readString(backup));
      }
    }
  }

  @Test
  void externallyRemovedRejectedFileDoesNotPreventSavingReplacement() throws Exception {
    Location loc = new Location(world, 1, 2, 3);
    Path stored = file(GoldsmithStationStore.fileName(loc), "{");
    assertTrue(GoldsmithStationStore.loadAll().isEmpty());
    Files.delete(stored);
    GoldsmithStation station = new GoldsmithStation(loc);
    station.setProject(project);
    GoldsmithStationStore.saveAll(List.of(station));
    try (var projects = mockStatic(JewelryProjectLoader.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      assertEquals(1, GoldsmithStationStore.loadAll().size());
    }
    try (var paths = Files.list(stored.getParent())) {
      assertEquals(1, paths.count());
    }
  }
}
