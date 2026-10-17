package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

class GemNameCutoffsTest {
  InfusionMain previous;

  @BeforeEach
  void setup() {
    previous = InfusionMain.plugin;
    D20Roll.load(null);
  }

  @AfterEach
  void cleanup() {
    InfusionMain.plugin = previous;
    D20Roll.load(null);
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedRarities.clear();
    GemNameCutoffs.load(List.of(), List.of());
  }

  static YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  static GemRarity rarity(String id, double chance) throws Exception {
    GemRarity rarity = new GemRarity(id, config("name: " + id));
    rarity.setChance(chance);
    return rarity;
  }

  static Gemstone gem(String id, String... blocks) throws Exception {
    Gemstone gem = new Gemstone();
    gem.setId(id);
    for (int i = 0; i < blocks.length; i += 4) {
      gem.addStat(
          GemStat.parse(
              id,
              blocks[i],
              config("stat: " + blocks[i + 1] + "\nmin: " + blocks[i + 2] + "\nmax: " + blocks[i + 3])));
    }
    return gem;
  }

  /** Every stat the infusion can give this gem at the given modifier, with its chance. */
  static Map<Double, Double> results(Gemstone gem, List<GemRarity> rarities, int modifier) {
    double total = rarities.stream().mapToDouble(GemRarity::getChance).sum();
    Map<Double, Double> results = new TreeMap<>();
    for (GemRarity rarity : rarities) {
      GemStat block =
          gem.getStats().stream().filter(b -> b.getId().equals(rarity.getId())).findFirst().orElseThrow();
      for (int natural = 1; natural <= 20; natural++) {
        double stat = D20Roll.gemStat(block, new D20Roll(natural, modifier).position(D20Roll.flawlessDc));
        results.merge(stat, rarity.getChance() / total / 20, Double::sum);
      }
    }
    return results;
  }

  @Test
  void bundledGemsAreNamedByRankAtTheirRarityShares() {
    new ConfigLoader()
        .loadConfig(YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")));
    List<GemRarity> rarities = ConfigLoader.loadedRarities;
    Gemstone agate = ConfigLoader.loadedGems.getFirst();
    assertEquals("agate", agate.getId());
    GemRarity epic = ConfigLoader.findRarityById("epic");
    GemRarity legendary = ConfigLoader.findRarityById("legendary");
    // A Flawless epic agate (0.60) beats a fumbled legendary one (0.40), so it gets the better name.
    assertSame(legendary, GemNameCutoffs.name(agate, epic, 0.6));
    assertSame(ConfigLoader.findRarityById("rare"), GemNameCutoffs.name(agate, legendary, 0.4));
    assertSame(legendary, GemNameCutoffs.name(agate, epic, 0.5516));
    assertSame(epic, GemNameCutoffs.name(agate, legendary, 0.5515));
    assertSame(ConfigLoader.findRarityById("rare"), GemNameCutoffs.name(agate, epic, 0.3347));
    assertSame(ConfigLoader.findRarityById("common"), GemNameCutoffs.name(agate, epic, 0.3346));

    double total = rarities.stream().mapToDouble(GemRarity::getChance).sum();
    for (Gemstone gem : ConfigLoader.loadedGems) {
      // Better stats never get a lower name, and each name's share stays near its rarity's chance
      // (results that tie on one stat can't be split, so up to 2.25 points off for the bundled gems).
      Map<GemRarity, Double> shares = new HashMap<>();
      int previousRank = -1;
      for (Map.Entry<Double, Double> result : results(gem, rarities, 0).entrySet()) {
        GemRarity named = GemNameCutoffs.name(gem, null, result.getKey());
        int rank = rarities.indexOf(named);
        assertTrue(rank >= previousRank, gem.getId() + " at " + result.getKey());
        previousRank = rank;
        shares.merge(named, result.getValue(), Double::sum);
      }
      for (GemRarity rarity : rarities) {
        double share = shares.getOrDefault(rarity, 0.0);
        assertEquals(rarity.getChance() / total, share, 0.025, gem.getId() + " " + rarity.getId());
      }
    }
  }

  @Test
  void higherReferenceModifierRaisesTheCutoffs() throws Exception {
    List<GemRarity> rarities = List.of(rarity("common", 90), rarity("legendary", 10));
    Gemstone ruby = gem("ruby", "common", "ATTACK_DAMAGE", "1", "2", "legendary", "ATTACK_DAMAGE", "2", "3");
    GemNameCutoffs.load(List.of(ruby), rarities);
    // At +0 the best 10% of results start at 2.4; at +5 legendary draws roll higher, so the cutoff rises.
    assertSame(rarities.get(1), GemNameCutoffs.name(ruby, null, 2.4));
    assertSame(rarities.get(0), GemNameCutoffs.name(ruby, null, 2.3999));
    D20Roll.load(config("infusion:\n  name-reference-modifier: 5\n"));
    assertEquals(5, D20Roll.nameReferenceModifier);
    GemNameCutoffs.load(List.of(ruby), rarities);
    assertSame(rarities.get(0), GemNameCutoffs.name(ruby, null, 2.4));
    assertSame(rarities.get(1), GemNameCutoffs.name(ruby, null, 2.4421));
  }

  @Test
  void gemsThatCannotBeRankedKeepTheDrawnName() throws Exception {
    Logger logger = mock(Logger.class);
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.getLogger()).thenReturn(logger);
    GemRarity common = rarity("common", 3);
    GemRarity rare = rarity("rare", 1);
    // Rarities that can never be drawn neither name gems nor count towards the shares.
    List<GemRarity> rarities =
        List.of(common, rarity("never", 0), rare, rarity("broken", Double.POSITIVE_INFINITY));
    Gemstone ranked = gem("ranked", "common", "ARMOR", "1", "2", "rare", "ARMOR", "2", "3");
    Gemstone missing = gem("missing", "common", "ARMOR", "1", "2");
    Gemstone mixed = gem("mixed", "common", "ARMOR", "1", "2", "rare", "MAX_HEALTH", "2", "3");
    GemNameCutoffs.load(List.of(ranked, missing, mixed), rarities);
    verify(logger).info(startsWith("Gem 'ranked' names: rare from "));
    verify(logger).warning(contains("'missing'"));
    verify(logger).warning(contains("'mixed'"));
    assertSame(rare, GemNameCutoffs.name(ranked, common, 3.6));
    assertSame(common, GemNameCutoffs.name(ranked, rare, 0.8));
    assertSame(rare, GemNameCutoffs.name(ranked, rare, Double.NaN));
    assertSame(rare, GemNameCutoffs.name(missing, rare, 0.8));
    assertSame(rare, GemNameCutoffs.name(mixed, rare, 0.8));
    assertSame(rare, GemNameCutoffs.name(new Gemstone(), rare, 0.8));

    // With nothing drawable there is nothing to rank against; the default logger is used without a plugin.
    InfusionMain.plugin = null;
    GemNameCutoffs.load(List.of(ranked), List.of(rarity("never", 0)));
    assertSame(rare, GemNameCutoffs.name(ranked, rare, 0.8));
    GemNameCutoffs.load(List.of(ranked), List.of(common, rare));
    assertSame(rare, GemNameCutoffs.name(ranked, common, 3.6));
  }
}
