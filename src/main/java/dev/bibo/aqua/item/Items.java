package dev.bibo.aqua.item;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.Keys;
import dev.bibo.aqua.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.Arrays;

/** Factory + detection + recipes for the Holy Water and the Water Catalyst items. */
public final class Items {

    private final AquaWaterPlugin plugin;
    private final Keys keys;

    public Items(AquaWaterPlugin plugin) {
        this.plugin = plugin;
        this.keys = plugin.keys();
    }

    public ItemStack holyWater(int amount) {
        ItemStack it = new ItemStack(Material.POTION, Math.max(1, amount));
        if (it.getItemMeta() instanceof PotionMeta meta) {
            try {
                meta.setBasePotionType(PotionType.WATER);
            } catch (Throwable ignored) {
            }
            meta.setColor(Color.fromRGB(64, 164, 223));
            meta.setDisplayName(Msg.color("&b&lСвятая Вода"));
            meta.setLore(Arrays.asList(
                    Msg.color("&7Выпей, чтобы пробудить в себе"),
                    Msg.color("&7силу управления водой."),
                    Msg.color("&8— благословение глубин —")));
            meta.getPersistentDataContainer().set(keys.holyWater, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    public ItemStack catalyst() {
        ItemStack it = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color("&3&lВодный Тотем"));
            meta.setLore(Arrays.asList(
                    Msg.color("&7Держи &fв левой руке&7, правая рука пуста."),
                    Msg.color(" "),
                    Msg.color("&7Первая цифра &8— &bгруппа"),
                    Msg.color("&7Вторая цифра &8— &bспособность в ней"),
                    Msg.color("&7Клавиша &fF &8— &bсброс, выбрать заново"),
                    Msg.color("&7ПКМ &8— &bнабрать воду &8(&7Shift+ПКМ &8— развеять)"),
                    Msg.color("&7ЛКМ &8— &bвыпустить способность"),
                    Msg.color(" "),
                    Msg.color("&8Цифра, под которой лежит предмет, просто"),
                    Msg.color("&8берёт его — держи нижние слоты пустыми."),
                    Msg.color("&8Предмет в руке — плагин молчит совсем."),
                    Msg.color("&8Несбрасываемый, не теряется при смерти.")));
            meta.getPersistentDataContainer().set(keys.catalyst, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    public boolean isHolyWater(ItemStack it) {
        return has(it, keys.holyWater);
    }

    public boolean isCatalyst(ItemStack it) {
        return has(it, keys.catalyst);
    }

    public boolean hasCatalystInInventory(Player p) {
        if (isCatalyst(p.getInventory().getItemInOffHand())) return true;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isCatalyst(it)) return true;
        }
        return false;
    }

    /** Give the totem into the off-hand (powers only work with it in the left hand). */
    public void giveCatalyst(Player p) {
        ItemStack cat = catalyst();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) {
            p.getInventory().setItemInOffHand(cat);
        } else {
            p.getInventory().addItem(cat);
        }
    }

    private boolean has(ItemStack it, org.bukkit.NamespacedKey key) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void registerRecipes() {
        try {
            Bukkit.removeRecipe(keys.holyWaterRecipe);
            Bukkit.removeRecipe(keys.catalystRecipe);
        } catch (Throwable ignored) {
        }

        ShapelessRecipe holy = new ShapelessRecipe(keys.holyWaterRecipe, holyWater(1));
        holy.addIngredient(Material.GLASS_BOTTLE);
        holy.addIngredient(Material.GHAST_TEAR);
        holy.addIngredient(Material.GLOWSTONE_DUST);

        ShapelessRecipe cat = new ShapelessRecipe(keys.catalystRecipe, catalyst());
        cat.addIngredient(Material.HEART_OF_THE_SEA);
        cat.addIngredient(Material.NAUTILUS_SHELL);
        cat.addIngredient(2, Material.PRISMARINE_CRYSTALS);

        try {
            Bukkit.addRecipe(holy);
            Bukkit.addRecipe(cat);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register recipes: " + t.getMessage());
        }
    }
}
