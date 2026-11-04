package fr.muscu.hud;

/**
 * Plan du HUD, en pixels de l'interface (ceux de Minecraft, avant l'échelle de l'interface).
 * Le HUD est le texte de la barre d'action : le jeu le centre horizontalement, sa ligne de texte est à
 * 72 px du bas de l'écran. Les images sont décalées vers le bas pour se placer autour de la hotbar.
 * <ul>
 *   <li>X : par rapport au centre de l'écran (la hotbar va de -91 à +91) ;</li>
 *   <li>"bas" : distance entre le haut de l'élément et le bas de l'écran.</li>
 * </ul>
 * <pre>
 *                     ♥♥♥♥♥♥♥♥♥♥       🍗🍗🍗🍗🍗🍗🍗🍗🍗🍗
 *  ⚇ Ami  ✚ prêt     ═══════ XP ═══════════════
 *      Gros cœur (6)  [  hotbar  ]  (ic)(ic)(ic) (duo)
 *         +3 47/140   [          ]
 * </pre>
 */
final class Plan {

    private Plan() {}

    /** Ligne de texte de référence du HUD (premier titre de barre de boss : y = 3). */
    static final int Y_TEXTE = 3;
    /** Distance entre la ligne de texte de la barre d'action et le bas de l'écran. */
    static final int LIGNE_ACTION = 72;

    /** Position verticale (dans le repère de PackHud) d'un élément dont le haut est à "bas" px du bas de l'écran. */
    static int haut(int bas) {
        return Y_TEXTE + LIGNE_ACTION - bas;
    }

    /** Demi-largeur du HUD : le texte fait 2 × MOITIE de large, centré sur l'écran. */
    static final int MOITIE = 300;

    // ── Niveau, à gauche de la hotbar (après l'emplacement de la main gauche) ──
    static final int ANNEAU = 30, ANNEAU_EP = 4;
    static final int ANNEAU_X = -91 - 29 - 6 - ANNEAU, ANNEAU_BAS = 31;
    static final int CHIFFRE_BAS = 20;
    static final int TEXTE_DROITE = ANNEAU_X - 5;
    static final int NOM_BAS = 26, POINTS_BAS = 15;
    static final int NOM_MAX = 80;
    static final int LIGNE_HAUT_BAS = 39;
    static final int ETAPES_NIVEAU = 40;

    // ── Défis, à droite de la hotbar ──
    static final int DEFI = 22, DEFI_EP = 2;
    static final int DEFIS_X = 91 + 6, DEFI_PAS = 25, DEFI_SEP = 4, DEFI_BAS = 24;
    static final int ETAPES_DEFI = 24;
}
