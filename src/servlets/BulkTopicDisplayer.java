package servlets;

import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class BulkTopicDisplayer implements Servlet {
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        Map<String, String> parameters = ri.getParameters();
        int count = parseCount(parameters.get("count"));
        StringBuilder notice = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String topic = parameters.get("topic" + i);
            String message = parameters.get("message" + i);
            if (topic != null && message != null && !message.trim().isEmpty()) {
                addNotice(notice, TopicDisplayer.publishAndWait(topic, message));
            }
        }

        String customTopic = parameters.get("customTopic");
        String customMessage = parameters.get("customMessage");
        if (customTopic != null && !customTopic.trim().isEmpty()) {
            addNotice(notice, TopicDisplayer.publishAndWait(customTopic, customMessage == null ? "" : customMessage));
        }

        TopicDisplayer.writeTopicTable(toClient, notice.toString());
    }

    @Override
    public void close() {
    }

    private static int parseCount(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void addNotice(StringBuilder notice, TopicDisplayer.PublishResult result) {
        if (result != null && !result.wasPublished() && !result.getMessage().isEmpty()) {
            if (notice.length() > 0) {
                notice.append(" ");
            }
            notice.append(result.getMessage());
        }
    }
}
