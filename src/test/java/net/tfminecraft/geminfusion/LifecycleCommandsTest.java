package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.geminfusion.goldsmith.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class LifecycleCommandsTest {
  ServerMock server;
  InfusionMain previous;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    previous = InfusionMain.plugin;
  }

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
    InfusionMain.plugin = previous;
    JewelryProjectLoader.get().clear();
  }

  @Test
  void pluginLoadsResourcesRegistersCommandAutosavesReloadsAndFlushesOnDisable() {
    MockBukkit.createMockPlugin("TLibs");
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    try (var stations = mockConstruction(GoldsmithStationManager.class);
        var log = mockStatic(GoldsmithLog.class)) {
      InfusionMain plugin = MockBukkit.load(InfusionMain.class);
      assertSame(plugin, InfusionMain.plugin);
      assertSame(stations.constructed().getFirst(), plugin.getGoldsmithStations());
      assertNotNull(plugin.getCommand("geminfusion").getExecutor());
      assertNotNull(plugin.getCommand("geminfusion").getTabCompleter());
      assertTrue(new java.io.File(plugin.getDataFolder(), "goldsmithing/projects.yml").exists());
      verify(plugin.getGoldsmithStations()).loadPersisted();
      server.getScheduler().performTicks(1200);
      verify(plugin.getGoldsmithStations()).flush(false);
      plugin.reloadConfigPCommand(server.addPlayer());
      verify(plugin.getGoldsmithStations()).clear();
      verify(plugin.getGoldsmithStations(), times(2)).loadPersisted();
      try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
        var plugins = mock(org.bukkit.plugin.PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        plugin.onEnable();
      }
      plugin.onDisable();
      verify(plugin.getGoldsmithStations(), atLeast(2)).flush(true);
    }
  }

  @Test
  void commandsCoverUsageReloadPermissionsAndProjectSelection() {
    CommandManager commands = new CommandManager();
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("other");
    Player player = mock(Player.class);
    CommandSender console = mock(CommandSender.class);
    InfusionMain.plugin = mock(InfusionMain.class);
    assertFalse(commands.onCommand(player, command, "x", new String[0]));
    when(command.getName()).thenReturn("geminfusion");
    assertTrue(commands.onCommand(console, command, "x", new String[0]));
    commands.onCommand(console, command, "x", new String[] {"unknown"});
    commands.onCommand(console, command, "x", new String[] {"reload"});
    verify(InfusionMain.plugin, never()).reloadConfigCommand();
    when(console.hasPermission(Permissions.ADMIN)).thenReturn(true);
    commands.onCommand(console, command, "x", new String[] {"reload"});
    verify(InfusionMain.plugin).reloadConfigCommand();
    when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
    commands.onCommand(player, command, "x", new String[] {"reload"});
    verify(InfusionMain.plugin).reloadConfigPCommand(player);
    when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
    commands.onCommand(player, command, "x", new String[] {"select"});
    when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
    commands.onCommand(console, command, "x", new String[] {"select"});
    verify(console).sendMessage(contains("Only players"));
    try (var perms = mockStatic(Permissions.class, CALLS_REAL_METHODS)) {
      perms.when(() -> Permissions.requireUseGoldsmith(player)).thenReturn(false);
      commands.onCommand(player, command, "x", new String[] {"select"});
    }
    commands.onCommand(player, command, "x", new String[] {"select"});
    verify(player).sendMessage(contains("Usage"));
    commands.onCommand(player, command, "x", new String[] {"select", "missing"});
    verify(player).sendMessage(contains("Unknown project"));
    JewelryProject project = mock(JewelryProject.class);
    when(project.getName()).thenReturn("Ring");
    JewelryProjectLoader.get().put("ring", project);
    GoldsmithStationManager stations = mock(GoldsmithStationManager.class);
    when(InfusionMain.plugin.getGoldsmithStations()).thenReturn(stations);
    commands.onCommand(player, command, "x", new String[] {"select", "ring"});
    Block target = server.addSimpleWorld("world").getBlockAt(1, 2, 3);
    when(player.getTargetBlockExact(6)).thenReturn(target);
    commands.onCommand(player, command, "x", new String[] {"select", "ring"});
    when(stations.isGoldsmithStation(target)).thenReturn(true);
    GoldsmithStation station = new GoldsmithStation(target.getLocation());
    when(stations.getOrCreate(target.getLocation())).thenReturn(station);
    commands.onCommand(player, command, "x", new String[] {"select", "ring"});
    assertSame(project, station.getProject());
    verify(stations).markDirty();
    commands.onCommand(player, command, "x", new String[] {"select", "ring"});
    verify(player).sendMessage(contains("already has a project"));
  }

  @Test
  void tabCompletionFiltersPrefixesPermissionsAndLimitsResults() {
    CommandManager commands = new CommandManager();
    Command command = mock(Command.class);
    CommandSender sender = mock(CommandSender.class);
    when(command.getName()).thenReturn("other");
    assertNull(commands.onTabComplete(sender, command, "x", new String[0]));
    when(command.getName()).thenReturn("geminfusion");
    assertEquals(
        List.of("reload", "select"),
        commands.onTabComplete(sender, command, "x", new String[] {""}));
    assertEquals(
        List.of("reload"), commands.onTabComplete(sender, command, "x", new String[] {"RE"}));
    assertTrue(
        commands.onTabComplete(sender, command, "x", new String[] {"unknown", ""}).isEmpty());
    assertTrue(commands.onTabComplete(sender, command, "x", new String[0]).isEmpty());
    assertTrue(commands.onTabComplete(sender, command, "x", new String[] {"select", ""}).isEmpty());
    when(sender.hasPermission(Permissions.ADMIN)).thenReturn(true);
    JewelryProjectLoader.get().put("other", mock(JewelryProject.class));
    for (int i = 0; i < 45; i++)
      JewelryProjectLoader.get().put("ring" + i, mock(JewelryProject.class));
    assertEquals(
        40, commands.onTabComplete(sender, command, "x", new String[] {"select", "RING"}).size());
    assertEquals(
        List.of("other"),
        commands.onTabComplete(sender, command, "x", new String[] {"select", "o"}));
  }

  @Test
  void goldsmithPermissionsRespectAdminOptionalPermissionAndDenialMessages() {
    String previousPermission = GoldsmithCache.permission;
    Player player = mock(Player.class);
    try {
      assertFalse(Permissions.requireAdmin(player));
      when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
      assertTrue(Permissions.requireAdmin(player));
      assertTrue(Permissions.canUseGoldsmith(player));
      when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
      GoldsmithCache.permission = null;
      assertTrue(Permissions.canUseGoldsmith(player));
      GoldsmithCache.permission = " ";
      assertTrue(Permissions.canUseGoldsmith(player));
      GoldsmithCache.permission = "goldsmith";
      assertFalse(Permissions.requireUseGoldsmith(player));
      when(player.hasPermission("goldsmith")).thenReturn(true);
      assertTrue(Permissions.requireUseGoldsmith(player));
    } finally {
      GoldsmithCache.permission = previousPermission;
    }
    new Permissions();
  }
}
