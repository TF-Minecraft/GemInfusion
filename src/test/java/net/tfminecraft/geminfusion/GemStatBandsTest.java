package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

class GemStatBandsTest {
  InfusionMain previous;
  Logger logger;

  @BeforeEach
  void setup() {
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    logger = mock(Logger.class);
    when(InfusionMain.plugin.getLogger()).thenReturn(logger);
  }

  @AfterEach
  void cleanup() {
    InfusionMain.plugin = previous;
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedRarities.clear();
    GemNameCutoffs.load(List.of(), List.of());
  }

  static YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  static List<String> describe(List<GemStat> stats) {
    return stats.stream()
        .map(s -> s.getId() + " " + s.getStatId() + " " + s.getMin() + "-" + s.getMax())
        .toList();
  }

  static final String BANDS =
      "stat-bands:\n"
          + "  tier-1:\n"
          + "    common: 1.0-1.2\n"
          + "    legendary: 1.9 - 2\n";

  @Test
  void gemUsesANamedSetOrItsOwnBands() throws Exception {
    var c =
        config(
            BANDS
                + "gems:\n"
                + "  onyx:\n"
                + "    stat: armor\n"
                + "    bands: tier-1\n"
                + "  agate:\n"
                + "    stat: max_health\n"
                + "    bands:\n"
                + "      common: 0.20-0.30\n"
                + "      rare: 0.5\n");
    assertEquals(
        List.of("common armor 1.0-1.2", "legendary armor 1.9-2.0"),
        describe(GemStat.parseGem("onyx", c.getConfigurationSection("gems.onyx"), c.getConfigurationSection("stat-bands"))));
    // A single number is a fixed stat.
    assertEquals(
        List.of("common max_health 0.2-0.3", "rare max_health 0.5-0.5"),
        describe(GemStat.parseGem("agate", c.getConfigurationSection("gems.agate"), null)));
    verify(logger, never()).warning(anyString());
  }

  @Test
  void brokenBandsAreSkippedWithAWarning() throws Exception {
    var c =
        config(
            BANDS
                + "gems:\n"
                + "  unknown:\n"
                + "    stat: armor\n"
                + "    bands: tier-9\n"
                + "  nostat:\n"
                + "    bands: tier-1\n"
                + "  blank:\n"
                + "    stat: ' '\n"
                + "    bands: tier-1\n"
                + "  bad:\n"
                + "    stat: armor\n"
                + "    bands:\n"
                + "      common: lots\n"
                + "      rare: {min: 1}\n"
                + "      epic: 2-3\n"
                + "  both:\n"
                + "    stat: armor\n"
                + "    bands: tier-1\n"
                + "    rarities:\n"
                + "      common: {stat: armor, min: 9}\n"
                + "  none:\n"
                + "    name: None\n");
    var sets = c.getConfigurationSection("stat-bands");
    assertTrue(GemStat.parseGem("unknown", c.getConfigurationSection("gems.unknown"), sets).isEmpty());
    assertTrue(GemStat.parseGem("unknown", c.getConfigurationSection("gems.unknown"), null).isEmpty());
    verify(logger, times(2)).warning(contains("unknown stat-bands set 'tier-9'"));
    assertTrue(GemStat.parseGem("nostat", c.getConfigurationSection("gems.nostat"), sets).isEmpty());
    assertTrue(GemStat.parseGem("blank", c.getConfigurationSection("gems.blank"), sets).isEmpty());
    verify(logger, times(2)).warning(contains("has bands but no stat"));
    assertEquals(
        List.of("epic armor 2.0-3.0"),
        describe(GemStat.parseGem("bad", c.getConfigurationSection("gems.bad"), sets)));
    verify(logger).warning(contains("common band 'lots' is not min-max"));
    verify(logger).warning(contains("'bad' rare band"));
    assertEquals(
        List.of("common armor 1.0-1.2", "legendary armor 1.9-2.0"),
        describe(GemStat.parseGem("both", c.getConfigurationSection("gems.both"), sets)));
    verify(logger).warning(contains("has both bands and rarities"));
    assertTrue(GemStat.parseGem("none", c.getConfigurationSection("gems.none"), sets).isEmpty());
    verify(logger).warning(contains("has neither bands nor rarities"));
  }

  @Test
  void olderRaritiesBlocksStillLoad() throws Exception {
    var c =
        config(
            "gems:\n"
                + "  ruby:\n"
                + "    rarities:\n"
                + "      common: {stat: ATTACK_DAMAGE, min: 2, max: 4}\n"
                + "      rare: {stat: ATTACK_DAMAGE, min: 5}\n"
                + "      bad: {}\n");
    assertEquals(
        List.of("common ATTACK_DAMAGE 2.0-4.0", "rare ATTACK_DAMAGE 5.0-5.0"),
        describe(GemStat.parseGem("ruby", c.getConfigurationSection("gems.ruby"), null)));
  }

  @Test
  void serverConfigIgnoresTheBundledBandsItFallsBackTo() throws Exception {
    // Bukkit's getConfig() uses the bundled config.yml as defaults for every key the server's file lacks.
    var bundled = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    var older =
        config(
            "gems:\n"
                + "  agate:\n"
                + "    rarities:\n"
                + "      common: {stat: max_health, min: 0.21, max: 0.31}\n"
                + "  coral:\n"
                + "    stat: physical_damage\n"
                + "    bands: tier-2\n");
    older.setDefaults(bundled);
    var sets = GemStat.ownSection(older, "stat-bands");
    assertNull(sets);
    assertEquals(
        List.of("common max_health 0.21-0.31"),
        describe(GemStat.parseGem("agate", older.getConfigurationSection("gems.agate"), sets)));
    // A set the server's file doesn't define is unknown, even though the bundled file has it.
    assertTrue(GemStat.parseGem("coral", older.getConfigurationSection("gems.coral"), sets).isEmpty());
    verify(logger).warning(contains("unknown stat-bands set 'tier-2'"));
    verify(logger, never()).warning(contains("both bands and rarities"));
  }

  @Test
  void bundledConfigGivesEveryGemFourBandsOfOneStat() {
    new ConfigLoader()
        .loadConfig(YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")));
    assertEquals(40, ConfigLoader.loadedGems.size());
    for (Gemstone gem : ConfigLoader.loadedGems) {
      assertEquals(
          List.of("common", "rare", "epic", "legendary"),
          gem.getStats().stream().map(GemStat::getId).toList(),
          gem.getId());
      assertEquals(1, gem.getStats().stream().map(GemStat::getStatId).distinct().count(), gem.getId());
    }
    Map<String, Gemstone> byId = new HashMap<>();
    for (Gemstone gem : ConfigLoader.loadedGems) byId.put(gem.getId(), gem);
    assertEquals(
        List.of(
            "common max_health 0.2-0.3",
            "rare max_health 0.3-0.4",
            "epic max_health 0.4-0.5",
            "legendary max_health 0.5-0.6"),
        describe(byId.get("agate").getStats()));
    assertEquals(
        List.of(
            "common physical_damage 2.0-2.2",
            "rare physical_damage 2.3-2.5",
            "epic physical_damage 2.6-2.8",
            "legendary physical_damage 2.9-3.0"),
        describe(byId.get("coral").getStats()));
    assertEquals(
        "legendary armor 0.8-1.0", describe(byId.get("sapphire").getStats()).getLast());
    verify(logger, never()).warning(anyString());
  }
}
