package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.geminfusion.InfusionMain;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

class GoldsmithInventoryManagerTest {
  ServerMock server;
  InfusionMain previous;
  Player player;
  ItemAPI items;
  MockedStatic<TLibs> libs;
  MockedStatic<JewelryProjectLoader> projects;
  LinkedHashMap<String, JewelryProject> registry = new LinkedHashMap<>();

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.getName()).thenReturn("GemInfusion");
    when(InfusionMain.plugin.namespace()).thenReturn("geminfusion");
    when(InfusionMain.plugin.getServer()).thenReturn(server);
    player = mock(Player.class);
    items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getItemAPI).thenReturn(items);
    projects = mockStatic(JewelryProjectLoader.class);
    projects.when(JewelryProjectLoader::get).thenReturn(registry);
  }

  @AfterEach
  void cleanup() {
    projects.close();
    libs.close();
    InfusionMain.plugin = previous;
    MockBukkit.unmock();
  }

  JewelryProject project(String id, String path, String tier, boolean gem) {
    JewelryProject p = mock(JewelryProject.class);
    when(p.getId()).thenReturn(id);
    when(p.getItem()).thenReturn(path);
    when(p.getName()).thenReturn("Fancy " + id);
    when(p.getTierId()).thenReturn(tier);
    when(p.requiresGem()).thenReturn(gem);
    when(p.getMaterialsByType()).thenReturn(Map.of("gold", 2));
    registry.put(id, p);
    return p;
  }

  Inventory opened() {
    var capture = org.mockito.ArgumentCaptor.forClass(Inventory.class);
    verify(player).openInventory(capture.capture());
    return capture.getValue();
  }

  @Test
  void validIconsAreClonedDecoratedAndRemainingSlotsFilled() {
    project("ring", "minecraft.DIAMOND", "mAJOR", true);
    project("band", "ia.band", null, true);
    project("blank", "minecraft.GOLD_INGOT", " ", true);
    project("key", "minecraft.GOLD_NUGGET", null, false);
    ItemStack original = new ItemStack(Material.DIAMOND, 6);
    when(items.getCreator().getItemFromPath("minecraft.DIAMOND")).thenReturn(original);
    when(items.getCreator().getItemFromPath("ia.band"))
        .thenReturn(new ItemStack(Material.GOLD_INGOT));
    when(items.getCreator().getItemFromPath("minecraft.GOLD_INGOT"))
        .thenReturn(new ItemStack(Material.GOLD_INGOT));
    when(items.getCreator().getItemFromPath("minecraft.GOLD_NUGGET"))
        .thenReturn(new ItemStack(Material.GOLD_NUGGET));
    assertEquals(4, new GoldsmithInventoryManager().openMenu(player));
    Inventory inv = opened();
    ItemStack icon = inv.getItem(0);
    assertEquals(1, icon.getAmount());
    assertEquals(6, original.getAmount());
    assertEquals("Fancy ring", icon.getItemMeta().getDisplayName());
    assertTrue(icon.getItemMeta().getLore().contains("§7Tier: §eMajor"));
    assertTrue(icon.getItemMeta().getLore().contains("§7Requires §a1 §7infused gem"));
    assertEquals(
        "ring",
        icon.getItemMeta()
            .getPersistentDataContainer()
            .get(GoldsmithInventoryManager.projectKey(), PersistentDataType.STRING));
    assertTrue(inv.getItem(1).getItemMeta().getLore().contains("§7Tier: §eGreater"));
    assertTrue(inv.getItem(2).getItemMeta().getLore().contains("§7Tier: §eGreater"));
    assertTrue(
        inv.getItem(3).getItemMeta().getLore().stream().noneMatch(l -> l.startsWith("§7Tier")));
    for (int slot = 4; slot < 27; slot++)
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(slot).getType());
  }

  @Test
  void invalidPathsAndMissingItemsSkipSlotsAndEmptyMenuIsLogged() {
    project("null", null, "x", false);
    project("blank", " ", "x", false);
    project("missing", "missing", "x", false);
    project("air", "air", "x", false);
    project("ia", "IA.bad", "x", false);
    when(items.getCreator().getItemFromPath("missing")).thenReturn(null);
    when(items.getCreator().getItemFromPath("air")).thenReturn(new ItemStack(Material.AIR));
    when(items.getCreator().getItemFromPath("IA.bad")).thenReturn(new ItemStack(Material.DIRT));
    try (var log = mockStatic(GoldsmithLog.class)) {
      assertEquals(0, new GoldsmithInventoryManager().openMenu(player));
      for (ItemStack item : opened().getContents())
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, item.getType());
      log.verify(() -> GoldsmithLog.warn(contains("zero valid")));
    }
  }

  @Test
  void menuStopsAtInventoryCapacity() {
    when(items.getCreator().getItemFromPath("minecraft.DIRT"))
        .thenAnswer(inv -> new ItemStack(Material.DIRT));
    for (int i = 0; i < 30; i++) project("p" + i, "minecraft.DIRT", "greater", false);
    assertEquals(27, new GoldsmithInventoryManager().openMenu(player));
    assertEquals(27, opened().getSize());
  }
}
