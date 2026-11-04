package fr.muscu;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Valeurs lues dans config.yml. */
public final class Reglages {

    /**
     * Activité suivie via une statistique vanilla.
     * @param unites unités de la statistique pour UNE chose comptée (100 cm = 1 bloc, 20 = 1 cœur de dégâts)
     */
    public record Activite(String id, String nom, Material icone, Statistic stat, int unites,
                           String singulier, String pluriel, int chaque, int points) {
        /** Unités brutes de la statistique nécessaires pour gagner des points. */
        public int seuil() { return chaque * unites; }
    }

    public record GroupeMinage(String id, String nom, Material icone, int chaque, int points, Set<Material> blocs) {}

    /** Un défi possible. cible = type de mob ("zombie") ou groupe de minerais ("rare"), sinon null. */
    public record Defi(TypeDefi type, String cible, int quantite, int points) {
        /** Unités brutes à atteindre. */
        public int seuil() { return quantite * type.unites; }
    }

    public final int pointsParNiveau;
    public final List<Activite> activites;
    public final int viandePoints;
    public final Set<Material> viandes;
    public final List<GroupeMinage> groupesMinage;
    public final boolean ignorerMineraisPoses;
    public final boolean duoActif;
    public final double duoRayon;
    public final int duoPourcent;
    public final int shakerPoints;
    public final int shakerCooldown;
    public final boolean defisActifs;
    public final int defisParJour;
    public final int defisBonusComplet;
    public final List<Defi> defisPerso;
    public final List<Defi> defisDuo;
    public final boolean etoileTab;
    public final boolean etoileChat;
    public final boolean etoileTete;
    public final boolean gainsBossBar;
    public final boolean feuArtifice;
    public final boolean hudActif;
    public final int hudPort;
    public final String hudAdresse;

    private Reglages(FileConfiguration c, Logger log) {
        pointsParNiveau = Math.max(1, c.getInt("niveaux.points-par-niveau", 20));

        activites = List.of(
                activite(c, "sauts", "Sauter", Material.RABBIT_FOOT, Statistic.JUMP, 1, "saut", "sauts", 10, 1),
                activite(c, "sprint", "Sprinter", Material.LEATHER_BOOTS, Statistic.SPRINT_ONE_CM, 100,
                        "bloc de sprint", "blocs de sprint", 50, 1),
                activite(c, "nage", "Nager", Material.TROPICAL_FISH, Statistic.SWIM_ONE_CM, 100,
                        "bloc de nage", "blocs de nage", 25, 1),
                activite(c, "degats", "Combattre", Material.IRON_SWORD, Statistic.DAMAGE_DEALT, 20,
                        "cœur de dégâts infligé", "cœurs de dégâts infligés", 10, 1),
                activite(c, "mob-tue", "Tuer des mobs", Material.ZOMBIE_HEAD, Statistic.MOB_KILLS, 1,
                        "mob tué", "mobs tués", 1, 2)
        );

        viandePoints = Math.max(0, c.getInt("activites.viande-cuite.points", 1));
        viandes = materiaux(c.getStringList("activites.viande-cuite.items"), log, "activites.viande-cuite.items");
        if (viandes.isEmpty()) {
            viandes.addAll(List.of(Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_PORKCHOP, Material.COOKED_MUTTON));
        }

        ignorerMineraisPoses = c.getBoolean("minage.ignorer-minerais-poses", true);
        List<GroupeMinage> groupes = new ArrayList<>();
        ConfigurationSection sg = c.getConfigurationSection("minage.groupes");
        if (sg != null) {
            for (String id : sg.getKeys(false)) {
                ConfigurationSection g = sg.getConfigurationSection(id);
                if (g == null) continue;
                Material icone = Material.matchMaterial(g.getString("icone", "STONE"));
                groupes.add(new GroupeMinage(id, g.getString("nom", id), icone == null ? Material.STONE : icone,
                        Math.max(1, g.getInt("chaque", 1)), Math.max(0, g.getInt("points", 1)),
                        materiaux(g.getStringList("blocs"), log, "minage.groupes." + id + ".blocs")));
            }
        }
        groupesMinage = List.copyOf(groupes);

        duoActif = c.getBoolean("duo.actif", true);
        duoRayon = Math.max(1, c.getDouble("duo.rayon", 20));
        duoPourcent = Math.max(0, c.getInt("duo.bonus-pourcent", 10));

        shakerPoints = Math.max(0, c.getInt("shaker.points", 5));
        shakerCooldown = Math.max(0, c.getInt("shaker.cooldown-secondes", 300));

        defisActifs = c.getBoolean("defis.actif", true);
        defisParJour = Math.max(1, Math.min(7, c.getInt("defis.par-jour", 3)));
        defisBonusComplet = Math.max(0, c.getInt("defis.bonus-tous-reussis", 10));
        defisPerso = defis(c, "defis.perso", log);
        defisDuo = defis(c, "defis.duo", log);

        etoileTab = c.getBoolean("affichage.etoile-tab", true);
        etoileChat = c.getBoolean("affichage.etoile-chat", true);
        etoileTete = c.getBoolean("affichage.etoile-tete", true);
        gainsBossBar = !"actionbar".equalsIgnoreCase(c.getString("affichage.gains", "bossbar"));
        feuArtifice = c.getBoolean("affichage.feu-artifice", true);

        hudActif = c.getBoolean("hud.actif", true);
        hudPort = Math.max(1, Math.min(65535, c.getInt("hud.port", 25566)));
        hudAdresse = c.getString("hud.adresse", "").trim();
    }

    private List<Defi> defis(FileConfiguration c, String chemin, Logger log) {
        List<Defi> liste = new ArrayList<>();
        int n = 0;
        for (Map<?, ?> m : c.getMapList(chemin)) {
            n++;
            TypeDefi type = TypeDefi.parse(String.valueOf(m.get("type")));
            if (type == null) {
                log.warning("config.yml (" + chemin + " n°" + n + ") : type de défi inconnu « " + m.get("type") + " », ignoré.");
                continue;
            }
            String cible = m.get("cible") == null ? null : String.valueOf(m.get("cible")).toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (cible != null && type == TypeDefi.TUER && Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(cible)) == null) {
                log.warning("config.yml (" + chemin + " n°" + n + ") : mob inconnu « " + cible + " », défi ignoré.");
                continue;
            }
            if (cible != null && type == TypeDefi.MINERAIS && groupesMinage.stream().noneMatch(g -> g.id().equalsIgnoreCase(m.get("cible").toString()))) {
                log.warning("config.yml (" + chemin + " n°" + n + ") : groupe de minerais inconnu « " + cible + " », défi ignoré.");
                continue;
            }
            if (cible != null && type != TypeDefi.TUER && type != TypeDefi.MINERAIS) cible = null;
            liste.add(new Defi(type, cible, Math.max(1, nombre(m.get("quantite"), 1)), Math.max(0, nombre(m.get("points"), 5))));
        }
        return List.copyOf(liste);
    }

    private static int nombre(Object o, int defaut) {
        if (o instanceof Number num) return num.intValue();
        try {
            return o == null ? defaut : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    public static Reglages charger(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        return new Reglages(plugin.getConfig(), plugin.getLogger());
    }

    /** Points nécessaires pour passer du niveau n au niveau n+1. */
    public int besoin(int niveau) {
        return (niveau + 1) * pointsParNiveau;
    }

    /** Nombre de cases pleines d'une barre de "total" cases. */
    public int cases(int niveau, int pts, int total) {
        if (niveau >= Paliers.MAX) return total;
        return Math.min(total, (int) ((long) pts * total / besoin(niveau)));
    }

    /** "48/80" ou "MAX". */
    public String progression(int niveau, int pts) {
        return niveau >= Paliers.MAX ? "MAX" : pts + "/" + besoin(niveau);
    }

    public GroupeMinage groupeDe(Material bloc) {
        for (GroupeMinage g : groupesMinage) {
            if (g.blocs().contains(bloc)) return g;
        }
        return null;
    }

    private static Activite activite(FileConfiguration c, String id, String nom, Material icone, Statistic stat, int unites,
                                     String singulier, String pluriel, int chaqueDefaut, int pointsDefaut) {
        int chaque = Math.max(1, c.getInt("activites." + id + ".chaque", chaqueDefaut));
        int points = Math.max(0, c.getInt("activites." + id + ".points", pointsDefaut));
        return new Activite(id, nom, icone, stat, unites, singulier, pluriel, chaque, points);
    }

    private static Set<Material> materiaux(List<String> noms, Logger log, String chemin) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String nom : new LinkedHashSet<>(noms)) {
            Material m = Material.matchMaterial(nom);
            if (m == null) log.warning("config.yml (" + chemin + ") : matériau inconnu « " + nom + " », ignoré.");
            else set.add(m);
        }
        return set;
    }
}
