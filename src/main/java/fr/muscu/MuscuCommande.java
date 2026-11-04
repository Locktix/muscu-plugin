package fr.muscu;

import fr.muscu.menu.MenuDefis;
import fr.muscu.menu.MenuGagner;
import fr.muscu.menu.MenuNiveaux;
import fr.muscu.menu.MenuPrincipal;
import fr.muscu.menu.MenuShaker;
import fr.muscu.menu.MenuStats;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /muscu                      ta fiche
 * /muscu defis | gagner | niveaux | shaker | stats
 * /muscu tableau              tableau sur le côté : perso -> classement -> masqué
 * /muscu admin ...            (permission muscu.admin)
 */
public final class MuscuCommande implements BasicCommand {

    private static final String PERM_ADMIN = "muscu.admin";
    private static final List<String> SOUS = List.of("defis", "gagner", "niveaux", "shaker", "stats", "tableau");
    private static final List<String> SOUS_ADMIN = List.of("niveau", "niveau-plus", "points", "reset", "shaker", "defis-relancer", "reload");

    private final MuscuPlugin plugin;

    public MuscuCommande(MuscuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sous = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        if (sous.equals("admin")) {
            if (!sender.hasPermission(PERM_ADMIN)) {
                sender.sendMessage(Txt.msg("Tu n'as pas la permission."));
                return;
            }
            admin(sender, source.getExecutor(), args);
            return;
        }

        Entity executeur = source.getExecutor();
        if (!(executeur instanceof Player joueur)) {
            sender.sendMessage(Txt.msg("Cette commande s'utilise en jeu (ou /muscu admin ... depuis la console)."));
            return;
        }
        switch (sous) {
            case "", "menu", "fiche" -> new MenuPrincipal(plugin, joueur).ouvrir();
            case "gagner", "aide" -> new MenuGagner(plugin, joueur).ouvrir();
            case "niveaux" -> new MenuNiveaux(plugin, joueur).ouvrir();
            case "defis", "défis" -> new MenuDefis(plugin, joueur).ouvrir();
            case "shaker" -> new MenuShaker(plugin, joueur).ouvrir();
            case "stats" -> new MenuStats(plugin, joueur).ouvrir();
            case "classement", "tableau" -> plugin.progression().basculerTableau(joueur);
            default -> sender.sendMessage(Txt.msg("Commandes : /muscu, /muscu defis, /muscu gagner, /muscu niveaux, /muscu shaker, /muscu stats, /muscu tableau"));
        }
    }

    private void admin(CommandSender sender, Entity executeur, String[] args) {
        String sous = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sous) {
            case "reload" -> {
                plugin.recharger();
                sender.sendMessage(Txt.msg("Config rechargée."));
            }
            case "defis-relancer" -> {
                plugin.defis().relancer();
                sender.sendMessage(Txt.msg("Nouveaux défis tirés pour aujourd'hui (progression remise à zéro)."));
            }
            case "niveau" -> {
                Player cible = cible(sender, executeur, args, 2);
                Integer n = nombre(sender, args, 3);
                if (cible == null || n == null) return;
                plugin.progression().definirNiveau(cible, n);
                sender.sendMessage(Txt.msg(cible.getName() + " est maintenant niveau " + plugin.progression().profil(cible).niveau + "."));
            }
            case "niveau-plus" -> {
                Player cible = cible(sender, executeur, args, 2);
                if (cible == null) return;
                Profil p = plugin.progression().profil(cible);
                if (p.niveau >= Paliers.MAX) {
                    sender.sendMessage(Txt.msg(cible.getName() + " est déjà au niveau max."));
                    return;
                }
                // Comme le datapack : on donne juste ce qu'il manque, le passage de niveau se fait normalement
                plugin.progression().gagner(cible, p, Math.max(1, plugin.reglages().besoin(p.niveau) - p.pts));
                // Les joueurs en jeu voient déjà l'annonce de montée de niveau : confirmation seulement pour la console
                if (!(sender instanceof Player)) sender.sendMessage(Txt.msg(cible.getName() + " passe niveau " + p.niveau + "."));
            }
            case "points" -> {
                Player cible = cible(sender, executeur, args, 2);
                Integer n = nombre(sender, args, 3);
                if (cible == null || n == null) return;
                if (n <= 0) {
                    sender.sendMessage(Txt.msg("Donne un nombre de points positif."));
                    return;
                }
                plugin.progression().gagner(cible, plugin.progression().profil(cible), n);
                sender.sendMessage(Txt.msg("+" + Txt.pts(n) + " pour " + cible.getName() + "."));
            }
            case "reset" -> {
                Player cible = cible(sender, executeur, args, 2);
                if (cible == null) return;
                plugin.progression().definirNiveau(cible, 0);
                sender.sendMessage(Txt.msg("Progression de " + cible.getName() + " remise à zéro."));
            }
            case "shaker" -> {
                Player cible = cible(sender, executeur, args, 2);
                if (cible == null) return;
                Map<Integer, org.bukkit.inventory.ItemStack> trop = cible.getInventory().addItem(plugin.shaker().creer());
                trop.values().forEach(i -> cible.getWorld().dropItem(cible.getLocation(), i));
                sender.sendMessage(Txt.msg("Shaker donné à " + cible.getName() + "."));
            }
            default -> sender.sendMessage(Txt.msg(Txt.t(
                    "/muscu admin niveau <joueur> <0-10> | niveau-plus [joueur] | points <joueur> <n> | reset <joueur> | shaker [joueur] | defis-relancer | reload",
                    NamedTextColor.YELLOW)));
        }
    }

    /** Joueur en ligne args[i], ou l'exécuteur si absent. */
    private Player cible(CommandSender sender, Entity executeur, String[] args, int i) {
        if (args.length > i) {
            Player p = Bukkit.getPlayerExact(args[i]);
            if (p == null || plugin.progression().profil(p) == null) {
                sender.sendMessage(Txt.msg("Joueur introuvable (il doit être connecté) : " + args[i]));
            }
            return p == null || plugin.progression().profil(p) == null ? null : p;
        }
        if (executeur instanceof Player p) return p;
        sender.sendMessage(Txt.msg("Précise un joueur."));
        return null;
    }

    private static Integer nombre(CommandSender sender, String[] args, int i) {
        if (args.length <= i) {
            sender.sendMessage(Txt.msg("Il manque un nombre."));
            return null;
        }
        try {
            return Integer.parseInt(args[i]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Txt.msg("Ce n'est pas un nombre : " + args[i]));
            return null;
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        boolean admin = source.getSender().hasPermission(PERM_ADMIN);
        List<String> choix = new ArrayList<>();
        if (args.length <= 1) {
            choix.addAll(SOUS);
            if (admin) choix.add("admin");
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                choix.addAll(SOUS_ADMIN);
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("reload") && !args[1].equalsIgnoreCase("defis-relancer")) {
                Bukkit.getOnlinePlayers().forEach(p -> choix.add(p.getName()));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("niveau")) {
                for (int n = 0; n <= Paliers.MAX; n++) choix.add(String.valueOf(n));
            }
        }
        String debut = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choix.stream().filter(c -> c.toLowerCase(Locale.ROOT).startsWith(debut)).toList();
    }
}
