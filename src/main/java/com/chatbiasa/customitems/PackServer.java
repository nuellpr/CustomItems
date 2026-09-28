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
        if (host == null || host.isBlank() || host.equals("0.0.0.0") || host.equals("::")) {
            String ip = Bukkit.getIp();
            // 0.0.0.0 is a bind address, not something a client can download from — never hand it out.
            if (ip == null || ip.isBlank() || ip.equals("0.0.0.0") || ip.equals("::")) {
                ip = "127.0.0.1";
                plugin.getLogger().warning("pack-format/external-url not set: pack URL points at 127.0.0.1, "
                        + "which only works for players on the same machine. Set external-url in config.yml.");
            }
            host = ip;
        }
        host = host.replaceAll("/+$", "");
        String url = (host.matches("^https?://.*") ? host : "http://" + host + ":" + port)
                + (host.endsWith(".zip") ? "" : "/pack.zip");
        plugin.getLogger().info("Pack server on " + url);
        return url;
    }

    public void stop() {
        if (server != null) server.stop(0);
    }
}
