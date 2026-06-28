package server;

import servlets.Servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MyHTTPServer extends Thread implements HTTPServer {
    private final int port;
    private final ExecutorService pool;
    private final Map<String, Servlet> getServlets = new ConcurrentHashMap<>();
    private final Map<String, Servlet> postServlets = new ConcurrentHashMap<>();
    private final Map<String, Servlet> deleteServlets = new ConcurrentHashMap<>();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public MyHTTPServer(int port, int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException("nThreads must be positive");
        }
        this.port = port;
        this.pool = Executors.newFixedThreadPool(nThreads);
        setName("MyHTTPServer-main-" + port);
    }

    @Override
    public void addServlet(String httpCommand, String uri, Servlet s) {
        if (s == null) {
            throw new IllegalArgumentException("Servlet cannot be null");
        }
        servletMap(httpCommand).put(normalizeUri(uri), s);
    }

    @Override
    public void removeServlet(String httpCommand, String uri) {
        servletMap(httpCommand).remove(normalizeUri(uri));
    }

    @Override
    public synchronized void start() {
        running = true;
        super.start();
    }

    @Override
    public void run() {
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            socket.setSoTimeout(1000);
            while (running) {
                try {
                    Socket client = socket.accept();
                    pool.submit(() -> handleClient(client));
                } catch (java.net.SocketTimeoutException e) {
                    // Periodically wake up to observe the running flag.
                } catch (SocketException e) {
                    if (running) {
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                throw new RuntimeException("HTTP server failed", e);
            }
        } finally {
            running = false;
        }
    }

    @Override
    public void close() {
        running = false;
        closeServerSocket();
        pool.shutdown();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeServlets();
    }

    private void handleClient(Socket client) {
        try (Socket socket = client;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            RequestParser.RequestInfo requestInfo = RequestParser.parseRequest(reader);
            Servlet servlet = findServlet(requestInfo.getHttpCommand(), requestInfo.getUri());
            if (servlet != null) {
                servlet.handle(requestInfo, socket.getOutputStream());
            } else {
                writeNotFound(socket);
            }
        } catch (IOException e) {
            // Client requests are isolated; a bad client must not stop the server.
        }
    }

    private Servlet findServlet(String httpCommand, String uri) {
        Map<String, Servlet> map = servletMap(httpCommand);
        String path = uriPath(uri);
        String bestPrefix = null;
        // Prefix routing lets one static servlet own /app/ while exact-looking routes
        // such as /graph and /publish still stay simple.
        for (String prefix : map.keySet()) {
            if (path.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
            }
        }
        return bestPrefix == null ? null : map.get(bestPrefix);
    }

    private void writeNotFound(Socket socket) throws IOException {
        String body = "No servlet";
        String response = "HTTP/1.1 404 Not Found\r\nContent-Length: " + body.length()
                + "\r\nConnection: close\r\n\r\n" + body;
        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Servlet> servletMap(String command) {
        String normalized = command == null ? "" : command.toUpperCase();
        switch (normalized) {
            case "GET":
                return getServlets;
            case "POST":
                return postServlets;
            case "DELETE":
                return deleteServlets;
            default:
                throw new IllegalArgumentException("Unsupported HTTP command: " + command);
        }
    }

    private static String normalizeUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    private static String uriPath(String uri) {
        String normalized = normalizeUri(uri);
        int queryIndex = normalized.indexOf('?');
        return queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Closing is best effort.
            }
        }
    }

    private void closeServlets() {
        Set<Servlet> allServlets = new HashSet<>();
        allServlets.addAll(getServlets.values());
        allServlets.addAll(postServlets.values());
        allServlets.addAll(deleteServlets.values());
        for (Servlet servlet : allServlets) {
            try {
                servlet.close();
            } catch (IOException e) {
                // Continue closing the rest.
            }
        }
    }
}
