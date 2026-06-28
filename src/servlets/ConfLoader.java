package servlets;

import configs.GenericConfig;
import graph.Graph;
import graph.TopicManagerSingleton;
import server.RequestParser.RequestInfo;
import views.HtmlGraphWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfLoader implements Servlet {
    private ConfigHolder current;
    private final Path uploadPath = Path.of("config_files", "uploaded.conf");

    @Override
    public synchronized void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        String previousContent = current == null ? null : current.content;
        String previousFileName = current == null ? null : current.fileName;
        boolean append = isAppendRequest(ri);
        boolean edit = isEditRequest(ri);
        boolean reset = isResetRequest(ri);
        try {
            if (reset) {
                closeCurrentConfig();
                TopicManagerSingleton.get().clear();
                writeHtml(toClient, currentGraphHtml());
                return;
            }
            if (edit) {
                handleEdit(ri, toClient, previousContent, previousFileName);
                return;
            }

            UploadedFile uploaded = extractUpload(ri);
            if (uploaded.content.trim().isEmpty()) {
                writeHtml(toClient, currentGraphHtml(), "Uploaded config is empty.");
                return;
            }
            String content = append && previousContent != null
                    ? previousContent.trim() + "\n" + uploaded.content.trim()
                    : uploaded.content;
            deploy(content, append && previousFileName != null ? previousFileName : uploaded.fileName, !append);
            writeHtml(toClient, currentGraphHtml());
        } catch (Exception e) {
            if (append && previousContent != null) {
                try {
                    deploy(previousContent, previousFileName, false);
                } catch (Exception restoreError) {
                    closeCurrentConfig();
                    TopicManagerSingleton.get().clear();
                }
            } else {
                closeCurrentConfig();
                TopicManagerSingleton.get().clear();
            }
            writeHtml(toClient, currentGraphHtml(), e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        closeCurrentConfig();
    }

    private void closeCurrentConfig() {
        if (current != null) {
            current.config.close();
            current = null;
        }
    }

    private void deploy(String content, String fileName, boolean clearTopics) throws IOException {
        closeCurrentConfig();
        if (clearTopics) {
            TopicManagerSingleton.get().clear();
        }
        Files.createDirectories(uploadPath.getParent());
        Files.writeString(uploadPath, content, StandardCharsets.UTF_8);

        GenericConfig config = new GenericConfig();
        config.setConfFile(uploadPath.toString());
        config.create();
        current = new ConfigHolder(config, fileName, content);
    }

    private void handleEdit(RequestInfo ri, OutputStream toClient, String previousContent, String previousFileName)
            throws IOException {
        if (previousContent == null) {
            writeHtml(toClient, currentGraphHtml(), "Load a config before editing it.");
            return;
        }

        Map<String, String> form = formValues(new String(ri.getContent(), StandardCharsets.UTF_8));
        String action = form.get("action");
        String edited = previousContent;
        try {
            if ("removeTopic".equals(action)) {
                String topic = form.get("topic");
                edited = removeTopicFromConfig(previousContent, topic);
                deploy(edited, previousFileName, false);
                TopicManagerSingleton.get().removeTopic(topic == null ? "" : topic.trim());
            } else if ("addEdge".equals(action)) {
                edited = addEdgeToConfig(previousContent, form.get("from"), form.get("to"));
                deploy(edited, previousFileName, false);
            } else if ("removeAgent".equals(action)) {
                edited = removeAgentFromConfig(previousContent, form.get("agent"));
                deploy(edited, previousFileName, false);
            } else if ("removeEdge".equals(action)) {
                edited = removeEdgeFromConfig(previousContent, form.get("from"), form.get("to"));
                deploy(edited, previousFileName, false);
            } else {
                writeHtml(toClient, currentGraphHtml(), "Unknown edit action.");
                return;
            }
            writeHtml(toClient, currentGraphHtml());
        } catch (Exception e) {
            try {
                deploy(previousContent, previousFileName, false);
            } catch (Exception restoreError) {
                closeCurrentConfig();
            }
            writeHtml(toClient, currentGraphHtml(), e.getMessage());
        }
    }

    private static String currentGraphHtml() {
        Graph graph = new Graph();
        graph.createFromTopics();
        return String.join("\n", HtmlGraphWriter.getGraphHTML(graph));
    }

    private static boolean isAppendRequest(RequestInfo ri) {
        return ri.getUri() != null && ri.getUri().startsWith("/append-config");
    }

    private static boolean isEditRequest(RequestInfo ri) {
        return ri.getUri() != null && ri.getUri().startsWith("/edit-config");
    }

    private static boolean isResetRequest(RequestInfo ri) {
        return ri.getUri() != null && ri.getUri().startsWith("/reset-config");
    }

    private static UploadedFile extractUpload(RequestInfo ri) {
        String body = new String(ri.getContent(), StandardCharsets.UTF_8);
        if (body.trim().isEmpty()) {
            return new UploadedFile("uploaded.conf", "");
        }
        if (body.startsWith("appendConfText=") || body.contains("&appendConfText=")) {
            return new UploadedFile("append.conf", formValue(body, "appendConfText"));
        }
        if (body.startsWith("confText=") || body.contains("&confText=")) {
            return new UploadedFile("inline.conf", formValue(body, "confText"));
        }
        if (!body.startsWith("--")) {
            return new UploadedFile("uploaded.conf", body);
        }

        String firstLine = firstLine(body);
        String[] parts = body.split(java.util.regex.Pattern.quote(firstLine));
        for (String part : parts) {
            if (!part.contains("Content-Disposition")) {
                continue;
            }
            String fileName = between(part, "filename=\"", "\"");
            int contentStart = part.indexOf("\r\n\r\n");
            int separatorLength = 4;
            if (contentStart < 0) {
                contentStart = part.indexOf("\n\n");
                separatorLength = 2;
            }
            if (contentStart >= 0) {
                String content = part.substring(contentStart + separatorLength);
                content = trimMultipartEnding(content);
                return new UploadedFile(fileName.isEmpty() ? "uploaded.conf" : fileName, content);
            }
        }
        return new UploadedFile("uploaded.conf", body);
    }

    private static String formValue(String body, String key) {
        return formValues(body).getOrDefault(key, "");
    }

    private static Map<String, String> formValues(String body) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            String rawKey = equals >= 0 ? pair.substring(0, equals) : pair;
            String rawValue = equals >= 0 ? pair.substring(equals + 1) : "";
            result.put(URLDecoder.decode(rawKey, StandardCharsets.UTF_8),
                    URLDecoder.decode(rawValue, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String firstLine(String text) {
        int end = text.indexOf("\r\n");
        if (end < 0) {
            end = text.indexOf('\n');
        }
        return end < 0 ? text : text.substring(0, end);
    }

    private static String between(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        int valueStart = startIndex + start.length();
        int endIndex = text.indexOf(end, valueStart);
        return endIndex < 0 ? "" : text.substring(valueStart, endIndex);
    }

    private static String trimMultipartEnding(String content) {
        String result = content;
        while (result.endsWith("\r\n") || result.endsWith("\n")) {
            result = result.substring(0, result.length() - (result.endsWith("\r\n") ? 2 : 1));
        }
        if (result.endsWith("--")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }

    private static void writeHtml(OutputStream out, String body) throws IOException {
        writeHtml(out, body, "");
    }

    private static void writeHtml(OutputStream out, String body, String notice) throws IOException {
        String refreshedBody = injectRefreshScript(body, notice);
        byte[] bytes = refreshedBody.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private static String injectRefreshScript(String body, String notice) {
        String rightUrl = "/publish?quiet=1";
        if (notice != null && !notice.trim().isEmpty()) {
            rightUrl += "&notice=" + URLEncoder.encode(notice, StandardCharsets.UTF_8);
        }
        String script = "<script>"
                + "if(parent&&parent.frames&&parent.frames['right']){parent.frames['right'].location='" + rightUrl + "';}"
                + "if(parent&&parent.frames&&parent.frames['left']){parent.frames['left'].postMessage('refresh-topic-form','*');}"
                + "</script>";
        int bodyIndex = body.indexOf("<body>");
        if (bodyIndex >= 0) {
            return body.substring(0, bodyIndex + 6) + script + body.substring(bodyIndex + 6);
        }
        return script + body;
    }

    private static String removeTopicFromConfig(String content, String topic) {
        String cleanTopic = cleanRequired(topic, "Topic");
        List<Block> blocks = blocks(content);
        List<Block> kept = new ArrayList<>();
        for (Block block : blocks) {
            block.subs = removeName(block.subs, cleanTopic);
            block.pubs = removeName(block.pubs, cleanTopic);
            if (block.subs.length > 0 && block.pubs.length > 0) {
                kept.add(block);
            }
        }
        return blocksToText(kept);
    }

    private static String addEdgeToConfig(String content, String from, String to) {
        String cleanFrom = cleanRequired(from, "From");
        String cleanTo = cleanRequired(to, "To");
        List<Block> result = new ArrayList<>();
        boolean added = false;

        for (Block block : blocks(content)) {
            String agentName = agentName(block);
            // An edge into an agent means adding a subscriber topic to that
            // agent block. Only variable-input agents can accept new inputs.
            if (cleanTo.equals(agentName)) {
                if (!allowsAdditionalInput(block.className)) {
                    throw new IllegalArgumentException("Agent does not support extra input edges: " + cleanTo);
                }
                if (containsName(block.subs, cleanFrom)) {
                    throw new IllegalArgumentException("Edge already exists: " + cleanFrom + " -> " + cleanTo);
                }
                block.subs = appendName(block.subs, cleanFrom);
                added = true;
            }
            // An edge out of an agent means adding a publisher topic to the
            // block. GenericConfig later validates that the topic has one owner.
            if (cleanFrom.equals(agentName)) {
                if (containsName(block.pubs, cleanTo)) {
                    throw new IllegalArgumentException("Edge already exists: " + cleanFrom + " -> " + cleanTo);
                }
                block.pubs = appendName(block.pubs, cleanTo);
                added = true;
            }
            result.add(block);
        }

        if (!added) {
            throw new IllegalArgumentException("Edge endpoint must include one existing agent: " + cleanFrom + " -> "
                    + cleanTo);
        }
        return blocksToText(result);
    }

    private static boolean allowsAdditionalInput(String className) {
        String simpleName = simpleClassName(className);
        return simpleName.equals("PlusAgent")
                || simpleName.equals("SubAgent")
                || simpleName.equals("MulAgent")
                || simpleName.equals("DivAgent")
                || simpleName.equals("ModAgent")
                || simpleName.equals("PowAgent")
                || simpleName.equals("MinAgent")
                || simpleName.equals("MaxAgent")
                || simpleName.equals("AvgAgent")
                || simpleName.equals("AndAgent")
                || simpleName.equals("OrAgent")
                || simpleName.equals("XorAgent")
                || simpleName.equals("NandAgent")
                || simpleName.equals("NorAgent")
                || simpleName.equals("XnorAgent")
                || simpleName.equals("MajorityAgent");
    }

    private static String removeAgentFromConfig(String content, String agent) {
        String cleanAgent = cleanRequired(agent, "Agent");
        List<Block> kept = new ArrayList<>();
        boolean removed = false;
        for (Block block : blocks(content)) {
            if (agentName(block).equals(cleanAgent)) {
                removed = true;
            } else {
                kept.add(block);
            }
        }
        if (!removed) {
            throw new IllegalArgumentException("Agent not found: " + cleanAgent);
        }
        return blocksToText(kept);
    }

    private static String removeEdgeFromConfig(String content, String from, String to) {
        String cleanFrom = cleanRequired(from, "From");
        String cleanTo = cleanRequired(to, "To");
        List<Block> result = new ArrayList<>();
        boolean removed = false;
        for (Block block : blocks(content)) {
            String agentName = agentName(block);
            if (cleanTo.equals(agentName)) {
                String[] updated = removeName(block.subs, cleanFrom);
                removed = removed || updated.length != block.subs.length;
                block.subs = updated;
            }
            if (cleanFrom.equals(agentName)) {
                String[] updated = removeName(block.pubs, cleanTo);
                removed = removed || updated.length != block.pubs.length;
                block.pubs = updated;
            }
            if (block.subs.length > 0 && block.pubs.length > 0) {
                result.add(block);
            }
        }
        if (!removed) {
            throw new IllegalArgumentException("Edge not found: " + cleanFrom + " -> " + cleanTo);
        }
        return blocksToText(result);
    }

    private static List<Block> blocks(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        if (lines.size() % 3 != 0) {
            throw new IllegalArgumentException("Current config is not made of 3-line agent blocks.");
        }
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 3) {
            blocks.add(new Block(lines.get(i), splitCsv(lines.get(i + 1)), splitCsv(lines.get(i + 2))));
        }
        return blocks;
    }

    private static String blocksToText(List<Block> blocks) {
        StringBuilder result = new StringBuilder();
        for (Block block : blocks) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(block.className).append('\n')
                    .append(String.join(",", block.subs)).append('\n')
                    .append(String.join(",", block.pubs)).append('\n');
        }
        return result.toString();
    }

    private static String agentName(Block block) {
        if (block.className.endsWith(".PlusAgent") || block.className.equals("PlusAgent")) {
            return block.subs.length >= 2 ? String.join("+", block.subs) + "=" + String.join(",", block.pubs)
                    : block.className;
        }
        if (block.className.endsWith(".IncAgent") || block.className.equals("IncAgent")) {
            return block.subs.length >= 1 ? block.subs[0] + "+1=" + String.join(",", block.pubs) : block.className;
        }
        String simpleName = simpleClassName(block.className);
        if (simpleName.endsWith("Agent")) {
            String op = simpleName.substring(0, simpleName.length() - "Agent".length()).toUpperCase();
            return op + "(" + String.join(",", block.subs) + ")=" + String.join(",", block.pubs);
        }
        return block.className + "(" + String.join(",", block.subs) + "->" + String.join(",", block.pubs) + ")";
    }

    private static String simpleClassName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static String[] splitCsv(String value) {
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }

    private static String[] removeName(String[] values, String name) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!value.equals(name)) {
                result.add(value);
            }
        }
        return result.toArray(new String[0]);
    }

    private static String[] appendName(String[] values, String name) {
        String[] result = java.util.Arrays.copyOf(values, values.length + 1);
        result[values.length] = name;
        return result;
    }

    private static boolean containsName(String[] values, String name) {
        for (String value : values) {
            if (value.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String cleanRequired(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
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

    private static class UploadedFile {
        private final String fileName;
        private final String content;

        private UploadedFile(String fileName, String content) {
            this.fileName = fileName;
            this.content = content;
        }
    }

    private static class ConfigHolder {
        private final GenericConfig config;
        private final String fileName;
        private final String content;

        private ConfigHolder(GenericConfig config, String fileName, String content) {
            this.config = config;
            this.fileName = fileName;
            this.content = content;
        }
    }

    private static class Block {
        private final String className;
        private String[] subs;
        private String[] pubs;

        private Block(String className, String[] subs, String[] pubs) {
            this.className = className;
            this.subs = subs;
            this.pubs = pubs;
        }
    }
}
