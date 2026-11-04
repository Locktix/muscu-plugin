package fr.muscu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Défis du jour : les mêmes pour tout le monde (tirés au hasard à partir de la date), chacun progresse de son côté,
 * plus un défi duo commun où tout le monde contribue. Remise à zéro à minuit (heure du serveur).
 */
public final class Defis {

    private final MuscuPlugin plugin;
    private final File fichier;
    private final YamlConfiguration donnees;

    private LocalDate jour;
    private int tirage;
    private List<Reglages.Defi> perso = List.of();
    private Reglages.Defi duo;
    private String signaturePerso = "";
    private String signatureDuo = "";
    private int duoProgres;
    private boolean duoFini;
    private final Map<UUID, Integer> contributions = new HashMap<>();
    private boolean aSauver;
    private int compteur;

    public Defis(MuscuPlugin plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "defis.yml");
        this.donnees = YamlConfiguration.loadConfiguration(fichier);

        LocalDate aujourdhui = LocalDate.now();
        String enregistre = donnees.getString("jour");
        if (aujourdhui.toString().equals(enregistre)) {
            jour = aujourdhui;
            tirage = donnees.getInt("tirage");
            duoProgres = donnees.getInt("duo.progres");
            duoFini = donnees.getBoolean("duo.fini");
            signatureDuo = donnees.getString("duo.signature", "");
            ConfigurationSection c = donnees.getConfigurationSection("duo.contributions");
            if (c != null) {
                for (String k : c.getKeys(false)) {
                    try {
                        contributions.put(UUID.fromString(k), c.getInt(k));
                    } catch (IllegalArgumentException ignore) {
                        // clé abîmée : on l'oublie
                    }
                }
            }
        } else {
            jour = aujourdhui;
            tirage = 0;
            aSauver = true;
        }
        tirer();
    }

    // ───────────── Tirage ─────────────

    /** (Re)tire les défis du jour à partir de la date : même résultat pour tout le monde, même après un redémarrage. */
    public void tirer() {
        Reglages r = plugin.reglages();
        if (!r.defisActifs) {
            perso = List.of();
            duo = null;
            return;
        }
        Random rnd = new Random(jour.toEpochDay() * 1_000_003L + tirage * 7_919L);
        List<Reglages.Defi> pool = new ArrayList<>(r.defisPerso);
        Collections.shuffle(pool, rnd);
        List<Reglages.Defi> choisis = new ArrayList<>();
        Set<TypeDefi> types = EnumSet.noneOf(TypeDefi.class);
        for (Reglages.Defi d : pool) {
            if (choisis.size() >= r.defisParJour) break;
            if (types.add(d.type())) choisis.add(d);
        }
        perso = List.copyOf(choisis);
        duo = r.defisDuo.isEmpty() ? null : r.defisDuo.get(rnd.nextInt(r.defisDuo.size()));

        // Si la config a changé (reload), les défis tirés peuvent être différents : on repart de zéro.
        signaturePerso = Integer.toHexString(perso.stream().map(Defis::signature).toList().toString().hashCode());
        String sigDuo = duo == null ? "" : signature(duo);
        if (!sigDuo.equals(signatureDuo)) {
            reinitialiserDuo();
            signatureDuo = sigDuo;
            aSauver = true;
        }
    }

    private static String signature(Reglages.Defi d) {
        return d.type().id + ":" + d.cible() + ":" + d.quantite() + ":" + d.points();
    }

    /** Admin : nouveau tirage pour aujourd'hui (progression remise à zéro). */
    public void relancer() {
        tirage++;
        reinitialiserDuo();
        tirer();
        aSauver = true;
        sauver();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Txt.msg(Txt.de(Txt.t("Nouveaux défis du jour ! ", NamedTextColor.YELLOW), lien())));
        }
    }

    /** À appeler régulièrement : passe au jour suivant à minuit. */
    public void tic() {
        LocalDate aujourdhui = LocalDate.now();
        if (!aujourdhui.equals(jour)) {
            jour = aujourdhui;
            tirage = 0;
            reinitialiserDuo();
            tirer();
            aSauver = true;
            if (actif()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(Txt.msg(Txt.de(Txt.t("☀ Nouveaux défis du jour ! ", NamedTextColor.YELLOW), lien())));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
                }
            }
        }
        if (++compteur >= 30) {
            compteur = 0;
            sauver();
        }
    }

    private void reinitialiserDuo() {
        duoProgres = 0;
        duoFini = false;
        contributions.clear();
    }

    // ───────────── Progression ─────────────

    /**
     * Le joueur vient de faire "brut" unités d'une activité.
     * @param detail précision (type de mob "zombie", groupe de minerai "rare"...) ou null
     */
    public void progresser(Player joueur, TypeDefi type, String detail, int brut) {
        if (!actif() || brut <= 0) return;
        Profil p = plugin.progression().profil(joueur);
        if (p == null) return;
        etat(p);
        boolean change = false;

        for (int i = 0; i < perso.size(); i++) {
            Reglages.Defi d = perso.get(i);
            if (d.type() != type || !correspond(d, detail)) continue;
            int avant = p.defisProgres[i];
            if (avant >= d.seuil()) continue;
            p.defisProgres[i] = (int) Math.min(d.seuil(), (long) avant + brut);
            change = true;
            if (p.defisProgres[i] >= d.seuil()) reussi(joueur, p, d);
        }

        int bonus = plugin.reglages().defisBonusComplet;
        if (change && !p.defisBonus && toutFini(p)) {
            p.defisBonus = true;
            p.statJournees++;
            joueur.sendMessage(Txt.msg(Txt.de(Txt.gras("★ Tous les défis du jour réussis ! ", NamedTextColor.GOLD),
                    Txt.t(bonus > 0 ? "+" + Txt.pts(bonus) : "Bravo !", NamedTextColor.GREEN))));
            joueur.playSound(joueur.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            if (bonus > 0) plugin.progression().gagner(joueur, p, bonus, false);
        }
        if (change) p.sauver(joueur);

        if (duo != null && !duoFini && duo.type() == type && correspond(duo, detail)) {
            int part = Math.min(brut, duo.seuil() - duoProgres);
            duoProgres += part;
            contributions.merge(joueur.getUniqueId(), part, Integer::sum);
            aSauver = true;
            if (duoProgres >= duo.seuil()) duoReussi();
        }
    }

    private void reussi(Player joueur, Profil p, Reglages.Defi d) {
        joueur.sendMessage(Txt.msg(Txt.de(Txt.t("✔ Défi réussi : ", NamedTextColor.GREEN),
                texte(d, false).color(NamedTextColor.WHITE), Txt.t("  +" + Txt.pts(d.points()), NamedTextColor.GREEN))));
        joueur.playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        p.statDefis++;
        if (d.points() > 0) plugin.progression().gagner(joueur, p, d.points(), false);
    }

    private void duoReussi() {
        duoFini = true;
        aSauver = true;
        List<String> noms = new ArrayList<>();
        for (UUID id : contributions.keySet()) {
            Player j = Bukkit.getPlayer(id);
            if (j != null && plugin.progression().profil(j) != null) {
                noms.add(j.getName());
                plugin.progression().profil(j).statDuos++;
                if (duo.points() > 0) plugin.progression().gagner(j, plugin.progression().profil(j), duo.points(), false);
                j.playSound(j.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            } else {
                String nom = Bukkit.getOfflinePlayer(id).getName();
                noms.add(nom == null ? "?" : nom);
                String cle = "en-attente." + id;
                donnees.set(cle, donnees.getInt(cle) + duo.points());
                donnees.set("en-attente-duos." + id, donnees.getInt("en-attente-duos." + id) + 1);
            }
        }
        Component msg = Txt.msg(Txt.de(Txt.gras("✦ Défi duo réussi ! ", NamedTextColor.AQUA), texte(duo, true).color(NamedTextColor.WHITE),
                Txt.t("  +" + Txt.pts(duo.points()) + " pour " + String.join(" et ", noms), NamedTextColor.GREEN)));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        sauver();
    }

    // ───────────── Connexion ─────────────

    public void connexion(Player joueur) {
        if (!actif()) return;
        Profil p = plugin.progression().profil(joueur);
        if (p == null) return;
        etat(p);

        String cle = "en-attente." + joueur.getUniqueId();
        int attente = donnees.getInt(cle);
        int duosRates = donnees.getInt("en-attente-duos." + joueur.getUniqueId());
        if (duosRates > 0) {
            p.statDuos += duosRates;
            donnees.set("en-attente-duos." + joueur.getUniqueId(), null);
            p.sauver(joueur);
            sauverTout();
        }
        if (attente > 0) {
            donnees.set(cle, null);
            sauverTout();
            joueur.sendMessage(Txt.msg(Txt.de(Txt.t("Pendant ton absence, le défi duo a été réussi : ", NamedTextColor.GRAY),
                    Txt.t("+" + Txt.pts(attente), NamedTextColor.GREEN))));
            plugin.progression().gagner(joueur, p, attente, false);
        }

        int faits = faits(p);
        joueur.sendMessage(Txt.msg(Txt.de(Txt.t("Défis du jour : ", NamedTextColor.GRAY),
                Txt.t(faits + "/" + perso.size() + " réussis", faits == perso.size() ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                Txt.t("  ", NamedTextColor.GRAY), lien())));
    }

    // ───────────── Lecture (menus) ─────────────

    public boolean actif() {
        return plugin.reglages().defisActifs && !perso.isEmpty();
    }

    public List<Reglages.Defi> perso() {
        return perso;
    }

    public Reglages.Defi duo() {
        return duo;
    }

    public int duoProgres() {
        return duoProgres;
    }

    public boolean duoFini() {
        return duoFini;
    }

    public int contribution(Player joueur) {
        return contributions.getOrDefault(joueur.getUniqueId(), 0);
    }

    /** Progression brute du joueur sur le défi n° i. */
    public int progres(Profil p, int i) {
        etat(p);
        return i < p.defisProgres.length ? p.defisProgres[i] : 0;
    }

    public int faits(Profil p) {
        etat(p);
        int n = 0;
        for (int i = 0; i < perso.size(); i++) if (p.defisProgres[i] >= perso.get(i).seuil()) n++;
        return n;
    }

    public boolean toutFini(Profil p) {
        return faits(p) == perso.size();
    }

    public Duration avantNouveaux() {
        return Duration.between(LocalDateTime.now(), jour.plusDays(1).atStartOfDay());
    }

    /** "Mine 30 minerais", "Tue 8 × Zombie", "Sprintez sur 2000 blocs à deux". */
    public Component texte(Reglages.Defi d, boolean enDuo) {
        Component base;
        if (d.type() == TypeDefi.TUER && d.cible() != null) {
            EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(d.cible()));
            base = Component.text((enDuo ? "Tuez " : "Tue ") + d.quantite() + " × ")
                    .append(type == null ? Component.text(d.cible()) : Component.translatable(type.translationKey()));
        } else if (d.type() == TypeDefi.MINERAIS && d.cible() != null) {
            String nom = d.cible();
            for (Reglages.GroupeMinage g : plugin.reglages().groupesMinage) {
                if (g.id().equalsIgnoreCase(d.cible())) nom = g.nom().toLowerCase(java.util.Locale.ROOT);
            }
            if (d.quantite() == 1) nom = nom.replaceAll("s(?=\\s|$)", "");
            base = Component.text((enDuo ? "Minez " : "Mine ") + d.quantite() + " " + nom);
        } else if (d.type() == TypeDefi.MINERAIS && d.quantite() == 1) {
            base = Component.text(enDuo ? "Minez 1 minerai" : "Mine 1 minerai");
        } else {
            base = Component.text(d.type().phrase(d.quantite(), enDuo));
        }
        return enDuo ? base.append(Component.text(" à deux")) : base;
    }

    /** Libellé court pour le tableau : "Zombie", "Minerais rares", "Pêche"... */
    public Component court(Reglages.Defi d) {
        if (d.type() == TypeDefi.TUER && d.cible() != null) {
            EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(d.cible()));
            return type == null ? Component.text(d.cible()) : Component.translatable(type.translationKey());
        }
        if (d.type() == TypeDefi.MINERAIS && d.cible() != null) {
            for (Reglages.GroupeMinage g : plugin.reglages().groupesMinage) {
                if (g.id().equalsIgnoreCase(d.cible())) return Component.text(g.nom());
            }
        }
        return Component.text(switch (d.type()) {
            case SAUTS -> "Sauts";
            case SPRINT -> "Sprint";
            case NAGE -> "Nage";
            case DEGATS -> "Dégâts";
            case TUER -> "Mobs tués";
            case MINERAIS -> "Minerais";
            case VIANDE -> "Viande cuite";
            case SHAKER -> "Shaker";
            case PECHE -> "Pêche";
            case RECOLTE -> "Récoltes";
            case BUCHES -> "Bûches";
            case POINTS -> "Points muscu";
        });
    }

    public Material icone(Reglages.Defi d) {
        if (d.type() == TypeDefi.TUER && d.cible() != null) {
            Material oeuf = Material.matchMaterial(d.cible() + "_spawn_egg");
            if (oeuf != null) return oeuf;
        }
        if (d.type() == TypeDefi.MINERAIS && d.cible() != null) {
            for (Reglages.GroupeMinage g : plugin.reglages().groupesMinage) {
                if (g.id().equalsIgnoreCase(d.cible())) return g.icone();
            }
        }
        return d.type().icone;
    }

    // ───────────── Interne ─────────────

    private String cleDuJour() {
        return jour + "#" + tirage + "#" + signaturePerso;
    }

    /** Remet à zéro la progression perso si elle date d'un autre jour / tirage. */
    private void etat(Profil p) {
        String cle = cleDuJour();
        if (!cle.equals(p.defisCle)) {
            p.defisCle = cle;
            java.util.Arrays.fill(p.defisProgres, 0);
            p.defisBonus = false;
        }
    }

    private static boolean correspond(Reglages.Defi d, String detail) {
        return d.cible() == null || d.cible().equalsIgnoreCase(detail);
    }

    private static Component lien() {
        return Txt.t("[Voir les défis]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/muscu defis"));
    }

    public void sauver() {
        if (!aSauver) return;
        aSauver = false;
        donnees.set("jour", jour.toString());
        donnees.set("tirage", tirage);
        donnees.set("duo.progres", duoProgres);
        donnees.set("duo.fini", duoFini);
        donnees.set("duo.signature", signatureDuo);
        donnees.set("duo.contributions", null);
        contributions.forEach((id, v) -> donnees.set("duo.contributions." + id, v));
        try {
            plugin.getDataFolder().mkdirs();
            donnees.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'enregistrer defis.yml : " + e.getMessage());
        }
    }

    /** Force l'enregistrement (arrêt du serveur). */
    public void sauverTout() {
        aSauver = true;
        sauver();
    }
}
