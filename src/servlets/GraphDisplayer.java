package servlets;

import graph.Graph;
import server.RequestParser.RequestInfo;
import views.HtmlGraphWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class GraphDisplayer implements Servlet {
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        Graph graph = new Graph();
        graph.createFromTopics();
        writeHtml(toClient, String.join("\n", HtmlGraphWriter.getGraphHTML(graph)));
    }

    @Override
    public void close() {
    }

    private static void writeHtml(OutputStream out, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }
}
