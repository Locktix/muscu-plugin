package fr.muscu;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.muscu.Txt.barre;
import static fr.muscu.Txt.de;
import static fr.muscu.Txt.etoile;
import static fr.muscu.Txt.t;

/**
 * Tableau "perso" : un scoreboard par joueur, redessiné chaque seconde.
 * <pre>
 *    ✦ MUSCU ✦
 *  ★3 Coup de poing
 *  ■■■■■■■■■■       56/80
 *
 *  ☀ Défis du jour     2/3
 *   ✔ Zombie             ✔
 *   • Pêche            3/5
 *  ♦ Duo · Sprint  1240/2000
 *
 *  ✚ Shaker           prêt
 *  ⚡ Bonus duo         Ami
 *
 *  Ami          ★4 ■■■■■
 * </pre>
 */
public final class TableauPerso {

    private static final int MAX_LIGNES = 15;
    private static final Ligne VIDE = new Ligne(Component.empty(), null);

    /** Une ligne : texte à gauche, valeur à droite (ou rien). */
    private record Ligne(Component gauche, Component droite) {}

    private final MuscuPlugin plugin;
    private final Map<UUID, Scoreboard> tableaux = new HashMap<>();
    private final Map<UUID, List<Ligne>> affiche = new HashMap<>();

    public TableauPerso(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    public void afficher(Player joueur, Profil profil) {
        Scoreboard sb = tableaux.computeIfAbsent(joueur.getUniqueId(), id -> creer());
        if (joueur.getScoreboard() != sb) {
            affiche.remove(joueur.getUniqueId());
            joueur.setScoreboard(sb);
        }
        maj(joueur, profil);
    }

    public boolean estAffiche(Player joueur) {
        Scoreboard sb = tableaux.get(joueur.getUniqueId());
        return sb != null && joueur.getScoreboard() == sb;
    }

    /** Déconnexion : on oublie son tableau. */
    public void oublier(Player joueur) {
        tableaux.remove(joueur.getUniqueId());
        affiche.remove(joueur.getUniqueId());
    }

    /** Redessine le tableau (seules les lignes qui ont changé sont renvoyées au joueur). */
    public void maj(Player joueur, Profil profil) {
        if (!estAffiche(joueur)) return;
        Scoreboard sb = tableaux.get(joueur.getUniqueId());
        Objective obj = sb.getObjective("muscu");
        if (obj == null) return;

        List<Ligne> lignes = construire(joueur, profil);
        if (lignes.size() > MAX_LIGNES) lignes = lignes.subList(0, MAX_LIGNES);
        List<Ligne> avant = affiche.getOrDefault(joueur.getUniqueId(), List.of());
        if (lignes.equals(avant)) return;

        for (int i = 0; i < lignes.size(); i++) {
            Ligne l = lignes.get(i);
            if (i < avant.size() && l.equals(avant.get(i))) continue;
            Score s = obj.getScore("l" + i);
            s.setScore(MAX_LIGNES - i);
            s.customName(l.gauche());
            s.numberFormat(l.droite() == null ? NumberFormat.blank() : NumberFormat.fixed(l.droite()));
        }
        for (int i = lignes.size(); i < avant.size(); i++) sb.resetScores("l" + i);
        affiche.put(joueur.getUniqueId(), List.copyOf(lignes));
    }

    public java.util.Collection<Scoreboard> tous() {
        return tableaux.values();
    }

    private Scoreboard creer() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        plugin.etiquettes().preparer(sb);
        Objective obj = sb.registerNewObjective("muscu", Criteria.DUMMY, Txt.gras("✦ MUSCU ✦", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        return sb;
    }

    private List<Ligne> construire(Player joueur, Profil p) {
        Reglages r = plugin.reglages();
        Defis defis = plugin.defis();
        List<Ligne> l = new ArrayList<>();

        // ── Progression ──
        l.add(new Ligne(de(etoile(p.niveau), Component.text(" "), t(Paliers.get(p.niveau).nom(), NamedTextColor.WHITE)), null));
        l.add(new Ligne(barre(r.cases(p.niveau, p.pts, 10), 10), t(r.progression(p.niveau, p.pts), NamedTextColor.GRAY)));

        // ── Défis ──
        if (defis.actif()) {
            l.add(VIDE);
            int n = defis.perso().size();
            int faits = defis.faits(p);
            l.add(new Ligne(t("☀ Défis du jour", NamedTextColor.YELLOW),
                    faits == n ? t("✔ " + faits + "/" + n, NamedTextColor.GREEN) : t(faits + "/" + n, NamedTextColor.YELLOW)));
            if (faits < n && n <= 4) {
                for (int i = 0; i < n; i++) {
                    Reglages.Defi d = defis.perso().get(i);
                    int brut = defis.progres(p, i);
                    if (brut >= d.seuil()) {
                        l.add(new Ligne(de(t(" ✔ ", NamedTextColor.GREEN), defis.court(d).color(NamedTextColor.DARK_GRAY)),
                                t("✔", NamedTextColor.GREEN)));
                    } else {
                        l.add(new Ligne(de(t(" • ", NamedTextColor.GRAY), defis.court(d).color(NamedTextColor.WHITE)),
                                t(Math.min(d.quantite(), brut / d.type().unites) + "/" + d.quantite(), NamedTextColor.YELLOW)));
                    }
                }
            }
            Reglages.Defi duo = defis.duo();
            if (duo != null) {
                l.add(new Ligne(de(t("♦ Duo · ", NamedTextColor.AQUA), defis.court(duo).color(NamedTextColor.WHITE)),
                        defis.duoFini() ? t("✔", NamedTextColor.GREEN)
                                : t(defis.duoProgres() / duo.type().unites + "/" + duo.quantite(), NamedTextColor.AQUA)));
            }
        }

        // ── Shaker et duo ──
        l.add(VIDE);
        int attente = plugin.shaker().cooldownRestant(joueur);
        l.add(new Ligne(t("✚ Shaker", NamedTextColor.GOLD), attente > 0
                ? t(attente / 60 + ":" + String.format("%02d", attente % 60), NamedTextColor.RED)
                : t("prêt", NamedTextColor.GREEN)));
        if (r.duoActif) {
            Player partenaire = plugin.progression().partenaire(joueur);
            l.add(new Ligne(t("⚡ Bonus duo", NamedTextColor.AQUA),
                    partenaire != null ? t(partenaire.getName(), NamedTextColor.AQUA) : t("—", NamedTextColor.DARK_GRAY)));
        }

        // ── Les autres joueurs ──
        List<Classement.Entree> autres = plugin.classement().autres(joueur.getUniqueId());
        if (!autres.isEmpty() && l.size() + 2 <= MAX_LIGNES) {
            l.add(VIDE);
            for (Classement.Entree e : autres) {
                if (l.size() >= MAX_LIGNES) break;
                l.add(new Ligne(t(e.nom(), e.enLigne() ? NamedTextColor.WHITE : NamedTextColor.GRAY),
                        de(etoile(e.niveau()), Component.text(" "), barre(r.cases(e.niveau(), e.pts(), 5), 5))));
            }
        }
        return l;
    }
}
