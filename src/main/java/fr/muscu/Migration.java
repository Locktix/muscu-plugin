package fr.muscu;

import io.papermc.paper.datapack.Datapack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Passage du datapack au plugin, automatique :
 *  1. au démarrage, les niveaux/points des scoreboards du datapack sont copiés dans migration.yml ;
 *  2. le datapack "muscu" est désactivé (sinon les points compteraient en double) ;
 *  3. ses scoreboards, son équipe et son tableau sont supprimés ;
 *  4. à sa prochaine connexion, chaque joueur récupère sa progression.
 */
public final class Migration {

    private static final List<String> VIEUX_TAGS = List.of("muscu.vu", "muscu.tableau_init", "muscu.v3", "muscu.duo");

    private final MuscuPlugin plugin;
    private final File fichier;
    private final YamlConfiguration donnees;

    public Migration(MuscuPlugin plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "migration.yml");
        this.donnees = YamlConfiguration.loadConfiguration(fichier);
    }

    public void demarrer() {
        importerScores();
        // Un tick plus tard : le serveur a fini de démarrer, on peut toucher aux datapacks.
        Bukkit.getScheduler().runTask(plugin, () -> {
            desactiverDatapack();
            nettoyerScoreboard();
        });
    }

    /** Progression du datapack pour ce pseudo (et on l'efface), ou null. */
    public int[] recuperer(String nom) {
        if (!donnees.isConfigurationSection(nom)) return null;
        int[] res = {donnees.getInt(nom + ".niveau"), donnees.getInt(nom + ".pts")};
        donnees.set(nom, null);
        sauver();
        return res;
    }

    /** Le joueur avait-il déjà vu le message de bienvenue du datapack ? Nettoie aussi ses vieux tags. */
    public boolean nettoyerJoueur(Player joueur) {
        boolean dejaVu = joueur.getScoreboardTags().contains("muscu.vu");
        VIEUX_TAGS.forEach(joueur::removeScoreboardTag);
        return dejaVu;
    }

    private void importerScores() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective oNiveau = main.getObjective("muscu.niveau");
        if (oNiveau == null) return;
        Objective oPts = main.getObjective("muscu.pts");
        int importes = 0;
        for (String entree : main.getEntries()) {
            if (entree.startsWith("#") || donnees.isConfigurationSection(entree)) continue;
            Score sNiveau = oNiveau.getScore(entree);
            int niveau = sNiveau.isScoreSet() ? sNiveau.getScore() : 0;
            int pts = 0;
            if (oPts != null) {
                Score sPts = oPts.getScore(entree);
                if (sPts.isScoreSet()) pts = sPts.getScore();
            }
            if (niveau <= 0 && pts <= 0) continue;
            donnees.set(entree + ".niveau", Math.min(Paliers.MAX, niveau));
            donnees.set(entree + ".pts", Math.max(0, pts));
            importes++;
        }
        if (importes > 0) {
            sauver();
            plugin.getLogger().info("Datapack Muscu détecté : progression de " + importes
                    + " joueur(s) récupérée, elle leur sera rendue à leur prochaine connexion.");
        }
    }

    private void desactiverDatapack() {
        for (Datapack pack : Bukkit.getDatapackManager().getPacks()) {
            String nom = pack.getName().toLowerCase(Locale.ROOT);
            if (nom.startsWith("file/") && nom.contains("muscu") && pack.isEnabled()) {
                pack.setEnabled(false);
                plugin.getLogger().warning("Ancien datapack « " + pack.getName() + " » désactivé (le plugin le remplace). "
                        + "Tu peux supprimer son dossier dans <monde>/datapacks/.");
            }
        }
    }

    private void nettoyerScoreboard() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        int supprimes = 0;
        for (Objective o : main.getObjectives()) {
            if (o.getName().equals("muscu") || o.getName().startsWith("muscu.")) {
                o.unregister();
                supprimes++;
            }
        }
        Team equipe = main.getTeam("muscu.tableau");
        if (equipe != null) equipe.unregister();
        if (supprimes > 0) plugin.getLogger().info("Anciens scoreboards du datapack supprimés (" + supprimes + ").");
    }

    private void sauver() {
        try {
            plugin.getDataFolder().mkdirs();
            donnees.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'enregistrer migration.yml : " + e.getMessage());
        }
    }
}
