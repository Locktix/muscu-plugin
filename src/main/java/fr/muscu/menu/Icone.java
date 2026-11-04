package fr.muscu.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Fabrique les objets affichés dans les menus. */
public final class Icone {

    private final ItemStack item;
    private final List<Component> lore = new ArrayList<>();

    private Icone(ItemStack item) {
        this.item = item;
    }

    public static Icone de(Material materiau) {
        return new Icone(ItemStack.of(materiau));
    }

    public static Icone de(ItemStack base) {
        Icone i = new Icone(base.clone());
        ItemLore existant = base.getData(DataComponentTypes.LORE);
        if (existant != null) i.lore.addAll(existant.lines());
        return i;
    }

    /** Vitre sans infobulle pour le fond des menus. */
    public static ItemStack fond(Material vitre) {
        return de(vitre).sansInfobulle().build();
    }

    public Icone nom(Component nom) {
        item.setData(DataComponentTypes.CUSTOM_NAME, nom.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        return this;
    }

    public Icone lore(Component... lignes) {
        for (Component l : lignes) {
            lore.add(l.colorIfAbsent(NamedTextColor.GRAY).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return this;
    }

    public Icone lore(List<Component> lignes) {
        return lore(lignes.toArray(Component[]::new));
    }

    public Icone vide() {
        lore.add(Component.empty());
        return this;
    }

    public Icone brillant(boolean oui) {
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, oui);
        return this;
    }

    public Icone quantite(int n) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), n)));
        return this;
    }

    /** Cache les lignes d'attributs (dégâts d'épée, armure...) qui n'ont rien à faire dans un menu. */
    public Icone sansAttributs() {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build());
        return this;
    }

    public Icone sansInfobulle() {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return this;
    }

    public Icone tete(Player joueur) {
        item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(joueur.getPlayerProfile()));
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return item;
    }
}
