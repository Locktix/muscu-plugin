package fr.muscu;

import fr.muscu.menu.Menu;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Toute la logique de progression : points, niveaux, bonus, duo, succès. */
public final class Progression {

    /** Marque les feux d'artifice de montée de niveau (pour annuler leurs dégâts). */
    public static final NamespacedKey K_FEU = new NamespacedKey("muscu", "feu");

    /** Couleurs du feu d'artifice pour chaque niveau (thème du bonus débloqué). */
    private static final Color[][] COULEURS = {
            {Color.WHITE},
            {Color.AQUA, Color.WHITE},                  // 1 minage
            {Color.RED, Color.fromRGB(0xFF8080)},       // 2 cœur
            {Color.SILVER, Color.WHITE},                // 3 force
            {Color.YELLOW, Color.WHITE},                // 4 vitesse
            {Color.AQUA, Color.TEAL},                   // 5 minage
            {Color.RED, Color.ORANGE},                  // 6 cœurs
            {Color.SILVER, Color.fromRGB(0x55FFFF)},    // 7 force
            {Color.FUCHSIA, Color.PURPLE},              // 8 chute
            {Color.YELLOW, Color.LIME},                 // 9 vitesse
            {Color.ORANGE, Color.YELLOW, Color.FUCHSIA} // 10 max
    };

    private final MuscuPlugin plugin;
    private final Map<UUID, Profil> profils = new HashMap<>();
    /** Barre de boss affichée quelques secondes après un gain. */
    private final Map<UUID, BossBar> barres = new HashMap<>();
    private final Map<UUID, Long> dernierGain = new HashMap<>();

    public Progression(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    public Profil profil(Player joueur) {
        return profils.get(joueur.getUniqueId());
    }

    // ───────────── Connexion / déconnexion ─────────────

    public void connecter(Player joueur) {
        Profil p = Profil.charger(joueur);
        boolean dejaVuDatapack = plugin.migration().nettoyerJoueur(joueur);
        boolean migre = false;
        if (p.nouveau) {
            int[] ancien = plugin.migration().recuperer(joueur.getName());
            if (ancien != null) {
                p.niveau = ancien[0];
                p.pts = ancien[1];
                migre = true;
            }
            if (dejaVuDatapack) p.vu = true;
        }
        // Première lecture des statistiques : on ne compte que ce qui se passe à partir de maintenant.
        for (Reglages.Activite a : plugin.reglages().activites) p.ecart(joueur, a.stat());
        profils.put(joueur.getUniqueId(), p);

        Bonus.appliquer(joueur, p.niveau);
        plugin.shaker().decouvrir(joueur);
        plugin.classement().maj(joueur, p);
        plugin.hud().envoyerPack(joueur);
        appliquerTableau(joueur, p);
        syncSucces(joueur, p.niveau);
        majNom(joueur, p.niveau);

        if (migre) {
            joueur.sendMessage(Txt.msg(Txt.de(Txt.t("Ta progression du datapack a été récupérée : ", NamedTextColor.GRAY),
                    Txt.t("niveau " + p.niveau + " (" + plugin.reglages().progression(p.niveau, p.pts) + ")", NamedTextColor.GOLD),
                    Txt.t(". Ton menu : ", NamedTextColor.GRAY), lienMenu())));
            p.vu = true;
        } else if (!p.vu) {
            joueur.sendMessage(Txt.msg(Txt.de(
                    Txt.t("Bienvenue à la salle ! Sprinte, saute, nage, combats, mine et mange de la viande cuite "
                            + "pour gagner des points de muscu. Ton menu : ", NamedTextColor.GRAY),
                    lienMenu(), Txt.t(" (ou touche G).", NamedTextColor.GRAY))));
            p.vu = true;
        }
        p.sauver(joueur);
        plugin.defis().connexion(joueur);
    }

    public void deconnecter(Player joueur) {
        Profil p = profils.remove(joueur.getUniqueId());
        plugin.tableauPerso().oublier(joueur);
        plugin.hud().deconnexion(joueur);
        plugin.etiquettes().retirer(joueur);
        BossBar barre = barres.remove(joueur.getUniqueId());
        if (barre != null) joueur.hideBossBar(barre);
        dernierGain.remove(joueur.getUniqueId());
        if (p == null) return;
        p.sauver(joueur);
        plugin.classement().maj(joueur, p);
        plugin.classement().sauver();
    }

    // ───────────── Boucle (toutes les secondes) ─────────────

    public void boucle() {
        Reglages r = plugin.reglages();
        plugin.defis().tic();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            Profil p = profil(joueur);
            if (p == null) continue;

            int gain = p.enAttente;
            p.enAttente = 0;
            for (Reglages.Activite a : r.activites) {
                int ecart = p.ecart(joueur, a.stat());
                if (ecart <= 0) continue;
                gain += p.convertir(a.id(), ecart, a.seuil(), a.points());
                TypeDefi defi = TypeDefi.depuisActivite(a.id());
                if (defi != null) plugin.defis().progresser(joueur, defi, null, ecart);
            }
            if (gain > 0) gagner(joueur, p, gain);

            if (p.tableau == ModeTableau.PERSO || p.tableau == ModeTableau.HUD) plugin.tableauPerso().maj(joueur, p);
            if (p.tableau == ModeTableau.HUD) plugin.hud().maj(joueur, p);

            // Barre de boss : disparaît 5 s après le dernier gain
            Long dernier = dernierGain.get(joueur.getUniqueId());
            if (dernier != null && System.currentTimeMillis() - dernier > 5000) {
                dernierGain.remove(joueur.getUniqueId());
                BossBar barre = barres.get(joueur.getUniqueId());
                if (barre != null) joueur.hideBossBar(barre);
            }

            // Menu ouvert : on le met à jour en direct
            if (joueur.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) menu.rafraichir();
        }
    }

    // ───────────── Gains et niveaux ─────────────

    /** Crédite des points (avec le bonus duo) et affiche le gain au-dessus de la hotbar. */
    public void gagner(Player joueur, Profil p, int gain) {
        gagner(joueur, p, gain, true);
    }

    /** @param compteDefis false pour les récompenses de défis (elles ne comptent pas dans "Gagne N points"). */
    public void gagner(Player joueur, Profil p, int gain, boolean compteDefis) {
        Reglages r = plugin.reglages();
        boolean duo = false;
        if (r.duoActif && r.duoPourcent > 0 && partenaire(joueur) != null) {
            duo = true;
            gain += p.convertir("duo", gain * r.duoPourcent, 100, 1);
        }
        p.pts += gain;
        boolean monte = verifierNiveau(joueur, p);
        plugin.classement().maj(joueur, p);
        p.sauver(joueur);

        if (plugin.hud().estAffiche(joueur)) {
            // Le HUD montre déjà la barre : on y ajoute juste "+N"
            plugin.hud().gain(joueur, gain);
            plugin.hud().maj(joueur, p);
        } else if (r.gainsBossBar) {
            afficherBarre(joueur, p, gain, duo, monte);
        } else {
            joueur.sendActionBar(Txt.de(
                    Txt.t("+" + gain + " ⚡", NamedTextColor.GREEN), Component.text("   "),
                    Txt.barre(r.cases(p.niveau, p.pts, 10), 10),
                    Txt.t("   ★ Niv " + p.niveau, NamedTextColor.GOLD),
                    Txt.t("  " + r.progression(p.niveau, p.pts), NamedTextColor.GRAY),
                    Txt.t(duo ? "   ✦ duo" : "", NamedTextColor.AQUA)));
        }
        if (compteDefis) plugin.defis().progresser(joueur, TypeDefi.POINTS, null, gain);
    }

    /** "★4 Cardio   +3 ⚡   56/80   ✦ duo" en haut de l'écran, avec la barre qui se remplit. */
    private void afficherBarre(Player joueur, Profil p, int gain, boolean duo, boolean monte) {
        Reglages r = plugin.reglages();
        boolean max = p.niveau >= Paliers.MAX;
        Component titre = Txt.de(Txt.etoile(p.niveau), Txt.t(" " + Paliers.get(p.niveau).nom(), NamedTextColor.WHITE),
                Txt.t("   +" + gain + " ⚡", NamedTextColor.GREEN),
                Txt.t("   " + r.progression(p.niveau, p.pts), NamedTextColor.GRAY),
                Txt.t(duo ? "   ✦ duo" : "", NamedTextColor.AQUA));
        float avance = max ? 1f : Math.min(1f, (float) p.pts / r.besoin(p.niveau));
        BossBar.Color couleur = monte ? BossBar.Color.GREEN : max ? BossBar.Color.PURPLE : BossBar.Color.YELLOW;
        BossBar barre = barres.get(joueur.getUniqueId());
        if (barre == null) {
            barre = BossBar.bossBar(titre, avance, couleur, BossBar.Overlay.NOTCHED_10);
            barres.put(joueur.getUniqueId(), barre);
        } else {
            barre.name(titre).progress(avance).color(couleur);
        }
        joueur.showBossBar(barre);
        dernierGain.put(joueur.getUniqueId(), System.currentTimeMillis());
    }

    /** @return true si le joueur a monté d'au moins un niveau */
    private boolean verifierNiveau(Player joueur, Profil p) {
        if (!monter(p, plugin.reglages())) return false;

        Bonus.appliquer(joueur, p.niveau);
        majNom(joueur, p.niveau);
        plugin.hud().montee(joueur);
        joueur.showTitle(Title.title(
                Txt.gras("★ NIVEAU " + p.niveau + " ★", NamedTextColor.GOLD),
                Txt.t(Paliers.get(p.niveau).bonus(), NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        joueur.playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        joueur.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, joueur.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        if (plugin.reglages().feuArtifice) feuArtifice(joueur, p.niveau);
        syncSucces(joueur, p.niveau);
        plugin.classement().sauver();

        // Annonce à tout le monde (remplace l'annonce de succès vanilla)
        Paliers.Palier palier = Paliers.get(p.niveau);
        Component annonce = Txt.msg(Txt.de(Txt.t(joueur.getName() + " passe ", NamedTextColor.WHITE), Txt.etoile(p.niveau),
                Txt.t(" " + palier.nom(), NamedTextColor.YELLOW), Txt.t(" !  ", NamedTextColor.WHITE),
                Txt.t("(" + palier.bonus() + ")", NamedTextColor.GREEN)));
        for (Player autre : Bukkit.getOnlinePlayers()) {
            autre.sendMessage(annonce);
            if (autre != joueur) autre.playSound(autre.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
        return true;
    }

    /**
     * Fait monter le profil tant qu'il a assez de points (plusieurs niveaux d'un coup si besoin).
     * Au niveau max, les points en trop sont gardés.
     * @return true si le profil a monté d'au moins un niveau
     */
    static boolean monter(Profil p, Reglages r) {
        boolean monte = false;
        while (p.niveau < Paliers.MAX && p.pts >= r.besoin(p.niveau)) {
            p.pts -= r.besoin(p.niveau);
            p.niveau++;
            monte = true;
        }
        return monte;
    }

    /** Feu d'artifice aux couleurs du palier, qui explose tout de suite et ne blesse personne. */
    private void feuArtifice(Player joueur, int niveau) {
        Color[] couleurs = COULEURS[Math.max(0, Math.min(COULEURS.length - 1, niveau))];
        FireworkEffect.Type forme = niveau >= Paliers.MAX ? FireworkEffect.Type.STAR
                : niveau % 5 == 0 ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST;
        Firework feu = joueur.getWorld().spawn(joueur.getLocation().add(0, 2.2, 0), Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(forme).withColor(couleurs).withFade(Color.WHITE)
                    .trail(true).flicker(niveau >= 5).build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
            f.getPersistentDataContainer().set(K_FEU, PersistentDataType.BOOLEAN, true);
        });
        feu.detonate();
    }

    /** Admin : met le joueur à un niveau précis (points remis à 0). */
    public void definirNiveau(Player joueur, int niveau) {
        Profil p = profil(joueur);
        if (p == null) return;
        p.niveau = Math.max(0, Math.min(Paliers.MAX, niveau));
        p.pts = 0;
        Bonus.appliquer(joueur, p.niveau);
        syncSucces(joueur, p.niveau);
        majNom(joueur, p.niveau);
        plugin.classement().maj(joueur, p);
        plugin.classement().sauver();
        p.sauver(joueur);
    }

    public void basculerTableau(Player joueur) {
        Profil p = profil(joueur);
        if (p == null) return;
        p.tableau = p.tableau.suivant();
        // Pas de HUD sans le pack : on passe au mode suivant
        if (p.tableau == ModeTableau.HUD && !plugin.hud().pret(joueur)) p.tableau = p.tableau.suivant();
        appliquerTableau(joueur, p);
        p.sauver(joueur);
        plugin.hud().message(joueur, Txt.de(Txt.t("✦ Tableau : ", NamedTextColor.GOLD),
                Txt.t(p.tableau.nom, p.tableau == ModeTableau.MASQUE ? NamedTextColor.GRAY : NamedTextColor.YELLOW)));
    }

    /** Montre au joueur le tableau correspondant à son mode. */
    public void appliquerTableau(Player joueur, Profil p) {
        boolean hud = p.tableau == ModeTableau.HUD && plugin.hud().pret(joueur);
        if (!hud) plugin.hud().cacher(joueur);
        if (hud) {
            Scoreboard vide = plugin.etiquettes().pourMasque();
            if (joueur.getScoreboard() != vide) joueur.setScoreboard(vide);
            BossBar barre = barres.get(joueur.getUniqueId());
            if (barre != null) joueur.hideBossBar(barre);
            plugin.hud().montrer(joueur, p);
            return;
        }
        switch (p.tableau) {
            // HUD sans le pack (pas encore chargé ou refusé) : tableau perso sur le côté
            case HUD, PERSO -> plugin.tableauPerso().afficher(joueur, p);
            case CLASSEMENT -> plugin.classement().afficher(joueur);
            case MASQUE -> {
                Scoreboard vide = plugin.etiquettes().pourMasque();
                if (joueur.getScoreboard() != vide) joueur.setScoreboard(vide);
            }
        }
    }

    // ───────────── Outils ─────────────

    /** "★5 Pseudo" dans la liste Tab et dans le chat (selon la config). */
    public void majNom(Player joueur, int niveau) {
        Component nom = Txt.de(Txt.etoile(niveau), Component.text(" "), Component.text(joueur.getName()));
        joueur.playerListName(plugin.reglages().etoileTab ? nom : null);
        joueur.displayName(plugin.reglages().etoileChat ? nom : null);
        plugin.etiquettes().maj(joueur, niveau);
    }

    /** Remet les noms normaux (arrêt du plugin). */
    public void retirerNoms() {
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            joueur.playerListName(null);
            joueur.displayName(null);
            BossBar barre = barres.get(joueur.getUniqueId());
            if (barre != null) joueur.hideBossBar(barre);
        }
    }

    /** L'autre joueur le plus proche dans le rayon du duo, ou null. */
    public Player partenaire(Player joueur) {
        double rayon2 = plugin.reglages().duoRayon * plugin.reglages().duoRayon;
        Player meilleur = null;
        double meilleurD = Double.MAX_VALUE;
        for (Player autre : joueur.getWorld().getPlayers()) {
            if (autre == joueur || autre.getGameMode() == GameMode.SPECTATOR || autre.isDead()) continue;
            double d = autre.getLocation().distanceSquared(joueur.getLocation());
            if (d <= rayon2 && d < meilleurD) {
                meilleur = autre;
                meilleurD = d;
            }
        }
        return meilleur;
    }

    /** Débloque les succès de l'onglet Muscu jusqu'au niveau actuel (et retire ceux au-dessus). */
    public void syncSucces(Player joueur, int niveau) {
        for (int n = 1; n <= Paliers.MAX; n++) {
            Advancement succes = Bukkit.getAdvancement(new NamespacedKey("muscu", "niveau/" + n));
            if (succes == null) continue;
            AdvancementProgress prog = joueur.getAdvancementProgress(succes);
            if (n <= niveau && !prog.isDone()) {
                List.copyOf(prog.getRemainingCriteria()).forEach(prog::awardCriteria);
            } else if (n > niveau && !prog.getAwardedCriteria().isEmpty()) {
                List.copyOf(prog.getAwardedCriteria()).forEach(prog::revokeCriteria);
            }
        }
    }

    private static Component lienMenu() {
        return Txt.t("/muscu", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/muscu"));
    }
}
