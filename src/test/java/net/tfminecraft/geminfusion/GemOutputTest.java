package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import java.util.function.Consumer;
import net.Indyuce.mmoitems.*;
import net.Indyuce.mmoitems.api.item.mmoitem.*;
import net.Indyuce.mmoitems.stat.data.*;
import net.Indyuce.mmoitems.stat.type.*;
import net.tfminecraft.geminfusion.goldsmith.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.*;

class GemOutputTest {
  InfusionMain previous;
  MMOItems previousMmo;
  io.lumine.mythic.lib.MythicLib previousLib;
  AttributeInfluence previousInfusion, previousJewelry;
  MockedStatic<NBTItem> nbtApi;
  NBTItem nbt;
  Gemstone gem;
  GemRarity rarity;
  Player player;

  @BeforeEach
  void setup() throws Exception {
    MockBukkit.mock();
    previousLib = io.lumine.mythic.lib.MythicLib.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.namespace()).thenReturn("geminfusion");
    when(InfusionMain.plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    previousMmo = MMOItems.plugin;
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(io.lumine.mythic.lib.MythicLib.plugin.namespace()).thenReturn("mythiclib");
    previousInfusion = AttributeInfluence.infusion;
    previousJewelry = AttributeInfluence.jewelry;
    AttributeInfluence.infusion = mock(AttributeInfluence.class);
    AttributeInfluence.jewelry = mock(AttributeInfluence.class);
    nbt = mock(NBTItem.class);
    nbtApi = mockStatic(NBTItem.class);
    nbtApi.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
    gem = new Gemstone();
    gem.setId("ruby");
    gem.setName("Ruby");
    gem.setMMOItem("GEM_STONE.RUBY");
    gem.setSocketColour("Red");
    gem.setSocketNameColour(ChatColor.RED);
    rarity = new GemRarity("rare", config("name: Rare\nchance: 1"));
    player = mock(Player.class);
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedGems.add(gem);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("GEM_STONE");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("RUBY");
    var attack = ItemStats.ATTACK_DAMAGE;
    when(MMOItems.plugin.getStats().get("ATTACK_DAMAGE")).thenReturn(attack);
  }

  @AfterEach
  void cleanup() {
    if (nbtApi != null) nbtApi.close();
    io.lumine.mythic.lib.MythicLib.plugin = previousLib;
    InfusionMain.plugin = previous;
    MMOItems.plugin = previousMmo;
    AttributeInfluence.infusion = previousInfusion;
    AttributeInfluence.jewelry = previousJewelry;
    ConfigLoader.loadedGems.clear();
    MockBukkit.unmock();
  }

  static YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  GemStat stat(String rarityId, String id, double value) throws Exception {
    return GemStat.parse("ruby", rarityId, config("stat: " + id + "\nmin: " + value));
  }

  ItemStack tagged(Material material) {
    ItemStack item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.setDisplayName("Original");
    item.setItemMeta(meta);
    return item;
  }

  MockedConstruction<LiveMMOItem> live(Consumer<LiveMMOItem> configure) {
    return mockConstruction(
        LiveMMOItem.class,
        withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
        (m, ctx) -> {
          when(m.computeStatHistory(any())).thenReturn(null);
          when(m.getData(any())).thenReturn(null);
          configure.accept(m);
        });
  }

  @Test
  void statRollUsesMatchingRarityAndAttributeThenClampsSuccessRange() throws Exception {
    gem.addStat(stat("other", "ATTACK_DAMAGE", 9));
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 2));
    MMOItem output = mock(MMOItem.class);
    when(AttributeInfluence.infusion.forPlayer(player)).thenReturn(.25);
    InfusedGemBuilder.rollStats(output, gem, rarity, 100, player);
    var value = ArgumentCaptor.forClass(DoubleData.class);
    verify(output).setData(eq(ItemStats.ATTACK_DAMAGE), value.capture());
    assertEquals(2.5, value.getValue().getValue());
    verify(output)
        .setData(eq(ItemStats.SUCCESS_RATE), argThat(v -> ((DoubleData) v).getValue() == 40));
    gem.setStats(new ArrayList<>());
    InfusedGemBuilder.rollStats(output, gem, rarity, 1, player);
    verify(output, times(2)).setData(eq(ItemStats.SUCCESS_RATE), any());
    gem.addStat(stat("rare", "MISSING", 1));
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    InfusedGemBuilder.rollStats(output, gem, rarity, 1, player);
    InfusionMain.plugin = null;
    InfusedGemBuilder.rollStats(output, gem, rarity, 1, player);
  }

  @Test
  void cosmeticsUpdateNameHistoryLoreAndRarityWithoutChangingStats() {
    MMOItem output = mock(MMOItem.class);
    InfusedGemBuilder.applyCosmetics(output, gem, rarity);
    var name = ArgumentCaptor.forClass(StringData.class);
    verify(output).replaceData(eq(ItemStats.NAME), name.capture());
    assertEquals("Rare Infused Ruby", name.getValue().getString());
    StringData existing = new StringData("Old");
    when(output.getData(ItemStats.NAME)).thenReturn(existing);
    StatHistory history = mock(StatHistory.class);
    NameData original = new NameData("Old");
    when(history.getOriginalData()).thenReturn(original);
    when(output.computeStatHistory(ItemStats.NAME)).thenReturn(history);
    InfusedGemBuilder.applyCosmetics(output, gem, rarity);
    assertEquals("Rare Infused Ruby", existing.getString());
    assertEquals("Rare Infused Ruby", original.getString());
    verify(output).setStatHistory(ItemStats.NAME, history);
    verify(output, times(2)).setData(eq(ItemStats.LORE), any(StringListData.class));
  }

  @Test
  void buildsAndRebuildsGemWithCosmeticsGlintAndPdc() {
    ItemStack blank = tagged(Material.DIAMOND), result = tagged(Material.DIAMOND);
    Gemstone template = mock(Gemstone.class);
    when(template.getMMOItem()).thenReturn(mock(MMOItem.class, RETURNS_DEEP_STUBS));
    when(template.getMMOItem().newBuilder().build()).thenReturn(blank);
    when(template.getName()).thenReturn("Ruby");
    when(template.getSocketColour()).thenReturn("Red");
    when(template.getSocketNameColour()).thenReturn(ChatColor.RED);
    try (var constructed = live(m -> when(m.newBuilder().build()).thenReturn(result))) {
      assertSame(result, InfusedGemBuilder.buildInfusedGem(template, rarity, 3, player));
      assertEquals("rare", GemRarityPdc.read(result));
      assertTrue(result.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
      assertSame(result, InfusedGemBuilder.applyCosmeticsToItem(blank, gem, rarity));
    }
    assertNull(InfusedGemBuilder.applyCosmeticsToItem(null, gem, rarity));
    ItemStack air = new ItemStack(Material.AIR);
    assertSame(air, InfusedGemBuilder.applyCosmeticsToItem(air, gem, rarity));
    assertSame(blank, InfusedGemBuilder.applyCosmeticsToItem(blank, null, rarity));
    assertSame(blank, InfusedGemBuilder.applyCosmeticsToItem(blank, gem, null));
    assertNull(InfusedGemBuilder.finalizeItem(null, "rare"));
    assertSame(air, InfusedGemBuilder.finalizeItem(air, "rare"));
  }

  @Test
  void infusedValidatorRecognizesOnlyConfiguredInfusedGemsAndCandidates() {
    ItemStack item = tagged(Material.DIAMOND);
    assertFalse(InfusedGemValidator.isInfused(null));
    assertFalse(InfusedGemValidator.isInfused(new ItemStack(Material.AIR)));
    assertFalse(InfusedGemValidator.isGemstoneCandidate(null));
    assertFalse(InfusedGemValidator.isGemstoneCandidate(new ItemStack(Material.AIR)));
    when(nbt.hasType()).thenReturn(false);
    assertFalse(InfusedGemValidator.isInfused(item));
    assertFalse(InfusedGemValidator.isGemstoneCandidate(item));
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    assertFalse(InfusedGemValidator.isInfused(item));
    assertTrue(InfusedGemValidator.isGemstoneCandidate(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Infused Gemstone");
    assertTrue(InfusedGemValidator.isInfused(item));
    assertTrue(InfusedGemValidator.isGemstoneCandidate(item));
    ConfigLoader.loadedGems.clear();
    assertFalse(InfusedGemValidator.isInfused(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("other");
    assertFalse(InfusedGemValidator.isGemstoneCandidate(item));
    ConfigLoader.loadedGems.add(gem);
    assertTrue(InfusedGemValidator.isGemstoneCandidate(item));
  }

  @Test
  void jewelryRejectsMissingInputsInvalidGemsAndUnreadableStats() {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    ItemStack gemItem = tagged(Material.DIAMOND);
    try (var log = mockStatic(GoldsmithLog.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var constructed = live(m -> when(m.getStats()).thenReturn(Set.of()))) {
      assertNull(JewelryOutput.build(station, player));
      when(station.getProject()).thenReturn(project);
      assertNull(JewelryOutput.build(station, player));
      when(station.getGem()).thenReturn(gemItem);
      assertNull(JewelryOutput.build(station, player));
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      when(nbt.hasType()).thenReturn(false);
      assertNull(JewelryOutput.build(station, player));
      when(nbt.hasType()).thenReturn(true);
      assertNull(JewelryOutput.build(station, player));
      ConfigLoader.loadedGems.clear();
      assertNull(JewelryOutput.build(station, player));
    }
  }

  @Test
  void jewelryCarriesScaledStatIntoFreshHistoryAndQualityLore() throws Exception {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    ItemStack gemItem = tagged(Material.DIAMOND),
        base = tagged(Material.GOLD_INGOT),
        out = tagged(Material.GOLD_INGOT);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(station.getRecipePercent()).thenReturn(90.0);
    when(station.getHitPercent()).thenReturn(80.0);
    when(project.getItem()).thenReturn("m.ring");
    when(project.getTierMultiplier()).thenReturn(.5);
    when(AttributeInfluence.jewelry.forPlayer(player)).thenReturn(.2);
    gem.addStat(stat("other", "MISSING", 3));
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 10));
    GemRarityPdc.write(gemItem, "rare");
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath("m.ring")).thenReturn(base);
    Quality quality = mock(Quality.class);
    when(quality.getName()).thenReturn("Fine");
    StatHistory history = mock(StatHistory.class);
    DoubleData original = new DoubleData(7);
    when(history.getOriginalData()).thenReturn(original);
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var qualityApi = mockStatic(QualityLoader.class);
        var constructed =
            live(
                m -> {
                  when(m.getData(ItemStats.ATTACK_DAMAGE)).thenReturn(new DoubleData(10));
                  when(m.newBuilder().build()).thenReturn(out);
                  when(m.computeStatHistory(ItemStats.ATTACK_DAMAGE)).thenReturn(history);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      qualityApi.when(() -> QualityLoader.getByAmount(80)).thenReturn(quality);
      qualityApi.when(() -> QualityLoader.resolveStatFactor(80)).thenReturn(50.0);
      GoldsmithMaterial material = mock(GoldsmithMaterial.class);
      when(material.getPath()).thenReturn("m.materials.shiny_gold");
      when(station.getDepositedByMaterial()).thenReturn(Map.of(material, 3));
      JewelryCraftResult result = JewelryOutput.build(station, player);
      assertNotNull(result);
      assertSame(out, result.getItem());
      assertEquals(Map.of("m.materials.shiny_gold", 3), GoldsmithProvenance.read(out));
      assertEquals(80, result.getFinishedTotal());
      assertEquals(90, result.getRecipePercent());
      assertEquals(80, result.getHitPercent());
      assertEquals(50, result.getStatCarryPercent());
      assertSame(quality, result.getQuality());
      assertEquals(0, original.getValue());
      verify(constructed.constructed().getLast())
          .setData(
              eq(ItemStats.ATTACK_DAMAGE),
              argThat(v -> v instanceof DoubleData d && d.getValue() == 3.0));
      verify(history)
          .registerExternalData(argThat(v -> v instanceof DoubleData d && d.getValue() == 3.0));
    }
  }

  @Test
  void jewelryFallsBackAfterMatchingNonnumericStatAndHandlesFailedBuild() throws Exception {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    ItemStack gemItem = tagged(Material.DIAMOND), base = tagged(Material.DIAMOND);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(project.getItem()).thenReturn("m.ring");
    gem.addStat(stat("rare", "NAME", 1));
    gem.addStat(stat("other", "ATTACK_DAMAGE", 2));
    GemRarityPdc.write(gemItem, "rare");
    when(MMOItems.plugin.getStats().get("NAME")).thenReturn(ItemStats.NAME);
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath("m.ring")).thenReturn(base);
    StatHistory history = mock(StatHistory.class);
    when(history.getOriginalData()).thenReturn(new StringData("unexpected legacy value"));
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var quality = mockStatic(QualityLoader.class);
        var log = mockStatic(GoldsmithLog.class);
        var constructed =
            live(
                m -> {
                  when(m.getData(ItemStats.NAME)).thenReturn(new StringData("Name"));
                  when(m.getData(ItemStats.ATTACK_DAMAGE)).thenReturn(new DoubleData(2));
                  when(m.newBuilder().build()).thenReturn(null);
                  when(m.computeStatHistory(ItemStats.ATTACK_DAMAGE)).thenReturn(history);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      assertNull(JewelryOutput.build(station, player));
      log.verify(() -> GoldsmithLog.warn(contains("Could not build jewelry item")));
      GemRarityPdc.write(gemItem, "absent");
      assertNull(JewelryOutput.build(station, player));
    }
  }

  @Test
  void jewelryHandlesFallbackStatsInvalidOutputPathsAndAbsentHistory() throws Exception {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    ItemStack gemItem = tagged(Material.DIAMOND),
        base = tagged(Material.DIAMOND),
        out = tagged(Material.DIAMOND);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(project.getTierMultiplier()).thenReturn(1.0);
    gem.addStat(stat("other", "SUCCESS_RATE", 1));
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 1));
    gem.addStat(stat("x", "MISSING", 1));
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath(any())).thenReturn(base);
    ItemStat<?, ?> nullId = mock(ItemStat.class);
    ItemStat<?, ?> dash = mock(ItemStat.class);
    when(dash.getId()).thenReturn("SUCCESS-RATE");
    LinkedHashSet<ItemStat> all =
        new LinkedHashSet<>(
            Arrays.asList(
                null,
                nullId,
                ItemStats.SUCCESS_RATE,
                dash,
                ItemStats.NAME,
                ItemStats.ATTACK_DAMAGE));
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var quality = mockStatic(QualityLoader.class);
        var log = mockStatic(GoldsmithLog.class);
        var constructed =
            live(
                m -> {
                  when(m.getStats()).thenReturn(all);
                  when(m.getData(ItemStats.NAME)).thenReturn(new StringData("Name"));
                  when(m.getData(ItemStats.ATTACK_DAMAGE)).thenReturn(new DoubleData(2));
                  when(m.newBuilder().build()).thenReturn(out);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      assertNotNull(JewelryOutput.build(station, player));
      gem.setStats(List.of(stat("x", "SUCCESS-RATE", 1), stat("x", "MISSING", 1)));
      assertNotNull(JewelryOutput.build(station, player));
      when(project.getItem()).thenReturn("ia.bad");
      when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(new ItemStack(Material.DIRT));
      assertNull(JewelryOutput.build(station, player));
      when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(null);
      assertNull(JewelryOutput.build(station, player));
      when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(base);
      when(MMOItems.plugin.getStats().get("ATTACK_DAMAGE")).thenReturn(null);
      assertNull(JewelryOutput.build(station, player));
    }
  }
}
