package fr.muscu;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class JoueurListener implements Listener {

    private final MuscuPlugin plugin;

    public JoueurListener(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void connexion(PlayerJoinEvent e) {
        plugin.progression().connecter(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void deconnexion(PlayerQuitEvent e) {
        plugin.progression().deconnecter(e.getPlayer());
    }

    /** Réponse du client au resource pack du HUD. */
    @EventHandler
    public void pack(PlayerResourcePackStatusEvent e) {
        plugin.hud().statut(e.getPlayer(), e.getStatus(), e.getID());
    }

    /** Après une mort, on remet les bonus (la progression n'est jamais perdue). */
    @EventHandler
    public void reapparition(PlayerPostRespawnEvent e) {
        Profil p = plugin.progression().profil(e.getPlayer());
        if (p != null) Bonus.appliquer(e.getPlayer(), p.niveau);
    }
}
