package fr.muscu.hud;

import fr.muscu.Defis;
import fr.muscu.MuscuPlugin;
import fr.muscu.Paliers;
import fr.muscu.Profil;
import fr.muscu.Reglages;
import fr.muscu.TypeDefi;
import fr.muscu.Txt;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import static fr.muscu.hud.Plan.*;

/**
 * HUD en images autour de la hotbar : le texte de la barre d'action, fait d'images (resource pack envoyé
 * par le serveur) et renvoyé chaque seconde. Sans le pack (refusé, pas téléchargeable), on retombe sur le tableau perso.
 */
public final class Hud {

    /** Identifiant fixe du pack : un nouvel envoi remplace l'ancien chez le client. */
    private static final UUID ID_PACK = UUID.nameUUIDFromBytes("muscu-hud".getBytes());

    private static final TextColor VERT = TextColor.color(0x5FD35F);
    private static final TextColor OR = TextColor.color(0xF2C14E);
    private static final TextColor AQUA = TextColor.color(0x4FD8E8);
    private static final TextColor ORANGE = TextColor.color(0xFFB33A);
    private static final TextColor ICONE_FINIE = TextColor.color(0x6E6E6E);
    private static final TextColor GRIS = TextColor.color(0xB8B0C8);
    private static final TextColor GRIS_FONCE = TextColor.color(0x6A6478);

    private enum Etat { ENVOYE, CHARGE, ECHEC }

    private final MuscuPlugin plugin;
    private PackHud pack;
    private ServeurPack serveur;
    private int portServeur = -1;

    private final Map<UUID, Etat> etats = new HashMap<>();
    private final Set<UUID> affiches = new HashSet<>();
    /** Message à afficher par-dessus le HUD : [texte, fin en ms]. */
    private final Map<UUID, Object[]> messages = new HashMap<>();
    /** Gain affiché ("+3") et jusqu'à quand. */
    private final Map<UUID, long[]> gains = new HashMap<>();
    private final Map<UUID, Long> montees = new HashMap<>();

    public Hud(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    // ───────────── Démarrage / arrêt ─────────────

    /** (Re)fabrique le pack et (re)lance le serveur web. Renvoie le pack aux joueurs si son contenu a changé. */
    public void demarrer() {
        Reglages r = plugin.reglages();
        String ancien = pack == null ? null : pack.sha1();
        if (!r.hudActif) {
            arreterServeur();
            pack = null;
            for (Player j : Bukkit.getOnlinePlayers()) cacher(j);
            etats.clear();
            return;
        }
        try {
            Set<String> textures = new LinkedHashSet<>();
            for (Reglages.Defi d : r.defisPerso) textures.add(texture(d));
            for (Reglages.Defi d : r.defisDuo) textures.add(texture(d));
            for (TypeDefi t : TypeDefi.values()) textures.add(textureParDefaut(t));
            PackHud nouveau = Images.fabriquer(textures);
            pack = nouveau;
            plugin.getDataFolder().mkdirs();
            Files.write(plugin.getDataFolder().toPath().resolve("muscu-hud.zip"), nouveau.zip());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de fabriquer le pack du HUD", e);
            return;
        }
        if (serveur == null || portServeur != r.hudPort) {
            arreterServeur();
            try {
                serveur = new ServeurPack(r.hudPort, () -> pack);
                portServeur = r.hudPort;
                plugin.getLogger().info("HUD : pack servi sur le port " + r.hudPort + " (empreinte " + pack.sha1().substring(0, 8) + ")");
            } catch (IOException e) {
                plugin.getLogger().warning("HUD : impossible d'ouvrir le port " + r.hudPort + " pour servir le pack ("
                        + e.getMessage() + "). Change hud.port dans config.yml. En attendant : tableau classique.");
                pack = null;
                return;
            }
        }
        if (ancien != null && !ancien.equals(pack.sha1())) {
            for (Player j : Bukkit.getOnlinePlayers()) envoyerPack(j);
        }
    }

    public void arreter() {
        for (Player j : Bukkit.getOnlinePlayers()) cacher(j);
        arreterServeur();
    }

    private void arreterServeur() {
        if (serveur != null) serveur.arreter();
        serveur = null;
        portServeur = -1;
    }

    // ───────────── Pack ─────────────

    public void envoyerPack(Player joueur) {
        if (pack == null) return;
        String url = url(joueur);
        etats.put(joueur.getUniqueId(), Etat.ENVOYE);
        joueur.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(ID_PACK, URI.create(url), pack.sha1()))
                .replace(false)
                .required(false)
                .prompt(Txt.de(Txt.gras("Muscu", NamedTextColor.GOLD),
                        Txt.t(" : interface en images (niveau et défis) autour de la hotbar.", NamedTextColor.WHITE))));
    }

    private String url(Player joueur) {
        String hote = plugin.reglages().hudAdresse;
        if (hote.isBlank()) {
            InetSocketAddress v = joueur.getVirtualHost();
            hote = v == null ? "" : v.getHostString();
            int zero = hote.indexOf('\0');
            if (zero >= 0) hote = hote.substring(0, zero);
            if (hote.endsWith(".")) hote = hote.substring(0, hote.length() - 1);
            if (hote.isBlank()) hote = "localhost";
            if (hote.contains(":") && !hote.startsWith("[")) hote = "[" + hote + "]";
        }
        if (!hote.contains("://")) hote = "http://" + hote + ":" + plugin.reglages().hudPort;
        if (hote.endsWith("/")) hote = hote.substring(0, hote.length() - 1);
        return hote + "/muscu-hud-" + pack.sha1().substring(0, 12) + ".zip";
    }

    /** Réponse du client au pack. */
    public void statut(Player joueur, PlayerResourcePackStatusEvent.Status statut, UUID id) {
        if (!ID_PACK.equals(id)) return;
        Profil p = plugin.progression().profil(joueur);
        switch (statut) {
            case SUCCESSFULLY_LOADED -> {
                etats.put(joueur.getUniqueId(), Etat.CHARGE);
                if (p != null) plugin.progression().appliquerTableau(joueur, p);
            }
            case DECLINED -> {
                etats.put(joueur.getUniqueId(), Etat.ECHEC);
                joueur.sendMessage(Txt.msg("Pack du HUD refusé : tu gardes le tableau sur le côté. Pour l'activer : "
                        + "Multijoueur → ce serveur → Modifier → Packs de ressources : Activés, puis reconnecte-toi."));
                if (p != null) plugin.progression().appliquerTableau(joueur, p);
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                etats.put(joueur.getUniqueId(), Etat.ECHEC);
                plugin.getLogger().warning("HUD : " + joueur.getName() + " n'a pas pu charger le pack (" + statut
                        + ", adresse " + url(joueur) + "). Si ce joueur n'est pas sur ta machine, le port " + plugin.reglages().hudPort
                        + " doit être ouvert, ou mets l'adresse publique dans hud.adresse (config.yml).");
                joueur.sendMessage(Txt.msg("Le pack du HUD n'a pas pu être chargé : tableau sur le côté à la place."));
                if (p != null) plugin.progression().appliquerTableau(joueur, p);
            }
            default -> {
            }
        }
    }

    /** Le joueur a-t-il le pack (le HUD peut s'afficher) ? */
    public boolean pret(Player joueur) {
        return pack != null && etats.get(joueur.getUniqueId()) == Etat.CHARGE;
    }

    public boolean actif() {
        return pack != null;
    }

    // ───────────── Affichage ─────────────

    public void montrer(Player joueur, Profil p) {
        affiches.add(joueur.getUniqueId());
        maj(joueur, p);
    }

    public boolean estAffiche(Player joueur) {
        return affiches.contains(joueur.getUniqueId());
    }

    public void cacher(Player joueur) {
        if (affiches.remove(joueur.getUniqueId())) joueur.sendActionBar(Component.empty());
    }

    public void deconnexion(Player joueur) {
        UUID id = joueur.getUniqueId();
        affiches.remove(id);
        etats.remove(id);
        gains.remove(id);
        montees.remove(id);
        messages.remove(id);
    }

    /** Points gagnés : "+N" à côté des points pendant 5 s (les gains rapprochés s'additionnent). */
    public void gain(Player joueur, int n) {
        long maintenant = System.currentTimeMillis();
        long[] g = gains.get(joueur.getUniqueId());
        long total = g != null && g[1] > maintenant ? g[0] + n : n;
        gains.put(joueur.getUniqueId(), new long[]{total, maintenant + 5000});
    }

    /** Montée de niveau : l'anneau clignote quelques secondes. */
    public void montee(Player joueur) {
        montees.put(joueur.getUniqueId(), System.currentTimeMillis() + 4000);
    }

    /**
     * Message court à l'endroit habituel de la barre d'action (au-dessus de la hotbar), pendant 3 s.
     * Sans HUD affiché, c'est une barre d'action normale. (Le HUD utilise lui-même la barre d'action :
     * un message envoyé directement serait effacé dans la seconde.)
     */
    public void message(Player joueur, Component texte) {
        if (!estAffiche(joueur)) {
            joueur.sendActionBar(texte);
            return;
        }
        messages.put(joueur.getUniqueId(), new Object[]{texte, System.currentTimeMillis() + 3000});
        Profil p = plugin.progression().profil(joueur);
        if (p != null) maj(joueur, p);
    }

    /** Redessine et renvoie le HUD (chaque seconde : la barre d'action s'efface toute seule après 3 s). */
    public void maj(Player joueur, Profil p) {
        if (!estAffiche(joueur) || pack == null) return;
        joueur.sendActionBar(dessiner(joueur, p));
    }

    private Component dessiner(Player joueur, Profil p) {
        Reglages r = plugin.reglages();
        Defis defis = plugin.defis();
        Toile t = new Toile(pack);
        Largeurs larg = pack.largeurs();
        long maintenant = System.currentTimeMillis();
        boolean max = p.niveau >= Paliers.MAX;
        int o = MOITIE; // les X du plan partent du centre de l'écran

        // ── Niveau : anneau, chiffre, nom, points ──
        Long montee = montees.get(joueur.getUniqueId());
        boolean flash = montee != null && montee > maintenant && (maintenant / 500) % 2 == 0;
        t.image(pack.get("n_fond", haut(ANNEAU_BAS)), o + ANNEAU_X);
        double avance = max ? 1 : Math.min(1, (double) p.pts / r.besoin(p.niveau));
        int etape = (int) Math.floor(avance * ETAPES_NIVEAU);
        if (etape > 0) {
            t.image(pack.get("n_arc_" + etape, haut(ANNEAU_BAS)), o + ANNEAU_X,
                    flash ? NamedTextColor.WHITE : max ? OR : ORANGE);
        }
        t.texteCentre(String.valueOf(p.niveau), pack.texte(haut(CHIFFRE_BAS)), flash ? OR : NamedTextColor.WHITE,
                o + ANNEAU_X + ANNEAU / 2);

        t.texteDroite(larg.couper(Paliers.get(p.niveau).nom(), NOM_MAX), pack.texte(haut(NOM_BAS)),
                flash ? OR : NamedTextColor.WHITE, o + TEXTE_DROITE);
        String points = r.progression(p.niveau, p.pts);
        t.texteDroite(points, pack.texte(haut(POINTS_BAS)), GRIS, o + TEXTE_DROITE);
        long[] g = gains.get(joueur.getUniqueId());
        if (g != null && g[1] > maintenant) {
            t.texteDroite("+" + g[0], pack.texte(haut(POINTS_BAS)), VERT, o + TEXTE_DROITE - larg.de(points) - 4);
        }

        // ── Ligne du haut : shaker, et bonus duo quand un autre joueur est proche ──
        int attente = plugin.shaker().cooldownRestant(joueur);
        String shaker = attente > 0 ? attente / 60 + ":" + String.format("%02d", attente % 60) : "prêt";
        int x = o + TEXTE_DROITE - larg.de(shaker) + 2;
        t.texte(shaker, pack.texte(haut(LIGNE_HAUT_BAS)), attente > 0 ? GRIS : VERT, x);
        x -= 10;
        t.image(pack.get("shaker", haut(LIGNE_HAUT_BAS)), x, attente > 0 ? GRIS_FONCE : OR);
        Player partenaire = r.duoActif ? plugin.progression().partenaire(joueur) : null;
        if (partenaire != null) {
            String duo = larg.couper(partenaire.getName(), 60) + " +" + r.duoPourcent + "%";
            x -= 6 + larg.de(duo);
            t.texte(duo, pack.texte(haut(LIGNE_HAUT_BAS)), AQUA, x);
            x -= 12;
            t.image(pack.get("duo", haut(LIGNE_HAUT_BAS - 1)), x, AQUA);
        }

        // ── Défis : un anneau par défi, à droite de la hotbar ──
        if (defis.actif()) {
            List<Reglages.Defi> perso = defis.perso();
            int n = perso.size();
            boolean tout = defis.faits(p) == n;
            for (int i = 0; i < n; i++) {
                Reglages.Defi d = perso.get(i);
                double av = Math.min(1, (double) defis.progres(p, i) / d.seuil());
                anneauDefi(t, o + DEFIS_X + i * DEFI_PAS, d, av, tout ? OR : av >= 1 ? VERT : ORANGE);
            }
            Reglages.Defi duo = defis.duo();
            if (duo != null) {
                double av = defis.duoFini() ? 1 : Math.min(1, (double) defis.duoProgres() / duo.seuil());
                anneauDefi(t, o + DEFIS_X + n * DEFI_PAS + DEFI_SEP, duo, av, av >= 1 ? VERT : AQUA);
            }
        }

        // ── Message éventuel, à la place habituelle de la barre d'action ──
        Object[] m = messages.get(joueur.getUniqueId());
        if (m != null && (long) m[1] > maintenant) {
            Component texte = (Component) m[0];
            int w = larg.de(PlainTextComponentSerializer.plainText().serialize(texte));
            t.composant(texte, w, o - w / 2);
        } else if (m != null) {
            messages.remove(joueur.getUniqueId());
        }
        return t.fin(2 * MOITIE);
    }

    /** Un défi : disque, icône, piste, arc de progression et coche. */
    private void anneauDefi(Toile t, int x, Reglages.Defi d, double avance, TextColor couleur) {
        boolean fini = avance >= 1;
        t.image(pack.get("d_disque", haut(DEFI_BAS)), x);
        String tex = texture(d);
        PackHud.Glyphe icone = pack.get(tex, pack.hautIcone(tex));
        if (icone == null) {
            tex = textureParDefaut(d.type());
            icone = pack.get(tex, pack.hautIcone(tex));
        }
        t.image(icone, x + (DEFI - pack.largeurIcone(tex)) / 2, fini ? ICONE_FINIE : NamedTextColor.WHITE);
        t.image(pack.get("d_piste", haut(DEFI_BAS)), x);
        int etape = (int) Math.floor(avance * ETAPES_DEFI);
        if (etape > 0) t.image(pack.get("d_arc_" + etape, haut(DEFI_BAS)), x, couleur);
        if (fini) t.image(pack.get("coche", haut(DEFI_BAS - 12)), x + 13);
    }

    // ───────────── Outils ─────────────

    /** Texture du jeu qui sert d'icône au défi (œuf du mob, minerai du groupe, ou icône du type). */
    private String texture(Reglages.Defi d) {
        if (d.type() == TypeDefi.TUER && d.cible() != null) {
            String oeuf = "item/" + d.cible() + "_spawn_egg";
            if (pack == null || pack.connue(oeuf)) return oeuf;
        }
        if (d.type() == TypeDefi.MINERAIS && d.cible() != null) {
            for (Reglages.GroupeMinage g : plugin.reglages().groupesMinage) {
                if (!g.id().equalsIgnoreCase(d.cible())) continue;
                Material m = g.icone();
                String tex = "item/" + m.getKey().getKey();
                if (pack == null || pack.connue(tex)) return tex;
            }
        }
        return textureParDefaut(d.type());
    }

    private static String textureParDefaut(TypeDefi type) {
        return switch (type) {
            case SAUTS -> "mob_effect/jump_boost";
            case SPRINT -> "mob_effect/speed";
            case NAGE -> "mob_effect/dolphins_grace";
            case DEGATS -> "mob_effect/strength";
            case TUER -> "item/iron_sword";
            case MINERAIS -> "item/iron_pickaxe";
            case VIANDE -> "item/cooked_beef";
            case SHAKER -> "item/honey_bottle";
            case PECHE -> "item/fishing_rod";
            case RECOLTE -> "item/wheat";
            case BUCHES -> "block/oak_log";
            case POINTS -> "item/experience_bottle";
        };
    }
}
