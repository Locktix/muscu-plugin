package fr.muscu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Statistiques "depuis le début" : les statistiques vanilla du jeu (lisibles même pour un joueur hors ligne)
 * + les compteurs du plugin (shakers, défis...).
 */
public final class Stats {

    public enum Stat {
        POINTS("Points de muscu gagnés", Material.EXPERIENCE_BOTTLE),
        TEMPS("Temps de jeu", Material.CLOCK),
        SPRINT("Blocs sprintés", Material.LEATHER_BOOTS),
        NAGE("Blocs nagés", Material.TROPICAL_FISH),
        SAUTS("Sauts", Material.RABBIT_FOOT),
        DEGATS("Cœurs de dégâts infligés", Material.IRON_SWORD),
        MOBS("Mobs tués", Material.ZOMBIE_HEAD),
        MINERAIS("Minerais minés", Material.DIAMOND_ORE),
        VIANDES("Viandes cuites mangées", Material.COOKED_BEEF),
        PECHE("Poissons pêchés", Material.FISHING_ROD),
        SHAKERS("Shakers bus", Material.HONEY_BOTTLE),
        DEFIS("Défis du jour réussis", Material.TARGET),
        JOURNEES("Journées parfaites (3/3)", Material.NETHER_STAR),
        DUOS("Défis duo réussis", Material.LEAD);

        public final String nom;
        public final Material icone;

        Stat(String nom, Material icone) {
            this.nom = nom;
            this.icone = icone;
        }
    }

    private record Cache(long quand, Map<Stat, Long> valeurs) {}

    private final MuscuPlugin plugin;
    /** Les joueurs hors ligne sont relus au plus une fois par minute (lecture de fichier). */
    private final Map<UUID, Cache> horsLigne = new HashMap<>();

    public Stats(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<Stat, Long> valeurs(UUID id) {
        Player enLigne = Bukkit.getPlayer(id);
        if (enLigne != null && plugin.progression().profil(enLigne) != null) {
            horsLigne.remove(id);
            return calculer(enLigne, plugin.progression().profil(enLigne), id);
        }
        Cache c = horsLigne.get(id);
        if (c != null && System.currentTimeMillis() - c.quand() < 60_000) return c.valeurs();
        Map<Stat, Long> v = calculer(Bukkit.getOfflinePlayer(id), null, id);
        horsLigne.put(id, new Cache(System.currentTimeMillis(), v));
        return v;
    }

    private Map<Stat, Long> calculer(OfflinePlayer j, Profil p, UUID id) {
        Reglages r = plugin.reglages();
        Classement cl = plugin.classement();
        Map<Stat, Long> v = new EnumMap<>(Stat.class);

        int niveau = p != null ? p.niveau : cl.niveau(id);
        long total = p != null ? p.pts : cl.pts(id);
        for (int n = 0; n < niveau; n++) total += r.besoin(n);
        v.put(Stat.POINTS, total);

        v.put(Stat.TEMPS, stat(j, Statistic.PLAY_ONE_MINUTE) / 20);
        v.put(Stat.SPRINT, stat(j, Statistic.SPRINT_ONE_CM) / 100);
        v.put(Stat.NAGE, stat(j, Statistic.SWIM_ONE_CM) / 100);
        v.put(Stat.SAUTS, stat(j, Statistic.JUMP));
        v.put(Stat.DEGATS, stat(j, Statistic.DAMAGE_DEALT) / 20);
        v.put(Stat.MOBS, stat(j, Statistic.MOB_KILLS));
        long minerais = 0;
        for (Reglages.GroupeMinage g : r.groupesMinage) for (Material m : g.blocs()) minerais += stat(j, Statistic.MINE_BLOCK, m);
        v.put(Stat.MINERAIS, minerais);
        long viandes = 0;
        for (Material m : r.viandes) viandes += stat(j, Statistic.USE_ITEM, m);
        v.put(Stat.VIANDES, viandes);
        v.put(Stat.PECHE, stat(j, Statistic.FISH_CAUGHT));

        v.put(Stat.SHAKERS, (long) (p != null ? p.statShakers : cl.compteur(id, "shakers")));
        v.put(Stat.DEFIS, (long) (p != null ? p.statDefis : cl.compteur(id, "defis")));
        v.put(Stat.JOURNEES, (long) (p != null ? p.statJournees : cl.compteur(id, "journees")));
        v.put(Stat.DUOS, (long) (p != null ? p.statDuos : cl.compteur(id, "duos")));
        return v;
    }

    /** "12 345 blocs", "3 h 05 min"... */
    public static String format(Stat s, long v) {
        return switch (s) {
            case TEMPS -> v / 3600 + " h " + String.format("%02d", (v % 3600) / 60) + " min";
            case POINTS -> milliers(v) + " pts";
            case SPRINT, NAGE -> milliers(v) + " blocs";
            case DEGATS -> milliers(v) + " cœurs";
            default -> milliers(v);
        };
    }

    private static String milliers(long v) {
        String s = Long.toString(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static long stat(OfflinePlayer j, Statistic s) {
        try {
            return j.getStatistic(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long stat(OfflinePlayer j, Statistic s, Material m) {
        try {
            return j.getStatistic(s, m);
        } catch (Exception e) {
            return 0;
        }
    }
}
