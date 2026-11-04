package fr.muscu.hud;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Mini serveur web intégré qui sert le resource pack aux joueurs (le jeu ne sait le télécharger que par une URL). */
final class ServeurPack {

    private final HttpServer serveur;
    private final ExecutorService fils = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Muscu-pack-http");
        t.setDaemon(true);
        return t;
    });

    ServeurPack(int port, Supplier<PackHud> pack) throws IOException {
        serveur = HttpServer.create(new InetSocketAddress(port), 0);
        serveur.createContext("/", echange -> {
            try (echange) {
                PackHud p = pack.get();
                String chemin = echange.getRequestURI().getPath();
                if (p == null || !chemin.startsWith("/muscu-hud") || !chemin.endsWith(".zip")) {
                    echange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] zip = p.zip();
                echange.getResponseHeaders().set("Content-Type", "application/zip");
                echange.sendResponseHeaders(200, zip.length);
                try (OutputStream out = echange.getResponseBody()) {
                    out.write(zip);
                }
            }
        });
        serveur.setExecutor(fils);
        serveur.start();
    }

    void arreter() {
        serveur.stop(0);
        fils.shutdownNow();
    }
}
