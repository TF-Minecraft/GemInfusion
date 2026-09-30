package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.geminfusion.InfusionMain;
import net.tfminecraft.geminfusion.PDCKeys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GoldsmithProvenanceTest {
  InfusionMain previous;

  @BeforeEach
  void setup() {
    MockBukkit.mock();
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.namespace()).thenReturn("geminfusion");
  }

  @AfterEach
  void cleanup() {
    InfusionMain.plugin = previous;
    MockBukkit.unmock();
  }

  private static GoldsmithMaterial material(String path) {
    GoldsmithMaterial material = mock(GoldsmithMaterial.class);
    when(material.getPath()).thenReturn(path);
    return material;
  }

  @Test
  void stampsTheDepositedMaterialsNotTheRecipe() {
    ItemStack ring = new ItemStack(Material.GOLD_INGOT);
    LinkedHashMap<GoldsmithMaterial, Integer> deposited = new LinkedHashMap<>();
    deposited.put(material("m.materials.shiny_gold"), 3);
    deposited.put(material("m.materials.rough_gold"), 1);
    deposited.put(material("m.materials.shiny_gold"), 2);

    GoldsmithProvenance.stamp(ring, deposited);

    assertEquals(
        Map.of("m.materials.shiny_gold", 5, "m.materials.rough_gold", 1),
        GoldsmithProvenance.read(ring));
  }

  @Test
  void stampsAnEmptyListWhenNothingWasDeposited() {
    ItemStack ring = new ItemStack(Material.GOLD_INGOT);
    GoldsmithProvenance.stamp(ring, Map.of());
    assertEquals(Map.of(), GoldsmithProvenance.read(ring));
  }

  @Test
  void readsNothingFromUnstampedOrUnreadableItems() {
    assertNull(GoldsmithProvenance.read(null));
    assertNull(GoldsmithProvenance.read(new ItemStack(Material.AIR)));
    assertNull(GoldsmithProvenance.read(new ItemStack(Material.GOLD_INGOT)));

    ItemStack broken = new ItemStack(Material.GOLD_INGOT);
    ItemMeta meta = broken.getItemMeta();
    meta.getPersistentDataContainer()
        .set(PDCKeys.goldsmithInputs(), PersistentDataType.STRING, "{not json");
    broken.setItemMeta(meta);
    assertNull(GoldsmithProvenance.read(broken));
  }
}
