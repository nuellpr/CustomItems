package com.chatbiasa.customitems;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PackServer {

    private final CustomItemsPlugin plugin;
    private HttpServer server;
    private ExecutorService executor;
    private int boundPort = -1;

    public PackServer(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reuses the listener when only external-url changes; binds a replacement before stopping the old port. */
    public synchronized String start() throws IOException {
        int port = plugin.getConfig().getInt("port", 8077);
        if (port < 1 || port > 65535) throw new IOException("Invalid pack server port: " + port);

        if (server == null || boundPort != port) {
            HttpServer replacement = HttpServer.create(new InetSocketAddress(port), 0);
            ExecutorService replacementExecutor = Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "CustomItems-PackServer");
                thread.setDaemon(true);
                return thread;
            });
            replacement.createContext("/pack.zip", this::servePack);
            replacement.setExecutor(replacementExecutor);
            try {
                replacement.start();
            } catch (RuntimeException e) {
                replacement.stop(0);
                replacementExecutor.shutdownNow();
                throw e;
            }

            HttpServer previous = server;
            ExecutorService previousExecutor = executor;
            server = replacement;
            executor = replacementExecutor;
            boundPort = port;
            if (previous != null) previous.stop(0);
            if (previousExecutor != null) previousExecutor.shutdownNow();
        }

        String url = buildUrl(port);
        plugin.getLogger().info("Pack server on " + url);
        return url;
    }

    private void servePack(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            File pack = plugin.pack().packFile;
            if (pack == null || !pack.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            try (FileChannel file = FileChannel.open(pack.toPath(), StandardOpenOption.READ)) {
                long length = file.size();
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(200, length);
                try (InputStream input = Channels.newInputStream(file);
                     OutputStream output = exchange.getResponseBody()) {
                    input.transferTo(output);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private String buildUrl(int port) {
        String host = plugin.getConfig().getString("external-url", "");
        if (host == null || host.isBlank() || host.equals("0.0.0.0") || host.equals("::")) {
            String ip = Bukkit.getIp();
            if (ip == null || ip.isBlank() || ip.equals("0.0.0.0") || ip.equals("::")) {
                ip = "127.0.0.1";
                plugin.getLogger().warning("external-url not set: pack URL points at 127.0.0.1, "
                        + "which only works for players on the same machine. Set external-url in config.yml.");
            }
            host = ip;
        }
        host = host.replaceAll("/+$", "");
        return (host.matches("^https?://.*") ? host : "http://" + host + ":" + port)
                + (host.endsWith(".zip") ? "" : "/pack.zip");
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        boundPort = -1;
    }
}
