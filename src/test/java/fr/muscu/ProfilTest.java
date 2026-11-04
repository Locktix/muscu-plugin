package fr.muscu;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Sauvegarde du profil dans les donnees du joueur, conversion en points, montee de niveau, bonus. */
class ProfilTest {

    private ServerMock serveur;
    private PlayerMock joueur;

    @BeforeEach
    void demarrer() {
        serveur = MockBukkit.mock();
        joueur = serveur.addPlayer();
    }

    @AfterEach
    void arreter() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------ conversion en points

    @Test
    void convertir_garderLeResteEntreDeuxAppels() {
        Profil p = new Profil();
        assertEquals(2, p.convertir("sauts", 25, 10, 1));
        assertEquals(5, p.reste("sauts"));
        assertEquals(3, p.convertir("sauts", 5, 10, 3));
        assertEquals(0, p.reste("sauts"));
        assertEquals(0, p.convertir("sauts", 9, 10, 1));
        p.viderReste("sauts");
        assertEquals(0, p.reste("sauts"));
    }

    @Test
    void convertir_bonusDuoDe10PourcentSurDePetitsGains() {
        // Progression.gagner : gain += convertir("duo", gain * 10, 100, 1)
        Profil p = new Profil();
        int bonus = 0;
        for (int i = 0; i < 10; i++) bonus += p.convertir("duo", 3 * 10, 100, 1);
        assertEquals(3, bonus); // 10 gains de 3 pts = 30 pts -> 3 pts de bonus, rien de perdu
    }

    @Test
    void convertir_pasDeDebordement() {
        Profil p = new Profil();
        assertEquals(Integer.MAX_VALUE, p.convertir("x", Integer.MAX_VALUE, 1, 2));
    }

    // ------------------------------------------------------------ sauvegarde

    @Test
    void nouveauJoueur_commenceAuNiveau0AvecLeHud() {
        Profil p = Profil.charger(joueur);
        assertTrue(p.nouveau);
        assertEquals(0, p.niveau);
        assertEquals(0, p.pts);
        assertEquals(ModeTableau.HUD, p.tableau);
    }

    @Test
    void sauverPuisCharger_rienNeSePerd() {
        Profil p = Profil.charger(joueur);
        p.niveau = 7;
        p.pts = 33;
        p.tableau = ModeTableau.CLASSEMENT;
        p.vu = true;
        p.defisCle = "2026-11-04#0";
        p.defisProgres[2] = 40;
        p.defisBonus = true;
        p.statShakers = 3;
        p.statDefis = 12;
        p.statJournees = 2;
        p.statDuos = 1;
        p.convertir("sprint", 4321, 5000, 1);
        p.sauver(joueur);

        Profil q = Profil.charger(joueur);
        assertFalse(q.nouveau);
        assertEquals(7, q.niveau);
        assertEquals(33, q.pts);
        assertEquals(ModeTableau.CLASSEMENT, q.tableau);
        assertTrue(q.vu);
        assertEquals("2026-11-04#0", q.defisCle);
        assertArrayEquals(new int[]{0, 0, 40, 0, 0, 0, 0}, q.defisProgres);
        assertTrue(q.defisBonus);
        assertEquals(3, q.statShakers);
        assertEquals(12, q.statDefis);
        assertEquals(2, q.statJournees);
        assertEquals(1, q.statDuos);
        assertEquals(4321, q.reste("sprint"));
    }

    @Test
    void ancienneVersion_tableauPersoPasseAuHudUneSeuleFois() {
        var pdc = joueur.getPersistentDataContainer();
        pdc.set(new NamespacedKey("muscu", "niveau"), PersistentDataType.INTEGER, 4);
        pdc.set(new NamespacedKey("muscu", "tableau"), PersistentDataType.BOOLEAN, true);

        Profil p = Profil.charger(joueur);
        assertEquals(4, p.niveau);
        assertEquals(ModeTableau.HUD, p.tableau);

        // Le joueur choisit ensuite le tableau perso : son choix est garde
        p.tableau = ModeTableau.PERSO;
        p.sauver(joueur);
        assertEquals(ModeTableau.PERSO, Profil.charger(joueur).tableau);
    }

    @Test
    void ancienneVersion_tableauMasqueResteMasque() {
        joueur.getPersistentDataContainer().set(new NamespacedKey("muscu", "tableau"), PersistentDataType.BOOLEAN, false);
        assertEquals(ModeTableau.MASQUE, Profil.charger(joueur).tableau);
    }

    // ------------------------------------------------------------ montee de niveau

    @Test
    void monter_unOuPlusieursNiveauxEtGardeLeReste() {
        Reglages r = ReglagesTest.reglagesParDefaut();
        Profil p = new Profil();
        p.pts = 19;
        assertFalse(Progression.monter(p, r));
        assertEquals(0, p.niveau);

        p.pts = 25;
        assertTrue(Progression.monter(p, r));
        assertEquals(1, p.niveau);
        assertEquals(5, p.pts);

        p.pts = 40 + 60 + 7; // niv 1 -> 3 d'un coup
        assertTrue(Progression.monter(p, r));
        assertEquals(3, p.niveau);
        assertEquals(7, p.pts);
    }

    @Test
    void monter_bloqueAuNiveauMax() {
        Reglages r = ReglagesTest.reglagesParDefaut();
        Profil p = new Profil();
        p.pts = 20 * 55 + 12; // 20 + 40 + ... + 200 = 1100 pts pour le niveau 10
        assertTrue(Progression.monter(p, r));
        assertEquals(Paliers.MAX, p.niveau);
        assertEquals(12, p.pts);

        p.pts = 5000;
        assertFalse(Progression.monter(p, r));
        assertEquals(Paliers.MAX, p.niveau);
    }

    // ------------------------------------------------------------ bonus appliques au joueur

    @Test
    void bonus_remplacesEtJamaisCumules() {
        var vie = joueur.getAttribute(Attribute.MAX_HEALTH);
        assertNotNull(vie);
        double base = vie.getValue();

        Bonus.appliquer(joueur, 10);
        assertEquals(base + 6, vie.getValue(), 1e-9);
        Bonus.appliquer(joueur, 10); // reconnexion, reapparition...
        assertEquals(base + 6, vie.getValue(), 1e-9);
        Bonus.appliquer(joueur, 2); // admin : retour au niveau 2
        assertEquals(base + 2, vie.getValue(), 1e-9);
        Bonus.appliquer(joueur, 0);
        assertEquals(base, vie.getValue(), 1e-9);
        assertNull(vie.getModifier(Bonus.VIE.cle));
    }
}
