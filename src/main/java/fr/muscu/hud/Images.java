package fr.muscu.hud;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static fr.muscu.hud.Dessin.argb;
import static fr.muscu.hud.Dessin.gris;
import static fr.muscu.hud.Plan.*;

/**
 * Toutes les images du HUD, dessinées en pixel art. Les arcs de progression sont blancs et prennent
 * la couleur du texte en jeu (orange, vert, turquoise...) : une seule série d'images pour toutes les couleurs.
 */
final class Images {

    private Images() {}

    private static final int CONTOUR = argb(230, 0x000000);
    private static final int PISTE = argb(255, 0x2A2A2A);

    static PackHud fabriquer(Collection<String> textures) throws IOException {
        PackHud pack = new PackHud(Largeurs.charger(), lireIcones());

        // ── Anneau du niveau : fond (disque + piste), puis un arc par étape de progression ──
        pack.image("n_fond", fond(ANNEAU, ANNEAU_EP, argb(200, 0x141018), true), haut(ANNEAU_BAS));
        for (int k = 1; k <= ETAPES_NIVEAU; k++) {
            pack.image("n_arc_" + k, arc(ANNEAU, ANNEAU_EP, (double) k / ETAPES_NIVEAU), haut(ANNEAU_BAS));
        }

        // ── Anneaux des défis : disque (sous l'icône), piste (par-dessus), arcs ──
        pack.image("d_disque", disque(DEFI, DEFI_EP, argb(150, 0x000000)), haut(DEFI_BAS));
        pack.image("d_piste", fond(DEFI, DEFI_EP, 0, false), haut(DEFI_BAS));
        for (int k = 1; k <= ETAPES_DEFI; k++) {
            pack.image("d_arc_" + k, arc(DEFI, DEFI_EP, (double) k / ETAPES_DEFI), haut(DEFI_BAS));
        }
        pack.image("coche", coche(), haut(DEFI_BAS - 12));

        // ── Petites icônes : duo et shaker ──
        pack.image("duo", duo(), haut(LIGNE_HAUT_BAS - 1));
        pack.image("shaker", shaker(), haut(LIGNE_HAUT_BAS));

        // ── Icônes des défis : textures du jeu, centrées dans leur anneau ──
        for (String t : textures) pack.vanilla(t, pack.hautIcone(t));

        // Lignes de texte
        pack.texte(haut(CHIFFRE_BAS));
        pack.texte(haut(NOM_BAS));
        pack.texte(haut(POINTS_BAS));
        pack.texte(haut(LIGNE_HAUT_BAS));

        pack.finir(icone());
        return pack;
    }

    private static Map<String, int[]> lireIcones() throws IOException {
        Map<String, int[]> m = new HashMap<>();
        try (InputStream in = Images.class.getResourceAsStream("/hud/icones.txt")) {
            if (in == null) throw new IOException("hud/icones.txt introuvable dans le plugin");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String ligne; (ligne = r.readLine()) != null; ) {
                if (ligne.isBlank() || ligne.startsWith("#")) continue;
                int eg = ligne.indexOf('=');
                String[] v = ligne.substring(eg + 1).split(",");
                m.put(ligne.substring(0, eg), new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
            }
        }
        return m;
    }

    // ───────────── Anneaux ─────────────

    /** Distance au centre d'un pixel d'une image n × n. */
    private static double dist(int n, int x, int y) {
        double c = (n - 1) / 2.0;
        return Math.hypot(x - c, y - c);
    }

    /** Angle d'un pixel, de 0 (en haut) à 1, dans le sens des aiguilles d'une montre. */
    private static double angle(int n, int x, int y) {
        double c = (n - 1) / 2.0;
        double a = Math.atan2(x - c, -(y - c));
        if (a < 0) a += 2 * Math.PI;
        return a / (2 * Math.PI);
    }

    /** Fond d'un anneau : contour noir, piste sombre, et (si demandé) le disque intérieur. */
    static Dessin fond(int n, int ep, int interieur, boolean avecDisque) {
        Dessin d = new Dessin(n, n);
        double rExt = n / 2.0, rInt = rExt - ep;
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                double r = dist(n, x, y);
                if (r > rExt - 0.2) continue;
                if (r > rExt - 1.2) d.px(x, y, CONTOUR);
                else if (r >= rInt) d.px(x, y, PISTE);
                else if (r > rInt - 1) d.px(x, y, CONTOUR);
                else if (avecDisque) d.px(x, y, interieur);
            }
        }
        return d;
    }

    /** Disque intérieur seul (sous l'icône d'un défi). */
    static Dessin disque(int n, int ep, int couleur) {
        Dessin d = new Dessin(n, n);
        double rInt = n / 2.0 - ep;
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                if (dist(n, x, y) <= rInt - 1) d.px(x, y, couleur);
            }
        }
        return d;
    }

    /** Arc de progression (blanc, teinté en jeu) de 0 à "avance", sur la piste de l'anneau. */
    static Dessin arc(int n, int ep, double avance) {
        Dessin d = new Dessin(n, n);
        double rExt = n / 2.0, rInt = rExt - ep;
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                double r = dist(n, x, y);
                if (r > rExt - 1.2 || r < rInt) continue;
                if (angle(n, x, y) <= avance + 1e-9) {
                    // un peu plus clair sur le bord extérieur : effet de relief
                    d.px(x, y, gris(r > rExt - 2.2 ? 1.0 : 0.82, 255));
                }
            }
        }
        return d;
    }

    // ───────────── Petits dessins ─────────────

    /** Coche verte (en couleur, pas teintée). */
    static Dessin coche() {
        Dessin d = new Dessin(8, 7);
        String[] m = {
                "......oo",
                ".....ogo",
                "oo..oggo",
                "ogooggo.",
                "oggggo..",
                ".oggo...",
                "..oo....",
        };
        d.motif(0, 0, m, new char[]{'o', 'g'}, new int[]{argb(255, 0x0B2A0B), argb(255, 0x5FE35F)});
        return d;
    }

    /** Deux silhouettes (bonus duo). */
    static Dessin duo() {
        Dessin d = new Dessin(9, 7);
        String[] m = {
                ".##...##.",
                ".##...##.",
                ".........",
                "###...###",
                "####.####",
                "####.####",
                "####.####",
        };
        d.motif(0, 0, m, new char[]{'#'}, new int[]{gris(1, 255)});
        return d;
    }

    /** Petit shaker. */
    static Dessin shaker() {
        Dessin d = new Dessin(7, 9);
        String[] m = {
                "..ccc..",
                "..oco..",
                ".ooooo.",
                ".offfo.",
                ".offfo.",
                ".ofwfo.",
                ".offfo.",
                ".offfo.",
                ".ooooo.",
        };
        d.motif(0, 0, m, new char[]{'c', 'o', 'f', 'w'}, new int[]{gris(0.75, 255), gris(0.35, 255), gris(0.95, 255), gris(1, 255)});
        return d;
    }

    /** Icône du pack (liste des packs du jeu) : l'anneau orange à 70 %. */
    static BufferedImage icone() {
        Dessin d = new Dessin(64, 64);
        Dessin f = fond(32, 5, argb(230, 0x141018), true);
        Dessin a = arc(32, 5, 0.7);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int c = f.img.getRGB(x / 2, y / 2);
                int arcPx = a.img.getRGB(x / 2, y / 2);
                if ((arcPx >>> 24) != 0) {
                    int g = arcPx & 0xFF;
                    c = argb(255, ((0xFF * g / 255) << 16) | ((0xB3 * g / 255) << 8) | (0x3A * g / 255));
                }
                if ((c >>> 24) != 0) d.px(x, y, c);
            }
        }
        return d.img;
    }
}
