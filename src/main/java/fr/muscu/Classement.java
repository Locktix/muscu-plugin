package fr.muscu;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tableau "✦ MUSCU ✦" sur le côté de l'écran. Il vit dans un scoreboard à part (rien n'est écrit dans le monde) :
 * les joueurs qui l'ont activé le voient, les autres gardent le scoreboard principal.
 * Les joueurs hors ligne restent affichés grâce à classement.yml.
 */
public final class Classement {

    private final MuscuPlugin plugin;
    private final Scoreboard tableau;
    private final Objective objectif;
    private final File fichier;
    private final YamlConfiguration cache;

    public Classement(MuscuPlugin plugin) {
        this.plugin = plugin;
        this.tableau = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objectif = tableau.registerNewObjective("muscu", Criteria.DUMMY, Txt.gras("✦ MUSCU ✦", NamedTextColor.GOLD));
        objectif.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.fichier = new File(plugin.getDataFolder(), "classement.yml");
        this.cache = YamlConfiguration.loadConfiguration(fichier);
        for (String uuid : cache.getKeys(false)) {
            ConfigurationSection s = cache.getConfigurationSection(uuid);
            if (s != null && s.getString("nom") != null) {
                ligne(s.getString("nom"), s.getInt("niveau"), s.getInt("pts"));
            }
        }
    }

    public void maj(Player joueur, Profil profil) {
        String cle = joueur.getUniqueId().toString();
        String ancienNom = cache.getString(cle + ".nom");
        if (ancienNom != null && !ancienNom.equals(joueur.getName())) tableau.resetScores(ancienNom);
        cache.set(cle + ".nom", joueur.getName());
        cache.set(cle + ".niveau", profil.niveau);
        cache.set(cle + ".pts", profil.pts);
        cache.set(cle + ".stats.shakers", profil.statShakers);
        cache.set(cle + ".stats.defis", profil.statDefis);
        cache.set(cle + ".stats.journees", profil.statJournees);
        cache.set(cle + ".stats.duos", profil.statDuos);
        ligne(joueur.getName(), profil.niveau, profil.pts);
    }

    public void afficher(Player joueur) {
        if (joueur.getScoreboard() != tableau) joueur.setScoreboard(tableau);
    }

    public record Entree(UUID id, String nom, int niveau, int pts, boolean enLigne) {}

    public Scoreboard scoreboard() {
        return tableau;
    }

    public int niveau(UUID id) {
        return cache.getInt(id + ".niveau");
    }

    public int pts(UUID id) {
        return cache.getInt(id + ".pts");
    }

    /** Compteur du plugin (shakers, defis, journees, duos) d'un joueur, même hors ligne. */
    public int compteur(UUID id, String nom) {
        return cache.getInt(id + ".stats." + nom);
    }

    /** Les autres joueurs connus (connectés ou non), du plus haut niveau au plus bas. */
    public List<Entree> autres(UUID moi) {
        List<Entree> liste = new ArrayList<>();
        for (String cle : cache.getKeys(false)) {
            if (cle.equals(moi.toString())) continue;
            ConfigurationSection s = cache.getConfigurationSection(cle);
            if (s == null || s.getString("nom") == null) continue;
            UUID id;
            try {
                id = UUID.fromString(cle);
            } catch (IllegalArgumentException e) {
                continue;
            }
            liste.add(new Entree(id, s.getString("nom"), s.getInt("niveau"), s.getInt("pts"), Bukkit.getPlayer(id) != null));
        }
        liste.sort(Comparator.comparingInt(Entree::niveau).thenComparingInt(Entree::pts).reversed());
        return liste;
    }

    public boolean estAffiche(Player joueur) {
        return joueur.getScoreboard() == tableau;
    }

    /** Remet le scoreboard principal à tout le monde (arrêt du plugin). */
    public void retirerATous() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (estAffiche(p)) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void sauver() {
        try {
            plugin.getDataFolder().mkdirs();
            cache.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'enregistrer classement.yml : " + e.getMessage());
        }
    }

    private void ligne(String nom, int niveau, int pts) {
        Score score = objectif.getScore(nom);
        score.setScore(niveau);
        int cases = plugin.reglages().cases(niveau, pts, 10);
        score.numberFormat(NumberFormat.fixed(Txt.de(Txt.t("★" + niveau + " ", NamedTextColor.GOLD), Txt.barre(cases, 10))));
    }
}
