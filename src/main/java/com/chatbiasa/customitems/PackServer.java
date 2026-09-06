package com.chatbiasa.customitems;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public final class PackServer {

    private final CustomItemsPlugin plugin;
    private HttpServer server;

    public PackServer(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public String start() throws IOException {
        int port = plugin.getConfig().getInt("port", 8077);
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/pack.zip", exchange -> {
            File pack = plugin.pack().packFile;
            byte[] body = pack != null && pack.isFile() ? Files.readAllBytes(pack.toPath()) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(body.length > 0 ? 200 : 404, body.length > 0 ? body.length : -1);
            if (body.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
        String host = plugin.getConfig().getString("external-url", "");
        if (host == null || host.isBlank()) {
            host = Bukkit.getIp().isBlank() ? "127.0.0.1" : Bukkit.getIp();
        }
        String url = host.matches("^https?://.*") ? host : "http://" + host + ":" + port;
        plugin.getLogger().info("Pack server on " + url + "/pack.zip");
        return url;
    }

    public void stop() {
        if (server != null) server.stop(0);
    }
}
