package fr.muscu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * "★4" devant le pseudo au-dessus de la tête, via une équipe (préfixe) par joueur.
 * Le nom affiché au-dessus d'un joueur dépend du scoreboard de celui qui regarde : l'équipe est donc
 * recopiée dans tous les scoreboards du plugin (classement, tableaux perso, et un scoreboard "neutre"
 * pour le mode masqué). Le scoreboard principal du monde n'est jamais modifié.
 */
public final class Etiquettes {

    private final MuscuPlugin plugin;
    private final Scoreboard neutre;

    public Etiquettes(MuscuPlugin plugin) {
        this.plugin = plugin;
        this.neutre = Bukkit.getScoreboardManager().getNewScoreboard();
    }

    /** Scoreboard du mode "masqué" : vide, mais avec les ★ au-dessus des têtes (sinon le scoreboard principal). */
    public Scoreboard pourMasque() {
        return plugin.reglages().etoileTete ? neutre : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public boolean estNeutre(Scoreboard sb) {
        return sb == neutre;
    }

    /** Met à jour l'étoile d'un joueur partout. */
    public void maj(Player joueur, int niveau) {
        for (Scoreboard sb : tous()) appliquer(sb, joueur, niveau);
    }

    /** Nouveau scoreboard : on y ajoute les étoiles de tous les joueurs connectés. */
    public void preparer(Scoreboard sb) {
        for (Player j : Bukkit.getOnlinePlayers()) {
            Profil p = plugin.progression().profil(j);
            if (p != null) appliquer(sb, j, p.niveau);
        }
    }

    public void retirer(Player joueur) {
        for (Scoreboard sb : tous()) {
            Team t = sb.getTeam(nomEquipe(joueur));
            if (t != null) t.unregister();
        }
    }

    private void appliquer(Scoreboard sb, Player joueur, int niveau) {
        Team t = sb.getTeam(nomEquipe(joueur));
        if (!plugin.reglages().etoileTete) {
            if (t != null) t.unregister();
            return;
        }
        if (t == null) t = sb.registerNewTeam(nomEquipe(joueur));
        t.prefix(Txt.etoile(niveau).append(Component.text(" ")));
        if (!t.hasEntry(joueur.getName())) t.addEntry(joueur.getName());
    }

    private List<Scoreboard> tous() {
        List<Scoreboard> liste = new ArrayList<>();
        liste.add(neutre);
        liste.add(plugin.classement().scoreboard());
        liste.addAll(plugin.tableauPerso().tous());
        return liste;
    }

    private static String nomEquipe(Player joueur) {
        return "muscu." + joueur.getName();
    }
}
