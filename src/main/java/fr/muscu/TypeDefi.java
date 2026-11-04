package fr.muscu;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Types de défis. "unites" = unités brutes comptées pour 1 unité affichée
 * (le sprint et la nage arrivent en cm, les dégâts en dixièmes de demi-cœur).
 */
public enum TypeDefi {

    SAUTS("sauts", Material.RABBIT_FOOT, 1, "Saute %d fois", "Sautez %d fois"),
    SPRINT("sprint", Material.LEATHER_BOOTS, 100, "Sprinte sur %d blocs", "Sprintez sur %d blocs"),
    NAGE("nage", Material.TROPICAL_FISH, 100, "Nage sur %d blocs", "Nagez sur %d blocs"),
    DEGATS("degats", Material.IRON_SWORD, 20, "Inflige %d cœurs de dégâts", "Infligez %d cœurs de dégâts"),
    TUER("tuer", Material.ZOMBIE_HEAD, 1, "Tue %d mobs", "Tuez %d mobs"),
    MINERAIS("minerais", Material.IRON_PICKAXE, 1, "Mine %d minerais", "Minez %d minerais"),
    VIANDE("viande", Material.COOKED_BEEF, 1, "Mange %d viandes cuites", "Mangez %d viandes cuites"),
    SHAKER("shaker", Material.HONEY_BOTTLE, 1, "Bois %d shaker(s)", "Buvez %d shakers"),
    PECHE("peche", Material.FISHING_ROD, 1, "Pêche %d prises", "Pêchez %d prises"),
    RECOLTE("recolte", Material.WHEAT, 1, "Récolte %d cultures mûres", "Récoltez %d cultures mûres"),
    BUCHES("buches", Material.OAK_LOG, 1, "Coupe %d bûches", "Coupez %d bûches"),
    POINTS("points", Material.EXPERIENCE_BOTTLE, 1, "Gagne %d points de muscu", "Gagnez %d points de muscu");

    public final String id;
    public final Material icone;
    public final int unites;
    private final String perso;
    private final String duo;

    TypeDefi(String id, Material icone, int unites, String perso, String duo) {
        this.id = id;
        this.icone = icone;
        this.unites = unites;
        this.perso = perso;
        this.duo = duo;
    }

    public String phrase(int quantite, boolean enDuo) {
        if (this == SHAKER && quantite == 1 && !enDuo) return "Bois un shaker";
        return String.format(Locale.ROOT, enDuo ? duo : perso, quantite);
    }

    public static TypeDefi parse(String id) {
        if (id == null) return null;
        for (TypeDefi t : values()) {
            if (t.id.equalsIgnoreCase(id.trim())) return t;
        }
        return null;
    }

    /** Type de défi correspondant à une activité suivie par statistique (null pour les mobs tués : vus par événement). */
    public static TypeDefi depuisActivite(String idActivite) {
        return switch (idActivite) {
            case "sauts" -> SAUTS;
            case "sprint" -> SPRINT;
            case "nage" -> NAGE;
            case "degats" -> DEGATS;
            default -> null;
        };
    }
}
