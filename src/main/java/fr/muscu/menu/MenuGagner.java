package fr.muscu.menu;

import fr.muscu.MuscuPlugin;
import fr.muscu.Profil;
import fr.muscu.Reglages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static fr.muscu.Txt.de;
import static fr.muscu.Txt.gras;
import static fr.muscu.Txt.nb;
import static fr.muscu.Txt.pts;
import static fr.muscu.Txt.t;

/** Toutes les façons de gagner des points, avec où en est le joueur pour chacune. */
public final class MenuGagner extends Menu {

    public MenuGagner(MuscuPlugin plugin, Player joueur) {
        super(plugin, joueur, 4, gras("⚡ Gagner des points", NamedTextColor.DARK_GREEN));
    }

    @Override
    protected void dessiner(Profil p) {
        Reglages r = plugin.reglages();

        // ── Activités (ligne 2) ──
        int case_ = 10;
        for (Reglages.Activite a : r.activites) {
            Icone ic = Icone.de(a.icone()).sansAttributs()
                    .nom(gras(a.nom(), NamedTextColor.YELLOW))
                    .lore(regle(a.chaque(), a.singulier(), a.pluriel(), a.points()));
            if (a.seuil() > 1 && a.points() > 0) {
                int restant = (a.seuil() - p.reste(a.id()) + a.unites() - 1) / a.unites();
                ic.lore(t("Encore " + nb(restant, a.singulier(), a.pluriel()) + " avant le prochain point", NamedTextColor.GRAY));
            }
            placer(case_++, ic.build());
        }

        placer(case_++, Icone.de(Material.COOKED_BEEF)
                .nom(gras("Manger de la viande cuite", NamedTextColor.YELLOW))
                .lore(regle(1, "viande cuite mangée", "viandes cuites mangées", r.viandePoints))
                .vide().lore(liste(r.viandes)).build());

        placer(case_, Icone.de(plugin.shaker().creer())
                        .nom(gras("Boire un shaker", NamedTextColor.YELLOW))
                        .vide().lore(t("» Clic pour voir la recette", NamedTextColor.YELLOW)).build(),
                () -> new MenuShaker(plugin, joueur).ouvrir());

        // ── Minage (ligne 3) ──
        case_ = 19;
        for (Reglages.GroupeMinage g : r.groupesMinage) {
            if (case_ > 23) break;
            Icone ic = Icone.de(g.icone())
                    .nom(gras("⛏ " + g.nom(), NamedTextColor.AQUA))
                    .lore(regle(g.chaque(), "minerai", "minerais", g.points()));
            if (g.chaque() > 1 && g.points() > 0) {
                int restant = g.chaque() - p.reste("mine_" + g.id());
                ic.lore(t("Encore " + nb(restant, "minerai", "minerais") + " avant le prochain point", NamedTextColor.GRAY));
            }
            ic.vide().lore(liste(g.blocs()));
            if (r.ignorerMineraisPoses) ic.lore(t("Les minerais posés à la main ne comptent pas", NamedTextColor.DARK_GRAY));
            placer(case_++, ic.build());
        }

        // ── Duo ──
        if (r.duoActif) {
            Player partenaire = plugin.progression().partenaire(joueur);
            placer(25, Icone.de(Material.LEAD).brillant(partenaire != null)
                    .nom(gras("✦ S'entraîner à deux", NamedTextColor.AQUA))
                    .lore(t("À moins de " + (int) r.duoRayon + " blocs d'un autre joueur :", NamedTextColor.GRAY),
                            t("+" + r.duoPourcent + "% sur tous tes points", NamedTextColor.GREEN))
                    .vide()
                    .lore(partenaire != null
                            ? t("✔ Actif avec " + partenaire.getName(), NamedTextColor.AQUA)
                            : t("✖ Personne à côté de toi", NamedTextColor.DARK_GRAY))
                    .build());
        }

        retour(31);
    }

    /** "10 sauts → 1 pt" ou "Chaque mob tué → 2 pts". */
    private static Component regle(int chaque, String singulier, String pluriel, int points) {
        String quoi = chaque == 1 ? "Chaque " + singulier : chaque + " " + pluriel;
        return de(t(quoi, NamedTextColor.WHITE), t(" → ", NamedTextColor.DARK_GRAY), t(pts(points), NamedTextColor.GREEN));
    }

    /** Noms des blocs/objets traduits par le jeu, 3 par ligne (les variantes "des abîmes" sont regroupées). */
    private static List<Component> liste(Collection<Material> materiaux) {
        List<Component> noms = new ArrayList<>();
        boolean abimes = false;
        for (Material m : materiaux) {
            if (m.name().startsWith("DEEPSLATE_")) {
                abimes = true;
                continue;
            }
            noms.add(Component.translatable(m.translationKey()));
        }
        List<Component> lignes = new ArrayList<>();
        for (int i = 0; i < noms.size(); i += 3) {
            List<Component> morceau = noms.subList(i, Math.min(noms.size(), i + 3));
            Component ligne = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")), morceau);
            lignes.add(de(t(i == 0 ? "• " : "  ", NamedTextColor.DARK_GRAY), ligne.color(NamedTextColor.GRAY)));
        }
        if (abimes) lignes.add(t("  (+ leurs versions des abîmes)", NamedTextColor.DARK_GRAY));
        return lignes;
    }
}
