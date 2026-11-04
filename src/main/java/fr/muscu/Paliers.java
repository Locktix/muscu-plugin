package fr.muscu;

import org.bukkit.Material;

import java.util.List;

/** Les 11 paliers (niveau 0 à 10) : nom, icône et bonus débloqué. Mêmes textes que le datapack. */
public final class Paliers {

    public static final int MAX = 10;

    public record Palier(int niveau, String nom, Material icone, String bonus) {}

    private static final List<Palier> LISTE = List.of(
            new Palier(0, "Débutant", Material.ANVIL, "Pas encore de bonus"),
            new Palier(1, "Premier échauffement", Material.IRON_PICKAXE, "+5% vitesse de minage"),
            new Palier(2, "Petit cœur", Material.APPLE, "+1 cœur"),
            new Palier(3, "Coup de poing", Material.IRON_SWORD, "+0,5 dégât d'attaque"),
            new Palier(4, "Cardio", Material.SUGAR, "+3% vitesse de déplacement"),
            new Palier(5, "Bras de mineur", Material.DIAMOND_PICKAXE, "Minage à +10%"),
            new Palier(6, "Gros cœur", Material.GOLDEN_APPLE, "+2 cœurs au total"),
            new Palier(7, "Frappe lourde", Material.DIAMOND_SWORD, "+1 dégât au total"),
            new Palier(8, "Genoux solides", Material.FEATHER, "+1 bloc de chute sans dégâts"),
            new Palier(9, "Sprinteur", Material.RABBIT_FOOT, "Vitesse à +6%"),
            new Palier(10, "Monstre de la salle", Material.ENCHANTED_GOLDEN_APPLE, "+3 cœurs au total")
    );

    private Paliers() {}

    public static Palier get(int niveau) {
        return LISTE.get(Math.max(0, Math.min(MAX, niveau)));
    }
}
