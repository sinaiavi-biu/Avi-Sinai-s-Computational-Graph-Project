package servlets;

import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HtmlLoader implements Servlet {
    private final Path htmlDirectory;

    public HtmlLoader(String htmlDirectory) {
        this.htmlDirectory = Path.of(htmlDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        String relativePath = requestedFile(ri);
        Path file = htmlDirectory.resolve(relativePath).normalize();
        if (!file.startsWith(htmlDirectory) || !Files.isRegularFile(file)) {
            writeHtml(toClient, "<!DOCTYPE html><html><body><h1>File not found</h1></body></html>");
            return;
        }
        writeHtml(toClient, Files.readString(file));
    }

    @Override
    public void close() {
    }

    private static String requestedFile(RequestInfo ri) {
        String[] parts = ri.getUriParts();
        if (parts.length <= 1) {
            return "index.html";
        }
        StringBuilder path = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(parts[i]);
        }
        return path.toString();
    }

    private static void writeHtml(OutputStream out, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }
}
