package fr.muscu.menu;

import fr.muscu.Defis;
import fr.muscu.MuscuPlugin;
import fr.muscu.Profil;
import fr.muscu.Reglages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;

import static fr.muscu.Txt.barre;
import static fr.muscu.Txt.de;
import static fr.muscu.Txt.gras;
import static fr.muscu.Txt.pts;
import static fr.muscu.Txt.t;

/**
 * <pre>
 *  ■ ■ ■ ■ [horloge] ■ ■ ■ ■
 *  ■ ■ [défi] ■ [défi] ■ [défi] ■ ■
 *  ■ ■ [tout réussir] ■ ■ ■ [duo] ■ ■
 *  ■ ■ ■ ■ [retour] ■ ■ ■ ■
 * </pre>
 */
public final class MenuDefis extends Menu {

    public MenuDefis(MuscuPlugin plugin, Player joueur) {
        super(plugin, joueur, 4, gras("☀ Défis du jour", NamedTextColor.GOLD));
    }

    @Override
    protected void dessiner(Profil p) {
        Defis defis = plugin.defis();
        Reglages r = plugin.reglages();
        retour(31);
        if (!defis.actif()) {
            placer(13, Icone.de(Material.BARRIER).nom(t("Les défis sont désactivés", NamedTextColor.RED)).build());
            return;
        }

        int n = defis.perso().size();
        int faits = defis.faits(p);
        Duration reste = defis.avantNouveaux();
        placer(4, Icone.de(Material.CLOCK)
                .nom(gras("☀ Défis du jour", NamedTextColor.GOLD))
                .lore(t("Réussis : " + faits + "/" + n, faits == n ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                        t("Nouveaux défis dans " + reste.toHours() + " h " + reste.toMinutesPart() + " min", NamedTextColor.GRAY))
                .build());

        // ── Défis perso ──
        for (int i = 0; i < n; i++) {
            Reglages.Defi d = defis.perso().get(i);
            int brut = defis.progres(p, i);
            boolean fini = brut >= d.seuil();
            Icone ic = Icone.de(defis.icone(d)).sansAttributs().brillant(fini)
                    .nom(fini ? de(t("✔ ", NamedTextColor.GREEN), defis.texte(d, false).color(NamedTextColor.GREEN))
                            : defis.texte(d, false).color(NamedTextColor.YELLOW))
                    .lore(progression(brut, d))
                    .lore(de(t("Récompense : ", NamedTextColor.GRAY), t("+" + pts(d.points()), NamedTextColor.GREEN)))
                    .vide()
                    .lore(fini ? t("✔ Réussi !", NamedTextColor.GREEN) : t("En cours...", NamedTextColor.DARK_GRAY));
            placer(casePerso(i, n), ic.build());
        }

        // ── Bonus "tout réussir" ──
        if (r.defisBonusComplet > 0) {
            placer(20, Icone.de(Material.NETHER_STAR).brillant(p.defisBonus)
                    .nom(gras("★ Tout réussir", NamedTextColor.GOLD))
                    .lore(t("Réussis les " + n + " défis du jour :", NamedTextColor.GRAY),
                            t("+" + pts(r.defisBonusComplet) + " en bonus", NamedTextColor.GREEN))
                    .vide()
                    .lore(p.defisBonus ? t("✔ Obtenu !", NamedTextColor.GREEN) : t(faits + "/" + n + " réussis", NamedTextColor.YELLOW))
                    .build());
        }

        // ── Défi duo ──
        Reglages.Defi duo = defis.duo();
        if (duo != null) {
            boolean fini = defis.duoFini();
            int part = defis.contribution(joueur);
            placer(24, Icone.de(defis.icone(duo)).sansAttributs().brillant(fini)
                    .nom(de(gras("✦ Défi duo : ", NamedTextColor.AQUA), defis.texte(duo, true).color(fini ? NamedTextColor.GREEN : NamedTextColor.WHITE)))
                    .lore(progression(defis.duoProgres(), duo))
                    .lore(t("Ta part : " + part / duo.type().unites, NamedTextColor.GRAY))
                    .lore(de(t("Récompense : ", NamedTextColor.GRAY), t("+" + pts(duo.points()) + " chacun", NamedTextColor.GREEN)))
                    .lore(t("(pour ceux qui ont participé)", NamedTextColor.DARK_GRAY))
                    .vide()
                    .lore(fini ? t("✔ Réussi ensemble !", NamedTextColor.GREEN) : t("Tout le monde compte !", NamedTextColor.AQUA))
                    .build());
        }
    }

    private static Component progression(int brut, Reglages.Defi d) {
        int fait = Math.min(d.quantite(), brut / d.type().unites);
        int cases = (int) ((long) Math.min(brut, d.seuil()) * 10 / d.seuil());
        return de(barre(cases, 10), t("  " + fait + "/" + d.quantite(), NamedTextColor.WHITE));
    }

    /** Cases de la 2e ligne, centrées selon le nombre de défis. */
    private static int casePerso(int i, int n) {
        if (n <= 4) return 13 - (n - 1) + 2 * i;
        return 13 - (n - 1) / 2 + i;
    }
}
