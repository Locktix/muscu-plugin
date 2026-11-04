package fr.muscu;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;

/**
 * Bonus d'attributs selon le niveau (valeur totale pour chaque niveau 0..10).
 * Les clés (muscu:minage, muscu:vie...) sont celles du datapack : les anciens bonus sont remplacés proprement.
 */
public enum Bonus {

    MINAGE("minage", "⛏", "Minage", NamedTextColor.AQUA, Material.IRON_PICKAXE,
            Attribute.BLOCK_BREAK_SPEED, Operation.ADD_SCALAR,
            new double[]{0, .05, .05, .05, .05, .10, .10, .10, .10, .10, .10}),
    VIE("vie", "❤", "Cœurs", NamedTextColor.RED, Material.GOLDEN_APPLE,
            Attribute.MAX_HEALTH, Operation.ADD_NUMBER,
            new double[]{0, 0, 2, 2, 2, 2, 4, 4, 4, 4, 6}),
    FORCE("force", "⚔", "Dégâts", NamedTextColor.WHITE, Material.IRON_SWORD,
            Attribute.ATTACK_DAMAGE, Operation.ADD_NUMBER,
            new double[]{0, 0, 0, .5, .5, .5, .5, 1, 1, 1, 1}),
    VITESSE("vitesse", "➹", "Vitesse", NamedTextColor.YELLOW, Material.SUGAR,
            Attribute.MOVEMENT_SPEED, Operation.ADD_SCALAR,
            new double[]{0, 0, 0, 0, .03, .03, .03, .03, .03, .06, .06}),
    CHUTE("chute", "⬇", "Chute", NamedTextColor.LIGHT_PURPLE, Material.FEATHER,
            Attribute.SAFE_FALL_DISTANCE, Operation.ADD_NUMBER,
            new double[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1});

    public final NamespacedKey cle;
    public final String symbole;
    public final String nom;
    public final TextColor couleur;
    public final Material icone;
    private final Attribute attribut;
    private final Operation operation;
    private final double[] valeurs;

    Bonus(String id, String symbole, String nom, TextColor couleur, Material icone,
          Attribute attribut, Operation operation, double[] valeurs) {
        this.cle = new NamespacedKey("muscu", id);
        this.symbole = symbole;
        this.nom = nom;
        this.couleur = couleur;
        this.icone = icone;
        this.attribut = attribut;
        this.operation = operation;
        this.valeurs = valeurs;
    }

    public double valeur(int niveau) {
        return valeurs[Math.max(0, Math.min(Paliers.MAX, niveau))];
    }

    /** Prochain niveau où ce bonus augmente, ou -1 s'il est déjà au max. */
    public int prochainPalier(int niveau) {
        double actuel = valeur(niveau);
        for (int n = niveau + 1; n <= Paliers.MAX; n++) {
            if (valeur(n) != actuel) return n;
        }
        return -1;
    }

    /** "+5%", "+2 cœurs", "+0,5", "+1 bloc". */
    public String format(double v) {
        return switch (this) {
            case MINAGE, VITESSE -> "+" + Math.round(v * 100) + "%";
            case VIE -> {
                int coeurs = (int) Math.round(v / 2);
                yield "+" + coeurs + (coeurs > 1 ? " cœurs" : " cœur");
            }
            case FORCE -> "+" + (v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v).replace('.', ','));
            case CHUTE -> "+" + (int) v + ((int) v > 1 ? " blocs" : " bloc");
        };
    }

    /** Retire puis remet tous les bonus du joueur selon son niveau. */
    public static void appliquer(Player joueur, int niveau) {
        for (Bonus b : values()) {
            AttributeInstance inst = joueur.getAttribute(b.attribut);
            if (inst == null) continue;
            if (inst.getModifier(b.cle) != null) inst.removeModifier(b.cle);
            double v = b.valeur(niveau);
            if (v != 0) inst.addModifier(new AttributeModifier(b.cle, v, b.operation));
        }
    }
}
