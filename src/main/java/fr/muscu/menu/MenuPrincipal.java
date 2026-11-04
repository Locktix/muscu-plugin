package fr.muscu.menu;

import fr.muscu.Bonus;
import fr.muscu.ModeTableau;
import fr.muscu.MuscuPlugin;
import fr.muscu.Paliers;
import fr.muscu.Profil;
import fr.muscu.Reglages;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static fr.muscu.Txt.barre;
import static fr.muscu.Txt.de;
import static fr.muscu.Txt.duree;
import static fr.muscu.Txt.gras;
import static fr.muscu.Txt.pts;
import static fr.muscu.Txt.t;

/**
 * Fiche du joueur.
 * <pre>
 *  ■ ■ ■ ■ [tête] ■ ■ ■ ■
 *  ■ ■ [⛏][❤][⚔][➹][⬇] ■ ■      bonus actifs
 *  [ barre de progression x9 ]
 *  ■ [défis] ■ [gagner] ■ [niveaux] ■ [shaker] ■
 *  ■ ■ [stats] ■ [tableau] ■ [fermer] ■ ■
 * </pre>
 */
public final class MenuPrincipal extends Menu {

    public MenuPrincipal(MuscuPlugin plugin, Player joueur) {
        super(plugin, joueur, 5, gras("✦ MUSCU ✦", NamedTextColor.GOLD));
    }

    @Override
    protected void dessiner(Profil p) {
        Reglages r = plugin.reglages();
        boolean max = p.niveau >= Paliers.MAX;
        int besoin = r.besoin(p.niveau);

        // ── Tête du joueur : résumé ──
        int total = p.pts;
        for (int n = 0; n < p.niveau; n++) total += r.besoin(n);
        Icone tete = Icone.de(Material.PLAYER_HEAD).tete(joueur)
                .nom(gras("★ " + joueur.getName() + " ★", NamedTextColor.GOLD))
                .lore(de(t("Niveau ", NamedTextColor.YELLOW), t(p.niveau + " / " + Paliers.MAX, NamedTextColor.GOLD),
                        t("  · " + Paliers.get(p.niveau).nom(), NamedTextColor.GRAY)))
                .lore(de(barre(r.cases(p.niveau, p.pts, 20), 20), t("  " + r.progression(p.niveau, p.pts), NamedTextColor.GRAY)))
                .vide()
                .lore(t("Total gagné : " + pts(total), NamedTextColor.DARK_GRAY));
        if (r.duoActif) {
            Player partenaire = plugin.progression().partenaire(joueur);
            tete.vide().lore(partenaire != null
                    ? t("✦ Duo actif avec " + partenaire.getName() + " (" + (int) partenaire.getLocation().distance(joueur.getLocation())
                    + " blocs) : +" + r.duoPourcent + "%", NamedTextColor.AQUA)
                    : t("✦ Duo : personne à moins de " + (int) r.duoRayon + " blocs", NamedTextColor.DARK_GRAY));
        }
        if (plugin.defis().actif()) {
            int faits = plugin.defis().faits(p), n = plugin.defis().perso().size();
            tete.lore(t("☀ Défis du jour : " + faits + "/" + n, faits == n ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        }
        placer(4, tete.build());

        // ── Bonus ──
        int case_ = 11;
        for (Bonus b : Bonus.values()) {
            double v = b.valeur(p.niveau);
            boolean actif = v != 0;
            int prochain = b.prochainPalier(p.niveau);
            Icone ic = Icone.de(b.icone).sansAttributs().brillant(actif)
                    .nom(de(t(b.symbole + " ", b.couleur), t(b.nom, actif ? NamedTextColor.WHITE : NamedTextColor.GRAY)))
                    .lore(actif ? t("Actuel : " + b.format(v), NamedTextColor.GREEN) : t("Actuel : aucun", NamedTextColor.DARK_GRAY))
                    .lore(prochain > 0
                            ? t("Prochain : " + b.format(b.valeur(prochain)) + " au niveau " + prochain, NamedTextColor.YELLOW)
                            : t("Au maximum !", NamedTextColor.GOLD));
            placer(case_++, ic.build());
        }

        // ── Barre de progression (9 vitres) ──
        int pleines = r.cases(p.niveau, p.pts, 9);
        for (int i = 0; i < 9; i++) {
            Material vitre = max ? Material.YELLOW_STAINED_GLASS_PANE
                    : i < pleines ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            Icone ic = Icone.de(vitre);
            if (max) {
                ic.nom(gras("★ Niveau max atteint ★", NamedTextColor.GOLD)).lore(t("Respect.", NamedTextColor.GRAY));
            } else {
                ic.nom(de(t("Progression : ", NamedTextColor.YELLOW), t(p.pts + "/" + besoin + " pts", NamedTextColor.WHITE)))
                        .lore(t("Encore " + pts(besoin - p.pts) + " pour le niveau " + (p.niveau + 1), NamedTextColor.GRAY))
                        .lore(de(t("Prochain bonus : ", NamedTextColor.GRAY), t(Paliers.get(p.niveau + 1).bonus(), NamedTextColor.AQUA)));
            }
            placer(18 + i, ic.build());
        }

        // ── Boutons ──
        if (plugin.defis().actif()) {
            int faits = plugin.defis().faits(p), n = plugin.defis().perso().size();
            placer(28, Icone.de(Material.TARGET).brillant(faits < n)
                            .nom(gras("☀ Défis du jour", NamedTextColor.GOLD))
                            .lore(t(faits + "/" + n + " réussis", faits == n ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                            .lore(t("+ un défi duo à faire ensemble", NamedTextColor.AQUA))
                            .vide().lore(t("» Clic pour voir", NamedTextColor.YELLOW)).build(),
                    () -> new MenuDefis(plugin, joueur).ouvrir());
        }

        placer(30, Icone.de(Material.BOOK)
                        .nom(gras("⚡ Gagner des points", NamedTextColor.GREEN))
                        .lore(t("Sauts, sprint, nage, combat, minage,", NamedTextColor.GRAY),
                                t("viande cuite, shaker, duo...", NamedTextColor.GRAY))
                        .vide().lore(t("» Clic pour voir le détail", NamedTextColor.YELLOW)).build(),
                () -> new MenuGagner(plugin, joueur).ouvrir());

        placer(32, Icone.de(Material.EXPERIENCE_BOTTLE)
                        .nom(gras("★ Les " + Paliers.MAX + " niveaux", NamedTextColor.GOLD))
                        .lore(t("Tous les paliers et leurs bonus", NamedTextColor.GRAY))
                        .vide().lore(t("» Clic pour voir", NamedTextColor.YELLOW)).build(),
                () -> new MenuNiveaux(plugin, joueur).ouvrir());

        int attente = plugin.shaker().cooldownRestant(joueur);
        placer(34, Icone.de(plugin.shaker().creer())
                        .nom(gras("✚ Shaker de protéines", NamedTextColor.GOLD))
                        .vide()
                        .lore(attente > 0
                                ? t("⌛ Prochain shaker dans " + duree(attente), NamedTextColor.RED)
                                : t("✔ Prêt à boire !", NamedTextColor.GREEN))
                        .lore(t("» Clic pour voir la recette", NamedTextColor.YELLOW)).build(),
                () -> new MenuShaker(plugin, joueur).ouvrir());

        ModeTableau mode = p.tableau;
        Icone tableau = Icone.de(switch (mode) {
                    case HUD -> Material.GLOW_ITEM_FRAME;
                    case PERSO -> Material.LIME_DYE;
                    case CLASSEMENT -> Material.LIGHT_BLUE_DYE;
                    case MASQUE -> Material.GRAY_DYE;
                })
                .nom(de(gras("✦ Tableau : ", NamedTextColor.AQUA),
                        gras(mode.nom, mode == ModeTableau.MASQUE ? NamedTextColor.GRAY : NamedTextColor.GREEN)))
                .lore(t("Ce qui s'affiche sur ton écran", NamedTextColor.GRAY)).vide();
        for (ModeTableau m : ModeTableau.values()) {
            tableau.lore(m == mode
                    ? de(t("➜ " + m.nom, NamedTextColor.GREEN), t(" : " + m.description, NamedTextColor.WHITE))
                    : de(t("   " + m.nom, NamedTextColor.DARK_GRAY), t(" : " + m.description, NamedTextColor.DARK_GRAY)));
        }
        tableau.vide().lore(t("» Clic pour passer à : " + mode.suivant().nom, NamedTextColor.YELLOW));
        placer(40, tableau.build(),
                () -> {
                    plugin.progression().basculerTableau(joueur);
                    rafraichir();
                });

        placer(38, Icone.de(Material.WRITABLE_BOOK)
                        .nom(gras("✎ Mes stats", NamedTextColor.GOLD))
                        .lore(t("Tout ce que tu as fait depuis le début,", NamedTextColor.GRAY),
                                t("comparé aux autres joueurs", NamedTextColor.GRAY))
                        .vide().lore(t("» Clic pour voir", NamedTextColor.YELLOW)).build(),
                () -> new MenuStats(plugin, joueur).ouvrir());

        placer(42, Icone.de(Material.BARRIER).nom(t("Fermer", NamedTextColor.RED)).build(), joueur::closeInventory);
    }
}
