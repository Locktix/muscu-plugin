package fr.muscu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import java.util.ArrayList;
import java.util.List;

/** Shaker de protéines : recette + objet. Même identifiant (muscu:shaker) que dans le datapack. */
public final class Shaker {

    public static final NamespacedKey CLE = new NamespacedKey("muscu", "shaker");

    private final MuscuPlugin plugin;

    public Shaker(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack creer() {
        Reglages r = plugin.reglages();
        ItemStack item = ItemStack.of(Material.HONEY_BOTTLE);
        item.setData(DataComponentTypes.ITEM_NAME, Txt.t("Shaker de protéines", NamedTextColor.GOLD));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                Txt.t("+" + Txt.pts(r.shakerPoints) + " de muscu", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Txt.t("Utilisable toutes les " + Txt.duree(r.shakerCooldown), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(4).saturation(2.4f).canAlwaysEat(true).build());
        if (r.shakerCooldown > 0) {
            item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(r.shakerCooldown).cooldownGroup(CLE).build());
        }
        return item;
    }

    /** Reconnaît le shaker (y compris ceux fabriqués avec le datapack) grâce à son groupe de cooldown. */
    public static boolean estShaker(ItemStack item) {
        if (item == null || item.getType() != Material.HONEY_BOTTLE) return false;
        UseCooldown cd = item.getData(DataComponentTypes.USE_COOLDOWN);
        return cd != null && cd.cooldownGroup() != null && CLE.asString().equals(cd.cooldownGroup().asString());
    }

    /** Secondes restantes avant de pouvoir reboire un shaker. */
    public int cooldownRestant(Player joueur) {
        return (joueur.getCooldown(CLE) + 19) / 20;
    }

    /** (Ré)enregistre la recette. resend = renvoyer le livre de recettes aux joueurs connectés. */
    public void enregistrerRecette(boolean resend) {
        if (Bukkit.getRecipe(CLE) != null) Bukkit.removeRecipe(CLE);
        ShapelessRecipe recette = new ShapelessRecipe(CLE, creer());
        recette.setCategory(CraftingBookCategory.MISC);
        recette.addIngredient(Material.GLASS_BOTTLE);
        recette.addIngredient(Material.MILK_BUCKET);
        recette.addIngredient(Material.SUGAR);
        recette.addIngredient(new RecipeChoice.MaterialChoice(new ArrayList<>(plugin.reglages().viandes)));
        Bukkit.addRecipe(recette);
        if (resend) Bukkit.updateRecipes();
    }

    public void assurerRecette() {
        if (Bukkit.getRecipe(CLE) == null) enregistrerRecette(true);
    }

    public void decouvrir(Player joueur) {
        joueur.discoverRecipe(CLE);
    }
}
