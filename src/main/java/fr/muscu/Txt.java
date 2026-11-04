package fr.muscu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Petits raccourcis pour fabriquer du texte coloré. */
public final class Txt {

    private Txt() {}

    public static Component t(String texte, TextColor couleur) {
        return Component.text(texte, couleur);
    }

    public static Component gras(String texte, TextColor couleur) {
        return Component.text(texte, couleur).decorate(TextDecoration.BOLD);
    }

    public static Component de(Component... morceaux) {
        return Component.textOfChildren(morceaux);
    }

    /** Barre "■■■■■■■■■■" : les cases pleines en vert, le reste en gris foncé. */
    public static Component barre(int pleines, int total) {
        pleines = Math.max(0, Math.min(total, pleines));
        return de(t("■".repeat(pleines), NamedTextColor.GREEN), t("■".repeat(total - pleines), NamedTextColor.DARK_GRAY));
    }

    /** "★5" doré (gris au niveau 0, en gras au niveau max). */
    public static Component etoile(int niveau) {
        if (niveau <= 0) return t("★0", NamedTextColor.GRAY);
        if (niveau >= Paliers.MAX) return gras("★" + niveau, NamedTextColor.GOLD);
        return t("★" + niveau, NamedTextColor.GOLD);
    }

    /** Message de chat préfixé "✦ Muscu". */
    public static Component msg(String texte) {
        return de(gras("✦ Muscu ", NamedTextColor.GOLD), t(texte, NamedTextColor.GRAY));
    }

    public static Component msg(Component contenu) {
        return de(gras("✦ Muscu ", NamedTextColor.GOLD), contenu.colorIfAbsent(NamedTextColor.GRAY));
    }

    /** "1 pt", "3 pts". */
    public static String pts(int n) {
        return n + (Math.abs(n) > 1 ? " pts" : " pt");
    }

    /** "1 saut", "4 sauts". */
    public static String nb(int n, String singulier, String pluriel) {
        return n + " " + (n > 1 ? pluriel : singulier);
    }

    /** "3 min 12 s", "45 s". */
    public static String duree(int secondes) {
        int m = secondes / 60, s = secondes % 60;
        if (m == 0) return s + " s";
        return s == 0 ? m + " min" : m + " min " + s + " s";
    }
}
