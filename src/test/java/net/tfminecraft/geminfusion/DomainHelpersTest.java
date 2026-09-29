package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithLog;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class DomainHelpersTest {
  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  @Test
  void statParserAcceptsNewAndLegacyFormatsAndRejectsMalformedEntries() throws Exception {
    InfusionMain previous = InfusionMain.plugin;
    try {
      InfusionMain.plugin = null;
      assertNull(GemStat.parse("ruby", "rare", null));
      assertNull(GemStat.parse("ruby", "rare", config("{}")));
      GemStat stat = GemStat.parse("ruby", "rare", config("stat: ATTACK_DAMAGE\nmin: 2\nmax: 4"));
      assertEquals("rare", stat.getId());
      assertEquals("ATTACK_DAMAGE", stat.getStatId());
      assertEquals(2, stat.getMin());
      assertEquals(4, stat.getMax());
      assertEquals(
          2, GemStat.parse("ruby", "rare", config("stat: ATTACK_DAMAGE\nmin: 2")).getMax());
      assertNull(GemStat.parse("ruby", "rare", config("stat: ' '")));
      ConfigurationSection broken = mock(ConfigurationSection.class);
      when(broken.contains("stat")).thenReturn(true);
      assertNull(GemStat.parse("ruby", "rare", broken));
      when(broken.contains("stat")).thenReturn(false);
      when(broken.getStringList("stats")).thenReturn(null);
      assertNull(GemStat.parse("ruby", "rare", broken));
      for (String entry :
          Arrays.asList(
              null,
              "invalid",
              "(1-2)",
              "damage(1-2",
              "damage(2)",
              "damage(x-2)",
              "damage(1-x)",
              "damage(2)")) {
        when(broken.getStringList("stats")).thenReturn(Arrays.asList(entry));
        assertNull(GemStat.parse("ruby", "rare", broken));
      }
      stat = GemStat.parse("ruby", "rare", config("stats: ['damage(1-2)', 'ignored(2-3)']"));
      assertEquals("damage", stat.getStatId());
      assertEquals(1, stat.getMin());
      assertEquals(2, stat.getMax());
      InfusionMain.plugin = mock(InfusionMain.class);
      var logger = mock(java.util.logging.Logger.class);
      when(InfusionMain.plugin.getLogger()).thenReturn(logger);
      GemStat.parse("ruby", "rare", null);
      verify(logger).warning(anyString());
    } finally {
      InfusionMain.plugin = previous;
    }
  }

  @Test
  void attributeCurveInterpolatesAndRejectsInvalidThresholdOrdering() throws Exception {
    assertEquals(0, AttributeInfluence.disabled().delta(9));
    assertEquals(0, AttributeInfluence.from(null, null).delta(9));
    assertEquals(0, AttributeInfluence.from(null, "dexterity").delta(9));
    AttributeInfluence curve =
        AttributeInfluence.from(
            config("floor: 0\nneutral: 10\nfull: 20\nmax-bonus: 0.4\nmax-penalty: 0.2"),
            "intelligence");
    assertEquals(-.2, curve.delta(-1));
    assertEquals(-.2, curve.delta(0));
    assertEquals(-.1, curve.delta(5), .00001);
    assertEquals(0, curve.delta(10));
    assertEquals(.2, curve.delta(15), .00001);
    assertEquals(.4, curve.delta(20));
    assertEquals(.4, curve.delta(100));
    assertEquals(
        0, AttributeInfluence.from(config("floor: 0\nneutral: 10\nfull: 5"), null).delta(4));
    assertEquals(
        0, AttributeInfluence.from(config("floor: 10\nneutral: 0\nfull: 20"), null).delta(4));
  }

  @Test
  void attributeLookupHandlesAbsentPluginPlayerInvalidIdAndIntegrationFailures() throws Exception {
    var server = MockBukkit.mock();
    Player player = server.addPlayer();
    AttributeInfluence influence =
        AttributeInfluence.from(config("mmocore-id: dexterity"), "intelligence");
    assertEquals(0, influence.readAttribute(null));
    assertEquals(0, influence.readAttribute(player));
    assertEquals(0, AttributeInfluence.from(config("{}"), null).readAttribute(player));
    assertEquals(0, AttributeInfluence.from(config("mmocore-id: ' '"), " ").readAttribute(player));
    MockBukkit.createMockPlugin("MMOCore");
    try (var data = mockStatic(PlayerData.class)) {
      data.when(() -> PlayerData.get(player)).thenThrow(new IllegalStateException("unavailable"));
      assertEquals(0, influence.readAttribute(player));
      PlayerData record = mock(PlayerData.class, RETURNS_DEEP_STUBS);
      when(record.getAttributes().getInstance("dexterity").getTotal()).thenReturn(15);
      data.when(() -> PlayerData.get(player)).thenReturn(record);
      assertEquals(15, influence.readAttribute(player));
      assertEquals(.1, influence.forPlayer(player), .00001);
    }
  }

  @Test
  void gemAndStationAccessorsPreserveAssignedValuesAndLookupMmoTemplate() {
    MMOItems previous = MMOItems.plugin;
    try {
      MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
      Gemstone gem = new Gemstone();
      gem.setMMOItem("gem_stone.ruby");
      MMOItem template = mock(MMOItem.class);
      when(MMOItems.plugin
              .getItems()
              .getMMOItem(MMOItems.plugin.getTypes().get("GEM_STONE"), "RUBY"))
          .thenReturn(template);
      assertSame(template, gem.getMMOItem());
      gem.addStat(null);
      assertTrue(gem.getStats().isEmpty());
      InfusionBlock block = new InfusionBlock();
      block.setToken(true);
      assertTrue(block.hasToken());
      block.setCurrentGems(new ArrayList<>(List.of(gem)));
      assertEquals(List.of(gem), block.getCurrentItems());
      GemRarity rarity = mock(GemRarity.class, CALLS_REAL_METHODS);
      rarity.setId("rare");
      rarity.setName("Rare");
      rarity.setChance(2);
      rarity.setAnnounce(true);
      assertEquals("rare", rarity.getId());
      assertEquals("Rare", rarity.getName());
      assertEquals(2, rarity.getChance());
      assertTrue(rarity.shouldAnnounce());
    } finally {
      MMOItems.plugin = previous;
    }
  }

  @Test
  void loggingUsesPluginLoggerOrFallback() {
    InfusionMain previous = InfusionMain.plugin;
    try {
      InfusionMain.plugin = null;
      assertNotNull(GoldsmithLog.get());
      GoldsmithLog.info("Coverage fallback logger");
      GoldsmithLog.warn("Coverage fallback warning");
      InfusionMain.plugin = mock(InfusionMain.class);
      var logger = mock(java.util.logging.Logger.class);
      when(InfusionMain.plugin.getLogger()).thenReturn(logger);
      GoldsmithLog.info("info");
      GoldsmithLog.warn("warning");
      verify(logger).info("info");
      verify(logger).warning("warning");
    } finally {
      InfusionMain.plugin = previous;
    }
  }
}
