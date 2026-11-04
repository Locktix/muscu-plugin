package fr.muscu;

/** Ce qu'affiche le tableau (HUD en haut ou tableau sur le côté). Le bouton du menu passe de l'un à l'autre dans cet ordre. */
public enum ModeTableau {

    HUD("hud", "HUD", "Anneaux en images autour de la hotbar"),
    PERSO("perso", "Perso", "Ta progression, tes défis, ton shaker (sur le côté)"),
    CLASSEMENT("classement", "Classement", "Les niveaux de tout le monde"),
    MASQUE("masque", "Masqué", "Rien sur le côté");

    public final String id;
    public final String nom;
    public final String description;

    ModeTableau(String id, String nom, String description) {
        this.id = id;
        this.nom = nom;
        this.description = description;
    }

    public ModeTableau suivant() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static ModeTableau parse(String id, ModeTableau defaut) {
        for (ModeTableau m : values()) {
            if (m.id.equalsIgnoreCase(id)) return m;
        }
        return defaut;
    }
}
