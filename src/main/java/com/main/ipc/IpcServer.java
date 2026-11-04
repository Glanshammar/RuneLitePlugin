package com.main.ipc;

import com.google.gson.Gson;
import com.main.data.ObjectDto;
import com.main.data.WcStatusDto;
import com.main.data.WidgetDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

@Slf4j
public class IpcServer {
    private final HttpServer server;
    private final Gson gson = new Gson();

    /** Volatile: written by the client thread, read by the HTTP thread. */
    private volatile String latestWcStatusJson = "{}";
    private volatile String latestObjectsJson = "[]";
    private volatile String latestWidgetsJson = "[]";

    public IpcServer(int port) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);

        server.createContext("/wc-status", exchange -> respond(exchange, latestWcStatusJson));
        server.createContext("/objects", exchange -> respond(exchange, latestObjectsJson));
        server.createContext("/widgets", exchange -> respond(exchange, latestWidgetsJson));

        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "apexea-ipc");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        log.info("IPC listening on http://127.0.0.1:{}/trees and /wc-status", port);
    }

    private void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }


    public void stop() {
        server.stop(0);
        log.info("IPC server stopped");
    }

    public void publishStatus(WcStatusDto status)     { latestWcStatusJson = gson.toJson(status); }
    public void publishObjects(List<ObjectDto> objects) { latestObjectsJson = gson.toJson(objects); }
    public void publishWidgets(List<WidgetDto> widgets) { latestWidgetsJson = gson.toJson(widgets); }
}