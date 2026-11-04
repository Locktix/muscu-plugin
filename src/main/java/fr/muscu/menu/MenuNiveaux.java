package fr.muscu.menu;

import fr.muscu.MuscuPlugin;
import fr.muscu.Paliers;
import fr.muscu.Profil;
import fr.muscu.Reglages;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import static fr.muscu.Txt.de;
import static fr.muscu.Txt.gras;
import static fr.muscu.Txt.pts;
import static fr.muscu.Txt.t;

/** Les 10 paliers : débloqués (brillants), en cours, verrouillés. */
public final class MenuNiveaux extends Menu {

    private static final int[] CASES = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public MenuNiveaux(MuscuPlugin plugin, Player joueur) {
        super(plugin, joueur, 4, gras("★ Les " + Paliers.MAX + " niveaux", NamedTextColor.GOLD));
    }

    @Override
    protected void dessiner(Profil p) {
        Reglages r = plugin.reglages();
        for (int n = 1; n <= Paliers.MAX && n <= CASES.length; n++) {
            Paliers.Palier palier = Paliers.get(n);
            boolean atteint = p.niveau >= n;
            boolean enCours = p.niveau + 1 == n;

            Icone ic = Icone.de(palier.icone()).quantite(n).sansAttributs().brillant(atteint)
                    .nom(de(gras("Niv " + n + " ", atteint ? NamedTextColor.GOLD : enCours ? NamedTextColor.YELLOW : NamedTextColor.GRAY),
                            t(palier.nom(), atteint ? NamedTextColor.YELLOW : enCours ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY)))
                    .lore(t(palier.bonus(), atteint ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                    .vide();
            if (atteint) {
                ic.lore(t("✔ Débloqué", NamedTextColor.GREEN));
            } else if (enCours) {
                ic.lore(de(t("➜ En cours : ", NamedTextColor.YELLOW), t(p.pts + "/" + r.besoin(p.niveau) + " pts", NamedTextColor.WHITE)));
            } else {
                int manque = -p.pts;
                for (int k = p.niveau; k < n; k++) manque += r.besoin(k);
                ic.lore(t("✖ Encore " + pts(manque) + " au total", NamedTextColor.DARK_GRAY));
            }
            placer(CASES[n - 1], ic.build());
        }
        retour(31);
    }
}
