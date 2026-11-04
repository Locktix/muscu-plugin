package fr.muscu.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static fr.muscu.hud.Plan.*;

/**
 * Le resource pack du HUD, fabriqué en mémoire au démarrage :
 * <ul>
 *   <li>les images (cartes, badge, barres, cases, coche...) dessinées pixel par pixel ;</li>
 *   <li>la police {@code muscu:hud} : une image par caractère (zone privée Unicode), avec sa position verticale,
 *       plus des espaces positifs/négatifs pour placer chaque élément au pixel près ;</li>
 *   <li>des polices {@code muscu:t<y>} : la police normale du jeu, décalée vers le bas (lignes de texte).</li>
 * </ul>
 * Le zip est identique d'un démarrage à l'autre (même empreinte) : le client le garde en cache.
 */
public final class PackHud {

    public static final Key POLICE = Key.key("muscu", "hud");
    private static final int FORMAT = 97;

    /** Un caractère de la police HUD : son texte et de combien il fait avancer le curseur. */
    public record Glyphe(String c, int avance) {}

    private final Map<String, Glyphe> glyphes = new HashMap<>();
    private final JsonArray providers = new JsonArray();
    private final Map<String, byte[]> fichiers = new LinkedHashMap<>();
    private final Map<Integer, Key> policesTexte = new TreeMap<>();
    private final Map<String, int[]> icones;
    private final Largeurs largeurs;
    private final Set<String> manquantes = new HashSet<>();
    private int prochain = 0xE000;

    private byte[] zip;
    private String sha1;

    PackHud(Largeurs largeurs, Map<String, int[]> icones) {
        this.largeurs = largeurs;
        this.icones = icones;
    }

    // ───────────── Glyphes ─────────────

    /** Ajoute une image à la police HUD ; "haut" = position verticale de son bord haut à l'écran. */
    Glyphe image(String id, Dessin d, int haut) {
        String chemin = "hud/" + id.replaceAll("[^a-z0-9_]", "_") + ".png";
        if (!fichiers.containsKey("assets/muscu/textures/" + chemin)) fichiers.put("assets/muscu/textures/" + chemin, png(d.img));
        return enregistrer(id + "@" + haut, "muscu:" + chemin, d.h, haut, d.avance());
    }

    /**
     * Une texture du jeu (objet, effet...) utilisée telle quelle : rien n'est copié dans le pack,
     * le client la prend dans ses propres textures. null si on ne connaît pas sa taille.
     */
    Glyphe vanilla(String texture, int haut) {
        int[] t = icones.get(texture);
        if (t == null) {
            manquantes.add(texture);
            return null;
        }
        return enregistrer(texture + "@" + haut, "minecraft:" + texture + ".png", t[1], haut, t[2]);
    }

    public Glyphe get(String id, int haut) {
        return glyphes.get(id + "@" + haut);
    }

    public boolean connue(String texture) {
        return icones.containsKey(texture);
    }

    /** Haut d'une icône du jeu centrée dans l'anneau d'un défi. */
    public int hautIcone(String texture) {
        int[] t = icones.get(texture);
        int h = t == null ? 16 : t[1];
        return haut(DEFI_BAS - (DEFI - h) / 2);
    }

    /** Largeur affichée d'une icône du jeu. */
    public int largeurIcone(String texture) {
        int[] t = icones.get(texture);
        return t == null ? 16 : t[0];
    }

    private Glyphe enregistrer(String cle, String fichier, int hauteur, int haut, int avance) {
        Glyphe g = glyphes.get(cle);
        if (g != null) return g;
        String c = new String(Character.toChars(prochain++));
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", fichier);
        p.addProperty("ascent", Y_TEXTE + 7 - haut);
        p.addProperty("height", hauteur);
        JsonArray chars = new JsonArray();
        chars.add(c);
        p.add("chars", chars);
        providers.add(p);
        g = new Glyphe(c, avance);
        glyphes.put(cle, g);
        return g;
    }

    /** Police de texte normale dont le haut des lettres tombe à "haut" à l'écran. */
    public Key texte(int haut) {
        return policesTexte.computeIfAbsent(haut, h -> Key.key("muscu", "t" + h));
    }

    public Largeurs largeurs() {
        return largeurs;
    }

    public Set<String> manquantes() {
        return manquantes;
    }

    // ───────────── Espaces ─────────────

    /** Caractères d'espace : ±1, ±2, ±4 ... ±512 px. */
    static String espace(int px) {
        StringBuilder sb = new StringBuilder();
        int base = px < 0 ? 0xF800 : 0xF820;
        int reste = Math.abs(px);
        for (int bit = 9; bit >= 0; bit--) {
            while (reste >= (1 << bit)) {
                sb.appendCodePoint(base + bit);
                reste -= 1 << bit;
            }
        }
        return sb.toString();
    }

    private static JsonObject espaces() {
        JsonObject adv = new JsonObject();
        for (int bit = 0; bit <= 9; bit++) {
            adv.addProperty(new String(Character.toChars(0xF800 + bit)), -(1 << bit));
            adv.addProperty(new String(Character.toChars(0xF820 + bit)), 1 << bit);
        }
        JsonObject p = new JsonObject();
        p.addProperty("type", "space");
        p.add("advances", adv);
        return p;
    }

    // ───────────── Fabrication du zip ─────────────

    void finir(BufferedImage icone) throws IOException {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();

        JsonObject mcmeta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Muscu : HUD en images (fourni par le serveur)");
        pack.addProperty("min_format", FORMAT);
        pack.addProperty("max_format", 999);
        mcmeta.add("pack", pack);

        Map<String, byte[]> tout = new LinkedHashMap<>();
        tout.put("pack.mcmeta", gson.toJson(mcmeta).getBytes(StandardCharsets.UTF_8));
        tout.put("pack.png", png(icone));

        // Police HUD : les images + les espaces
        JsonArray liste = new JsonArray();
        liste.add(espaces());
        liste.addAll(providers);
        JsonObject hud = new JsonObject();
        hud.add("providers", liste);
        tout.put("assets/muscu/font/hud.json", gson.toJson(hud).getBytes(StandardCharsets.UTF_8));

        // Polices de texte décalées : copie de la police du jeu avec un "ascent" plus bas
        JsonObject defaut = lirePoliceDefaut();
        for (Map.Entry<Integer, Key> e : policesTexte.entrySet()) {
            int decalage = e.getKey() - Y_TEXTE;
            JsonArray ps = new JsonArray();
            JsonObject ref = new JsonObject();
            ref.addProperty("type", "reference");
            ref.addProperty("id", "minecraft:include/space");
            ps.add(ref);
            for (JsonElement el : defaut.getAsJsonArray("providers")) {
                JsonObject p = el.getAsJsonObject().deepCopy();
                p.addProperty("ascent", p.get("ascent").getAsInt() - decalage);
                ps.add(p);
            }
            JsonObject police = new JsonObject();
            police.add("providers", ps);
            tout.put("assets/muscu/font/" + e.getValue().value() + ".json", gson.toJson(police).getBytes(StandardCharsets.UTF_8));
        }

        tout.putAll(fichiers);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : tout.entrySet()) {
                ZipEntry z = new ZipEntry(e.getKey());
                z.setTime(315532800000L); // date fixe : même zip, même empreinte
                zos.putNextEntry(z);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        zip = bos.toByteArray();
        try {
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    public byte[] zip() {
        return zip;
    }

    public String sha1() {
        return sha1;
    }

    private static JsonObject lirePoliceDefaut() throws IOException {
        try (InputStream in = PackHud.class.getResourceAsStream("/hud/police_defaut.json")) {
            if (in == null) throw new IOException("hud/police_defaut.json introuvable dans le plugin");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
