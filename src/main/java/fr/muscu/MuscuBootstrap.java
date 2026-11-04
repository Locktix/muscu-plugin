package fr.muscu;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

import java.net.URL;

/**
 * Charge le mini datapack inclus dans le plugin (dossier muscu_pack) : l'onglet de succès Muscu
 * et le bouton "Muscu" de la touche G / du menu Échap. Ce sont les seules choses qu'un plugin
 * ne peut pas créer lui-même.
 */
public final class MuscuBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
            try {
                URL pack = getClass().getResource("/muscu_pack");
                if (pack == null) {
                    context.getLogger().error("Dossier muscu_pack introuvable dans le jar : pas de succès ni de bouton G.");
                    return;
                }
                event.registrar().discoverPack(pack.toURI(), "muscu",
                        c -> c.title(Component.text("Muscu (plugin)")).autoEnableOnServerStart(true));
            } catch (Exception e) {
                context.getLogger().error("Impossible de charger le datapack intégré de Muscu", e);
            }
        }));
    }
}
