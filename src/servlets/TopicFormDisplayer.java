package servlets;

import graph.Topic;
import graph.TopicManagerSingleton;
import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TopicFormDisplayer implements Servlet {
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        List<Topic> topics = new ArrayList<>(TopicManagerSingleton.get().getTopics());
        topics.sort(Comparator.comparing(t -> t.name));

        StringBuilder html = new StringBuilder();
        html.append("<form action=\"http://localhost:8080/publishAll\" method=\"get\" target=\"right\" data-signature=\"")
                .append(escape(signature(topics))).append("\">");
        int index = 0;
        for (Topic topic : topics) {
            if (topic.publisherCount() > 0) {
                continue;
            }
            html.append("<div class=\"topic-row\"><strong title=\"").append(escape(topic.name)).append("\">")
                    .append(escape(topic.name)).append("</strong>")
                    .append("<input type=\"hidden\" name=\"topic").append(index).append("\" value=\"")
                    .append(escape(topic.name)).append("\">")
                    .append("<input name=\"message").append(index).append("\" type=\"text\" autocomplete=\"off\"></div>");
            index++;
        }
        html.append("<input type=\"hidden\" name=\"count\" value=\"").append(index).append("\">");
        if (index == 0) {
            html.append("<p class=\"hint\">No source topics yet. Upload a config or add a manual topic below.</p>");
        }
        html.append("<label for=\"customTopic\">Manual topic</label>");
        html.append("<input id=\"customTopic\" name=\"customTopic\" type=\"text\" autocomplete=\"off\">");
        html.append("<label for=\"customMessage\">Manual message</label>");
        html.append("<input id=\"customMessage\" name=\"customMessage\" type=\"text\" autocomplete=\"off\">");
        html.append("<button type=\"submit\">publish</button></form>");

        writeHtml(toClient, html.toString());
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String signature(List<Topic> topics) {
        StringBuilder result = new StringBuilder();
        for (Topic topic : topics) {
            if (topic.publisherCount() == 0) {
                if (result.length() > 0) {
                    result.append('|');
                }
                result.append(topic.name);
            }
        }
        return result.toString();
    }
}
