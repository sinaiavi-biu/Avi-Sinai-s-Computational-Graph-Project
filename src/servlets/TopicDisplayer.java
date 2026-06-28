package servlets;

import graph.Message;
import graph.Topic;
import graph.TopicManagerSingleton;
import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TopicDisplayer implements Servlet {
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        String topic = ri.getParameters().get("topic");
        String message = ri.getParameters().get("message");
        boolean quiet = "1".equals(ri.getParameters().get("quiet"));
        String notice = ri.getParameters().get("notice");

        PublishResult result = publishAndWait(topic, message == null ? "" : message);
        String messageNotice = result.message;
        if (notice != null && !notice.trim().isEmpty()) {
            messageNotice = messageNotice == null || messageNotice.isEmpty() ? notice : messageNotice + " " + notice;
        }

        writeTopicTable(toClient, messageNotice, !quiet);
    }

    @Override
    public void close() {
    }

    static PublishResult publishAndWait(String topic, String message) {
        if (topic == null || topic.trim().isEmpty()) {
            return PublishResult.ignored();
        }

        String cleanTopic = topic.trim();
        Topic existing = findTopic(cleanTopic);
        if (existing != null && existing.publisherCount() > 0) {
            return PublishResult.rejected("Topic " + cleanTopic + " is computed by the config and cannot be manually published.");
        }

        TopicManagerSingleton.get().getTopic(cleanTopic).publish(new Message(message == null ? "" : message));
        waitForParallelAgents();
        return PublishResult.published();
    }

    static void writeTopicTable(OutputStream toClient) throws IOException {
        writeTopicTable(toClient, "");
    }

    static void writeTopicTable(OutputStream toClient, String notice) throws IOException {
        writeTopicTable(toClient, notice, true);
    }

    static void writeTopicTable(OutputStream toClient, String notice, boolean refreshGraph) throws IOException {
        writeHtml(toClient, tableHtml(notice, refreshGraph));
    }

    private static String tableHtml(String notice, boolean refreshGraph) {
        List<Topic> topics = new ArrayList<>(TopicManagerSingleton.get().getTopics());
        topics.sort(Comparator.comparing(t -> t.name));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Topics</title>");
        html.append("<style>");
        html.append("body{font-family:\"Segoe UI\",Arial,sans-serif;margin:0;padding:20px;color:#172033;background:linear-gradient(135deg,rgba(239,246,255,.95),rgba(238,247,246,.86) 50%,rgba(255,247,237,.9)),linear-gradient(90deg,rgba(23,32,51,.04) 1px,transparent 1px),linear-gradient(0deg,rgba(23,32,51,.04) 1px,transparent 1px);background-size:auto,36px 36px,36px 36px}");
        html.append("h1{margin:0 0 18px;font-size:30px;letter-spacing:0}");
        html.append(".meta{color:#68758a;font-size:13px;margin-bottom:14px}");
        html.append("table{width:100%;border-collapse:separate;border-spacing:0;border:1px solid #cfd6e3;background:rgba(255,255,255,.82)}");
        html.append("th,td{border-bottom:1px solid #e3e8f0;padding:12px 14px;text-align:left;font-size:16px}");
        html.append("tr:last-child td{border-bottom:0}");
        html.append("th{background:#eef7f6;font-size:15px;text-transform:uppercase;letter-spacing:.04em;color:#334155}");
        html.append("td:first-child{font-weight:800;width:38%}");
        html.append(".empty{color:#94a3b8}");
        html.append(".notice{margin:0 0 14px;padding:10px 12px;background:#fff7d6;border:1px solid #f2c94c;color:#6b4e00;font-weight:700}");
        html.append("</style></head><body>");
        html.append("<script>");
        if (refreshGraph) {
            html.append("if(parent&&parent.frames&&parent.frames['center']){parent.frames['center'].location='/graph';}");
        }
        html.append("if(parent&&parent.frames&&parent.frames['left']){parent.frames['left'].postMessage('refresh-topic-form','*');}");
        html.append("</script>");
        html.append("<h1>Topic Values</h1><div class=\"meta\">Latest messages after the last publish request</div>");
        if (notice != null && !notice.isEmpty()) {
            html.append("<div class=\"notice\">").append(escape(notice)).append("</div>");
        }
        html.append("<table><tr><th>Topic name</th><th>Last value/message</th></tr>");
        for (Topic topic : topics) {
            Message last = topic.getLastMessage();
            html.append("<tr><td>").append(escape(topic.name)).append("</td><td>")
                    .append(last == null ? "<span class=\"empty\">empty</span>" : escape(last.asText)).append("</td></tr>");
        }
        html.append("</table></body></html>");
        return html.toString();
    }

    private static Topic findTopic(String name) {
        for (Topic topic : TopicManagerSingleton.get().getTopics()) {
            if (topic.name.equals(name)) {
                return topic;
            }
        }
        return null;
    }

    private static void waitForParallelAgents() {
        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    static class PublishResult {
        private final boolean published;
        private final String message;

        private PublishResult(boolean published, String message) {
            this.published = published;
            this.message = message;
        }

        private static PublishResult published() {
            return new PublishResult(true, "");
        }

        private static PublishResult ignored() {
            return new PublishResult(false, "");
        }

        private static PublishResult rejected(String message) {
            return new PublishResult(false, message);
        }

        boolean wasPublished() {
            return published;
        }

        String getMessage() {
            return message;
        }
    }
}
