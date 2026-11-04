package fr.muscu.menu;

import fr.muscu.MuscuPlugin;
import fr.muscu.Profil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Base des menus Muscu : un coffre en lecture seule, chaque case pouvant avoir une action au clic.
 * Les menus se redessinent toutes les secondes tant qu'ils sont ouverts (progression en direct).
 */
public abstract class Menu implements InventoryHolder {

    protected final MuscuPlugin plugin;
    protected final Player joueur;
    private final Inventory inventaire;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    protected Menu(MuscuPlugin plugin, Player joueur, int lignes, Component titre) {
        this.plugin = plugin;
        this.joueur = joueur;
        this.inventaire = Bukkit.createInventory(this, lignes * 9, titre);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventaire;
    }

    public final void ouvrir() {
        rafraichir();
        joueur.openInventory(inventaire);
    }

    public final void rafraichir() {
        Profil profil = plugin.progression().profil(joueur);
        if (profil == null) return;
        actions.clear();
        ItemStack fond = Icone.fond(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inventaire.getSize(); i++) inventaire.setItem(i, fond);
        dessiner(profil);
    }

    protected abstract void dessiner(Profil profil);

    protected void placer(int case_, ItemStack item) {
        inventaire.setItem(case_, item);
    }

    protected void placer(int case_, ItemStack item, Runnable action) {
        inventaire.setItem(case_, item);
        actions.put(case_, action);
    }

    /** Bouton "← Retour" vers le menu principal. */
    protected void retour(int case_) {
        placer(case_, Icone.de(Material.ARROW).nom(fr.muscu.Txt.t("← Retour", NamedTextColor.YELLOW)).build(),
                () -> new MenuPrincipal(plugin, joueur).ouvrir());
    }

    final void clic(int case_) {
        Runnable action = actions.get(case_);
        if (action == null) return;
        joueur.playSound(joueur.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        action.run();
    }
}
