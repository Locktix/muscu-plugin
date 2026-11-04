package fr.muscu.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Assemble le HUD : chaque élément est posé à une position X précise. Entre deux éléments, on insère
 * des espaces (positifs ou négatifs) de la police HUD pour amener le "curseur" du texte au bon endroit.
 */
final class Toile {

    private final PackHud pack;
    private final List<Component> morceaux = new ArrayList<>();
    private int curseur;

    Toile(PackHud pack) {
        this.pack = pack;
    }

    /** Image (blanche = couleurs d'origine). */
    Toile image(PackHud.Glyphe g, int x) {
        return image(g, x, NamedTextColor.WHITE);
    }

    /** Image teintée par une couleur (les images grises prennent cette couleur). */
    Toile image(PackHud.Glyphe g, int x, TextColor teinte) {
        if (g == null) return this;
        aller(x);
        morceaux.add(Component.text(g.c(), teinte).font(PackHud.POLICE).shadowColor(ShadowColor.none()));
        curseur += g.avance();
        return this;
    }

    /** Texte dont le premier pixel est à x. */
    Toile texte(String s, Key police, TextColor couleur, int x) {
        if (s.isEmpty()) return this;
        aller(x);
        morceaux.add(Component.text(s, couleur).font(police));
        curseur += pack.largeurs().de(s);
        return this;
    }

    /** Texte dont le dernier pixel est à x. */
    Toile texteDroite(String s, Key police, TextColor couleur, int x) {
        return texte(s, police, couleur, x - pack.largeurs().de(s) + 2);
    }

    /** Texte centré sur x. */
    Toile texteCentre(String s, Key police, TextColor couleur, int x) {
        return texte(s, police, couleur, x - (pack.largeurs().de(s) - 1) / 2);
    }

    /** Composant quelconque (message) dont le premier pixel est à x et qui fait "avance" px. */
    Toile composant(Component c, int avance, int x) {
        aller(x);
        morceaux.add(c);
        curseur += avance;
        return this;
    }

    private void aller(int x) {
        int d = x - curseur;
        if (d != 0) morceaux.add(Component.text(PackHud.espace(d)).font(PackHud.POLICE));
        curseur = x;
    }

    /** Termine le HUD : sa largeur totale vaut "largeur" (le jeu le centre sur l'écran). */
    Component fin(int largeur) {
        aller(largeur);
        TextComponent.Builder b = Component.text();
        morceaux.forEach(b::append);
        return b.build();
    }
}
