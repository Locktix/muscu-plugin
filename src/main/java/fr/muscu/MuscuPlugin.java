package fr.muscu;

import fr.muscu.hud.Hud;
import fr.muscu.menu.MenuListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MuscuPlugin extends JavaPlugin {

    private Reglages reglages;
    private Classement classement;
    private Progression progression;
    private Shaker shaker;
    private Migration migration;
    private Defis defis;
    private TableauPerso tableauPerso;
    private Etiquettes etiquettes;
    private Stats stats;
    private Hud hud;

    @Override
    public void onEnable() {
        reglages = Reglages.charger(this);
        classement = new Classement(this);
        progression = new Progression(this);
        shaker = new Shaker(this);
        migration = new Migration(this);
        defis = new Defis(this);
        tableauPerso = new TableauPerso(this);
        etiquettes = new Etiquettes(this);
        stats = new Stats(this);
        hud = new Hud(this);
        hud.demarrer();

        shaker.enregistrerRecette(false);
        migration.demarrer();

        getServer().getPluginManager().registerEvents(new JoueurListener(this), this);
        getServer().getPluginManager().registerEvents(new ActiviteListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("muscu", "Ouvre ton menu Muscu", List.of(), new MuscuCommande(this)));

        // Joueurs déjà connectés (cas d'un rechargement du plugin)
        for (Player joueur : Bukkit.getOnlinePlayers()) progression.connecter(joueur);

        Bukkit.getScheduler().runTaskTimer(this, progression::boucle, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (progression == null) return;
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            // On rend le scoreboard normal aux joueurs qui voyaient un tableau Muscu
            if (tableauPerso.estAffiche(joueur) || classement.estAffiche(joueur) || etiquettes.estNeutre(joueur.getScoreboard())) {
                joueur.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            progression.deconnecter(joueur);
            if (joueur.getOpenInventory().getTopInventory().getHolder(false) instanceof fr.muscu.menu.Menu) {
                joueur.closeInventory();
            }
        }
        hud.arreter();
        classement.retirerATous();
        classement.sauver();
        progression.retirerNoms();
        defis.sauverTout();
    }

    /** /muscu admin reload */
    public void recharger() {
        reglages = Reglages.charger(this);
        shaker.enregistrerRecette(true);
        defis.tirer();
        hud.demarrer();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            Profil p = progression.profil(joueur);
            if (p != null) {
                progression.majNom(joueur, p.niveau);
                progression.appliquerTableau(joueur, p);
            }
        }
    }

    public Reglages reglages() { return reglages; }
    public Classement classement() { return classement; }
    public Progression progression() { return progression; }
    public Shaker shaker() { return shaker; }
    public Migration migration() { return migration; }
    public Defis defis() { return defis; }
    public TableauPerso tableauPerso() { return tableauPerso; }
    public Etiquettes etiquettes() { return etiquettes; }
    public Stats stats() { return stats; }
    public Hud hud() { return hud; }
}
