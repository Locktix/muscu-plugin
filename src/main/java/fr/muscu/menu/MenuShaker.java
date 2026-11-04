package fr.muscu.menu;

import fr.muscu.MuscuPlugin;
import fr.muscu.Profil;
import fr.muscu.Reglages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static fr.muscu.Txt.duree;
import static fr.muscu.Txt.gras;
import static fr.muscu.Txt.t;

/** Recette du shaker, présentée comme dans un établi. */
public final class MenuShaker extends Menu {

    public MenuShaker(MuscuPlugin plugin, Player joueur) {
        super(plugin, joueur, 3, gras("✚ Recette du shaker", NamedTextColor.GOLD));
    }

    @Override
    protected void dessiner(Profil p) {
        Reglages r = plugin.reglages();
        placer(10, ingredient(Material.GLASS_BOTTLE).build());
        placer(11, ingredient(Material.MILK_BUCKET).lore(t("(le seau vide te revient)", NamedTextColor.DARK_GRAY)).build());
        placer(12, ingredient(Material.SUGAR).build());

        Icone viande = Icone.de(Material.COOKED_BEEF).nom(t("Viande cuite au choix", NamedTextColor.WHITE));
        for (Material m : r.viandes) viande.lore(Component.text("• ").append(Component.translatable(m.translationKey())));
        placer(13, viande.build());

        placer(14, Icone.de(Material.CRAFTING_TABLE)
                .nom(t("→ Dans un établi", NamedTextColor.YELLOW))
                .lore(t("Sans forme : place les 4 ingrédients", NamedTextColor.GRAY), t("où tu veux dans la grille.", NamedTextColor.GRAY))
                .build());

        int attente = plugin.shaker().cooldownRestant(joueur);
        placer(16, Icone.de(plugin.shaker().creer())
                .vide()
                .lore(attente > 0
                        ? t("⌛ Prochain shaker dans " + duree(attente), NamedTextColor.RED)
                        : t("✔ Prêt à boire !", NamedTextColor.GREEN))
                .build());

        retour(22);
    }

    private static Icone ingredient(Material m) {
        return Icone.de(m).lore(t("Ingrédient", NamedTextColor.DARK_GRAY));
    }
}
