package fr.muscu;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Activités suivies par événement : minage, bûches, récoltes, pêche, mobs tués, viande cuite, shaker.
 * (Sauts, sprint, nage et dégâts sont lus dans les statistiques par la boucle.)
 */
public final class ActiviteListener implements Listener {

    /** Positions des minerais et bûches posés par des joueurs, rangées dans les données du chunk. */
    private static final NamespacedKey K_POSES = new NamespacedKey("muscu", "minerais_poses");

    private static final Set<Material> CULTURES = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART, Material.COCOA);

    private final MuscuPlugin plugin;

    public ActiviteListener(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void casse(BlockBreakEvent e) {
        Player joueur = e.getPlayer();
        Block bloc = e.getBlock();
        Reglages.GroupeMinage groupe = plugin.reglages().groupeDe(bloc.getType());
        boolean buche = Tag.LOGS.isTagged(bloc.getType());
        boolean culture = CULTURES.contains(bloc.getType())
                && bloc.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
        if (groupe == null && !buche && !culture) return;

        if (groupe != null || buche) {
            boolean pose = retirerPose(bloc);
            if (pose && plugin.reglages().ignorerMineraisPoses) return;
        }
        if (joueur.getGameMode() != GameMode.SURVIVAL && joueur.getGameMode() != GameMode.ADVENTURE) return;
        Profil p = plugin.progression().profil(joueur);
        if (p == null) return;

        if (groupe != null) {
            p.enAttente += p.convertir("mine_" + groupe.id(), 1, groupe.chaque(), groupe.points());
            plugin.defis().progresser(joueur, TypeDefi.MINERAIS, groupe.id(), 1);
        }
        if (buche) plugin.defis().progresser(joueur, TypeDefi.BUCHES, null, 1);
        if (culture) plugin.defis().progresser(joueur, TypeDefi.RECOLTE, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pose(BlockPlaceEvent e) {
        if (!plugin.reglages().ignorerMineraisPoses) return;
        Block bloc = e.getBlockPlaced();
        if (plugin.reglages().groupeDe(bloc.getType()) == null && !Tag.LOGS.isTagged(bloc.getType())) return;
        PersistentDataContainer c = bloc.getChunk().getPersistentDataContainer();
        int[] poses = c.getOrDefault(K_POSES, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int code = code(bloc);
        for (int v : poses) if (v == code) return;
        int[] nouveau = Arrays.copyOf(poses, poses.length + 1);
        nouveau[poses.length] = code;
        c.set(K_POSES, PersistentDataType.INTEGER_ARRAY, nouveau);
    }

    /** Mobs tués : les points passent par la statistique (boucle), ici on ne compte que pour les défis. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mort(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (mob instanceof Player) return;
        Player tueur = mob.getKiller();
        if (tueur == null) return;
        plugin.defis().progresser(tueur, TypeDefi.TUER, mob.getType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void peche(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) plugin.defis().progresser(e.getPlayer(), TypeDefi.PECHE, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void manger(PlayerItemConsumeEvent e) {
        Player joueur = e.getPlayer();
        Profil p = plugin.progression().profil(joueur);
        if (p == null) return;
        Reglages r = plugin.reglages();

        if (Shaker.estShaker(e.getItem())) {
            joueur.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, joueur.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0);
            joueur.playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.2f);
            p.statShakers++;
            if (r.shakerPoints > 0) plugin.progression().gagner(joueur, p, r.shakerPoints);
            plugin.defis().progresser(joueur, TypeDefi.SHAKER, null, 1);
            return;
        }
        if (r.viandes.contains(e.getItem().getType())) {
            p.enAttente += r.viandePoints;
            plugin.defis().progresser(joueur, TypeDefi.VIANDE, null, 1);
        }
    }

    /** Les feux d'artifice de montée de niveau ne font pas de dégâts. */
    @EventHandler(ignoreCancelled = true)
    public void feuInoffensif(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof org.bukkit.entity.Firework f
                && f.getPersistentDataContainer().has(Progression.K_FEU, PersistentDataType.BOOLEAN)) {
            e.setCancelled(true);
        }
    }

    /** Après un /reload ou un changement de datapacks, on s'assure que la recette du shaker existe toujours. */
    @EventHandler
    public void rechargement(ServerResourcesReloadedEvent e) {
        plugin.shaker().assurerRecette();
    }

    private boolean retirerPose(Block bloc) {
        PersistentDataContainer c = bloc.getChunk().getPersistentDataContainer();
        int[] poses = c.get(K_POSES, PersistentDataType.INTEGER_ARRAY);
        if (poses == null) return false;
        int code = code(bloc);
        int[] reste = Arrays.stream(poses).filter(v -> v != code).toArray();
        if (reste.length == poses.length) return false;
        if (reste.length == 0) c.remove(K_POSES);
        else c.set(K_POSES, PersistentDataType.INTEGER_ARRAY, reste);
        return true;
    }

    /** Position du bloc dans son chunk, tassée dans un int. */
    private static int code(Block bloc) {
        return ((bloc.getY() + 4096) << 8) | ((bloc.getX() & 15) << 4) | (bloc.getZ() & 15);
    }
}
