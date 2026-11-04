package fr.muscu.menu;

import fr.muscu.Classement;
import fr.muscu.MuscuPlugin;
import fr.muscu.Profil;
import fr.muscu.Stats;
import fr.muscu.Stats.Stat;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.muscu.Txt.de;
import static fr.muscu.Txt.gras;
import static fr.muscu.Txt.t;

/** Stats depuis le début, comparées aux autres joueurs (✔ = en tête). */
public final class MenuStats extends Menu {

    private static final int[] CASES = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public MenuStats(MuscuPlugin plugin, Player joueur) {
        super(plugin, joueur, 5, gras("✎ Mes stats", NamedTextColor.GOLD));
    }

    @Override
    protected void dessiner(Profil p) {
        Stats stats = plugin.stats();
        Map<Stat, Long> moi = stats.valeurs(joueur.getUniqueId());
        List<Classement.Entree> autres = plugin.classement().autres(joueur.getUniqueId());
        if (autres.size() > 3) autres = autres.subList(0, 3);
        List<Map<Stat, Long>> leurs = new ArrayList<>();
        for (Classement.Entree e : autres) leurs.add(stats.valeurs(e.id()));

        int enTete = 0;
        Stat[] liste = Stat.values();
        for (int i = 0; i < liste.length && i < CASES.length; i++) {
            Stat s = liste[i];
            long v = moi.get(s);
            long max = v;
            for (Map<Stat, Long> l : leurs) max = Math.max(max, l.get(s));
            boolean departage = max > 0 && !leurs.isEmpty();
            boolean jeMene = departage && v == max;
            if (jeMene) enTete++;

            Icone ic = Icone.de(s.icone).sansAttributs().brillant(jeMene)
                    .nom(gras(s.nom, NamedTextColor.YELLOW))
                    .lore(de(t("Toi : ", NamedTextColor.GRAY), t(Stats.format(s, v), NamedTextColor.WHITE),
                            t(jeMene ? "  ✔" : "", NamedTextColor.GREEN)));
            for (int k = 0; k < autres.size(); k++) {
                long lv = leurs.get(k).get(s);
                ic.lore(de(t(autres.get(k).nom() + " : ", NamedTextColor.DARK_GRAY), t(Stats.format(s, lv), NamedTextColor.GRAY),
                        t(departage && lv == max ? "  ✔" : "", NamedTextColor.GREEN)));
            }
            placer(CASES[i], ic.build());
        }

        Icone tete = Icone.de(Material.PLAYER_HEAD).tete(joueur).nom(gras("✎ " + joueur.getName(), NamedTextColor.GOLD));
        if (autres.isEmpty()) {
            tete.lore(t("Tes stats depuis ton arrivée", NamedTextColor.GRAY));
        } else {
            tete.lore(t("Toi contre " + autres.stream().map(Classement.Entree::nom).reduce((a, b) -> a + ", " + b).orElse(""),
                            NamedTextColor.GRAY))
                    .lore(de(t("Tu mènes dans ", NamedTextColor.GRAY), t(enTete + "", NamedTextColor.GREEN),
                            t(" catégories sur " + Math.min(liste.length, CASES.length), NamedTextColor.GRAY)))
                    .vide().lore(t("✔ = en tête  (égalité : ✔ pour tous)", NamedTextColor.DARK_GRAY));
        }
        placer(4, tete.build());
        retour(40);
    }
}
