package fr.muscu;

import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Progression d'un joueur connecté. Sauvegardée dans les données du joueur (PersistentDataContainer),
 * donc dans le fichier playerdata du monde : rien à gérer à côté.
 */
public final class Profil {

    private static final NamespacedKey K_NIVEAU = new NamespacedKey("muscu", "niveau");
    private static final NamespacedKey K_PTS = new NamespacedKey("muscu", "pts");
    private static final NamespacedKey K_TABLEAU = new NamespacedKey("muscu", "tableau");
    private static final NamespacedKey K_MODE = new NamespacedKey("muscu", "tableau_mode");
    private static final NamespacedKey K_VU = new NamespacedKey("muscu", "vu");
    private static final NamespacedKey K_HUD = new NamespacedKey("muscu", "hud_propose");
    private static final NamespacedKey K_RESTES = new NamespacedKey("muscu", "restes");
    private static final NamespacedKey K_DEFIS_CLE = new NamespacedKey("muscu", "defis_jour");
    private static final NamespacedKey K_DEFIS_PROGRES = new NamespacedKey("muscu", "defis_progres");
    private static final NamespacedKey K_DEFIS_BONUS = new NamespacedKey("muscu", "defis_bonus");
    private static final NamespacedKey K_STATS = new NamespacedKey("muscu", "stats");

    public int niveau;
    public int pts;
    public ModeTableau tableau = ModeTableau.HUD;
    public boolean vu;
    /** Vrai si le joueur n'avait encore aucune donnée Muscu (nouveau ou venant du datapack). */
    public boolean nouveau;

    /** Défis du jour : clé du jour ("2026-11-03#0"), progression brute de chaque défi, bonus "tout réussi" déjà donné. */
    public String defisCle = "";
    public final int[] defisProgres = new int[7];
    public boolean defisBonus;

    /** Compteurs pour la page Stats (le reste vient des statistiques vanilla). */
    public int statShakers, statDefis, statJournees, statDuos;

    /** Restes accumulés vers le prochain point (sauts, cm de sprint, minerais...). */
    private final Map<String, Integer> restes = new HashMap<>();
    /** Dernière valeur vue de chaque statistique (pour calculer ce qui a bougé). */
    private final Map<Statistic, Integer> derniers = new EnumMap<>(Statistic.class);
    /** Points gagnés par événement (viande, minage), crédités à la prochaine boucle. */
    public int enAttente;

    public static Profil charger(Player joueur) {
        PersistentDataContainer c = joueur.getPersistentDataContainer();
        Profil p = new Profil();
        p.nouveau = !c.has(K_NIVEAU, PersistentDataType.INTEGER);
        p.niveau = c.getOrDefault(K_NIVEAU, PersistentDataType.INTEGER, 0);
        p.pts = c.getOrDefault(K_PTS, PersistentDataType.INTEGER, 0);
        // Ancienne version : simple oui/non (oui -> nouveau tableau perso)
        ModeTableau parDefaut = c.getOrDefault(K_TABLEAU, PersistentDataType.BOOLEAN, true) ? ModeTableau.PERSO : ModeTableau.MASQUE;
        p.tableau = ModeTableau.parse(c.get(K_MODE, PersistentDataType.STRING), parDefaut);
        // Version 1.4 : ceux qui avaient le tableau perso passent au HUD (une seule fois)
        if (!c.has(K_HUD, PersistentDataType.BOOLEAN) && p.tableau == ModeTableau.PERSO) {
            p.tableau = ModeTableau.HUD;
        }
        p.vu = c.getOrDefault(K_VU, PersistentDataType.BOOLEAN, false);
        p.defisCle = c.getOrDefault(K_DEFIS_CLE, PersistentDataType.STRING, "");
        int[] prog = c.get(K_DEFIS_PROGRES, PersistentDataType.INTEGER_ARRAY);
        if (prog != null) System.arraycopy(prog, 0, p.defisProgres, 0, Math.min(prog.length, p.defisProgres.length));
        p.defisBonus = c.getOrDefault(K_DEFIS_BONUS, PersistentDataType.BOOLEAN, false);
        int[] st = c.getOrDefault(K_STATS, PersistentDataType.INTEGER_ARRAY, new int[0]);
        if (st.length >= 4) {
            p.statShakers = st[0];
            p.statDefis = st[1];
            p.statJournees = st[2];
            p.statDuos = st[3];
        }
        PersistentDataContainer r = c.get(K_RESTES, PersistentDataType.TAG_CONTAINER);
        if (r != null) {
            for (NamespacedKey k : r.getKeys()) {
                Integer v = r.get(k, PersistentDataType.INTEGER);
                if (v != null) p.restes.put(k.getKey(), v);
            }
        }
        return p;
    }

    public void sauver(Player joueur) {
        PersistentDataContainer c = joueur.getPersistentDataContainer();
        c.set(K_NIVEAU, PersistentDataType.INTEGER, niveau);
        c.set(K_PTS, PersistentDataType.INTEGER, pts);
        c.set(K_TABLEAU, PersistentDataType.BOOLEAN, tableau != ModeTableau.MASQUE);
        c.set(K_MODE, PersistentDataType.STRING, tableau.id);
        c.set(K_VU, PersistentDataType.BOOLEAN, vu);
        c.set(K_HUD, PersistentDataType.BOOLEAN, true);
        c.set(K_DEFIS_CLE, PersistentDataType.STRING, defisCle);
        c.set(K_DEFIS_PROGRES, PersistentDataType.INTEGER_ARRAY, defisProgres.clone());
        c.set(K_DEFIS_BONUS, PersistentDataType.BOOLEAN, defisBonus);
        c.set(K_STATS, PersistentDataType.INTEGER_ARRAY, new int[]{statShakers, statDefis, statJournees, statDuos});
        PersistentDataContainer r = c.getAdapterContext().newPersistentDataContainer();
        restes.forEach((id, v) -> {
            if (v != 0) r.set(new NamespacedKey("muscu", id), PersistentDataType.INTEGER, v);
        });
        c.set(K_RESTES, PersistentDataType.TAG_CONTAINER, r);
    }

    public int reste(String id) {
        return restes.getOrDefault(id, 0);
    }

    /**
     * Ajoute "ajout" au compteur "id" et convertit en points : "points" tous les "seuil".
     * Le reste est gardé pour la suite (comme les modulos du datapack).
     */
    public int convertir(String id, int ajout, int seuil, int points) {
        long total = (long) reste(id) + ajout;
        long gagnes = total / seuil;
        restes.put(id, (int) (total % seuil));
        return (int) Math.min(Integer.MAX_VALUE, gagnes * points);
    }

    public void viderReste(String id) {
        restes.remove(id);
    }

    /** Écart de la statistique depuis la dernière lecture (0 la première fois). */
    public int ecart(Player joueur, Statistic stat) {
        int actuel = joueur.getStatistic(stat);
        Integer avant = derniers.put(stat, actuel);
        return avant == null ? 0 : Math.max(0, actuel - avant);
    }
}
