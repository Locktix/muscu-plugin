package fr.muscu;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** config.yml livré avec le plugin, calcul des niveaux, et config invalide. */
class ReglagesTest {

    private final List<String> avertissements = new ArrayList<>();
    private Logger log;

    @BeforeEach
    void demarrer() {
        MockBukkit.mock();
        log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { avertissements.add(r.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
    }

    @AfterEach
    void arreter() {
        MockBukkit.unmock();
    }

    static YamlConfiguration configLivree() {
        try (var lecteur = new InputStreamReader(
                Objects.requireNonNull(ReglagesTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(lecteur);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Reglages reglagesParDefaut() {
        return Reglages.depuis(configLivree(), Logger.getAnonymousLogger());
    }

    @Test
    void configLivree_seChargeSansAvertissement() {
        YamlConfiguration c = configLivree();
        Reglages r = Reglages.depuis(c, log);

        assertEquals(List.of(), avertissements);
        // Aucun defi ignore (type, mob ou groupe de minerais mal ecrit)
        assertEquals(c.getMapList("defis.perso").size(), r.defisPerso.size());
        assertEquals(c.getMapList("defis.duo").size(), r.defisDuo.size());
        assertTrue(r.defisPerso.size() >= r.defisParJour, "pas assez de defis pour en tirer " + r.defisParJour + " par jour");
        assertEquals(3, r.groupesMinage.size());
        assertEquals(4, r.viandes.size());
    }

    @Test
    void configLivree_valeursAttendues() {
        Reglages r = reglagesParDefaut();
        assertEquals(20, r.pointsParNiveau);
        assertFalse(r.gainsBossBar, "les gains vont dans l'action bar (la boss bar sert aux metiers de SurviePlus)");
        assertEquals("rare", r.groupeDe(Material.DEEPSLATE_DIAMOND_ORE).id());
        assertEquals("commun", r.groupeDe(Material.NETHER_GOLD_ORE).id());
        assertNull(r.groupeDe(Material.STONE));
        Reglages.Activite sprint = r.activites.stream().filter(a -> a.id().equals("sprint")).findFirst().orElseThrow();
        assertEquals(50 * 100, sprint.seuil()); // 50 blocs = 5000 cm
    }

    @Test
    void niveaux_besoinCasesEtTexte() {
        Reglages r = reglagesParDefaut();
        // (N + 1) x 20 : 20 pts pour le niv 1 ... 200 pour le niv 10
        assertEquals(20, r.besoin(0));
        assertEquals(200, r.besoin(9));
        assertEquals(0, r.cases(0, 0, 10));
        assertEquals(5, r.cases(0, 10, 10));
        assertEquals(10, r.cases(0, 999, 10));
        assertEquals(10, r.cases(Paliers.MAX, 0, 10));
        assertEquals("48/80", r.progression(3, 48));
        assertEquals("MAX", r.progression(Paliers.MAX, 12));
    }

    @Test
    void configInvalide_defisIgnoresAvecAvertissement() throws InvalidConfigurationException {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("""
                niveaux:
                  points-par-niveau: 0
                defis:
                  par-jour: 99
                  perso:
                    - { type: voler, quantite: 3 }
                    - { type: tuer, cible: dragon_rose, quantite: 3 }
                    - { type: minerais, cible: inexistant, quantite: 3 }
                    - { type: sauts, cible: zombie, quantite: "12", points: abc }
                    - { type: tuer, cible: "minecraft:Zombie", quantite: -4, points: -1 }
                """);

        Reglages r = Reglages.depuis(c, log);

        assertEquals(3, avertissements.size(), avertissements.toString());
        assertEquals(List.of(
                new Reglages.Defi(TypeDefi.SAUTS, null, 12, 5),      // cible inutile retiree, points par defaut
                new Reglages.Defi(TypeDefi.TUER, "zombie", 1, 0)),   // quantite et points ramenes dans les bornes
                r.defisPerso);
        assertEquals(1, r.pointsParNiveau);
        assertEquals(7, r.defisParJour);
        // Viandes par defaut si la liste est vide
        assertTrue(r.viandes.contains(Material.COOKED_BEEF));
    }
}
