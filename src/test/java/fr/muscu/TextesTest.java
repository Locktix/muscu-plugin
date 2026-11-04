package fr.muscu;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Paliers, bonus, defis, modes d'affichage et petits textes (sans serveur). */
class TextesTest {

    @Test
    void paliers_chaqueNiveauDonneUnBonus() {
        for (int n = 0; n <= Paliers.MAX; n++) assertEquals(n, Paliers.get(n).niveau());
        assertEquals(0, Paliers.get(-3).niveau());
        assertEquals(Paliers.MAX, Paliers.get(99).niveau());
        // Le texte "Monstre de la salle : +3 coeurs" doit correspondre a ce qui est vraiment donne
        for (int n = 1; n <= Paliers.MAX; n++) {
            int niveau = n;
            assertTrue(Arrays.stream(Bonus.values()).anyMatch(b -> b.valeur(niveau) != b.valeur(niveau - 1)),
                    "le niveau " + n + " (" + Paliers.get(n).nom() + ") ne change aucun bonus");
        }
    }

    @Test
    void bonus_valeursProchainPalierEtTexte() {
        assertEquals(0, Bonus.VIE.valeur(-1));
        assertEquals(6, Bonus.VIE.valeur(99));
        assertEquals(2, Bonus.VIE.prochainPalier(0));
        assertEquals(10, Bonus.VIE.prochainPalier(6));
        assertEquals(-1, Bonus.VIE.prochainPalier(10));
        assertEquals(1, Bonus.MINAGE.prochainPalier(0));

        assertEquals("+5%", Bonus.MINAGE.format(.05));
        assertEquals("+6%", Bonus.VITESSE.format(.06));
        assertEquals("+1 cœur", Bonus.VIE.format(2));
        assertEquals("+3 cœurs", Bonus.VIE.format(6));
        assertEquals("+0,5", Bonus.FORCE.format(.5));
        assertEquals("+1", Bonus.FORCE.format(1));
        assertEquals("+1 bloc", Bonus.CHUTE.format(1));
    }

    @Test
    void typeDefi_phrasesEtLecture() {
        assertEquals("Saute 150 fois", TypeDefi.SAUTS.phrase(150, false));
        assertEquals("Sautez 150 fois", TypeDefi.SAUTS.phrase(150, true));
        assertEquals("Bois un shaker", TypeDefi.SHAKER.phrase(1, false));
        assertEquals("Bois 2 shaker(s)", TypeDefi.SHAKER.phrase(2, false));
        assertEquals("Buvez 1 shakers", TypeDefi.SHAKER.phrase(1, true));
        assertEquals(TypeDefi.SAUTS, TypeDefi.parse(" Sauts "));
        assertNull(TypeDefi.parse("voler"));
        assertNull(TypeDefi.parse(null));
        assertEquals(TypeDefi.NAGE, TypeDefi.depuisActivite("nage"));
        assertNull(TypeDefi.depuisActivite("mob-tue"));
        Set<String> ids = new HashSet<>();
        for (TypeDefi t : TypeDefi.values()) assertTrue(ids.add(t.id), "id en double : " + t.id);
    }

    @Test
    void modeTableau_faitLeTourEtLitLesAnciennesValeurs() {
        ModeTableau m = ModeTableau.HUD;
        for (int i = 0; i < ModeTableau.values().length; i++) m = m.suivant();
        assertEquals(ModeTableau.HUD, m);
        assertEquals(ModeTableau.PERSO, ModeTableau.HUD.suivant());
        assertEquals(ModeTableau.CLASSEMENT, ModeTableau.parse("CLASSEMENT", ModeTableau.HUD));
        assertEquals(ModeTableau.MASQUE, ModeTableau.parse(null, ModeTableau.MASQUE));
        assertEquals(ModeTableau.HUD, ModeTableau.parse("n'importe quoi", ModeTableau.HUD));
    }

    @Test
    void txt_pointsQuantitesDurees() {
        assertEquals("0 pt", Txt.pts(0));
        assertEquals("1 pt", Txt.pts(1));
        assertEquals("3 pts", Txt.pts(3));
        assertEquals("-3 pts", Txt.pts(-3));
        assertEquals("1 saut", Txt.nb(1, "saut", "sauts"));
        assertEquals("4 sauts", Txt.nb(4, "saut", "sauts"));
        assertEquals("0 s", Txt.duree(0));
        assertEquals("45 s", Txt.duree(45));
        assertEquals("1 min", Txt.duree(60));
        assertEquals("3 min 12 s", Txt.duree(192));
    }

    @Test
    void stats_milliersEtTemps() {
        assertEquals("999", Stats.format(Stats.Stat.SAUTS, 999));
        assertEquals("12 345 pts", Stats.format(Stats.Stat.POINTS, 12_345));
        assertEquals("1 234 567 blocs", Stats.format(Stats.Stat.SPRINT, 1_234_567));
        assertEquals("3 h 05 min", Stats.format(Stats.Stat.TEMPS, 3 * 3600 + 5 * 60 + 59));
        assertEquals("0 h 00 min", Stats.format(Stats.Stat.TEMPS, 0));
    }
}
