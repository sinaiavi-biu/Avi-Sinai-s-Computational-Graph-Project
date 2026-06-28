package configs;

import graph.Agent;
import graph.Message;
import graph.Topic;
import graph.TopicManagerSingleton;

abstract class BooleanGateAgent implements Agent {
    private final String gateName;
    private final String[] subs;
    private final String[] pubs;
    private final Topic[] inputTopics;
    private final Topic[] outputTopics;
    private final Boolean[] values;

    BooleanGateAgent(String gateName, String[] subs, String[] pubs, int minInputs) {
        validate(gateName, subs, pubs, minInputs);
        this.gateName = gateName;
        this.subs = subs.clone();
        this.pubs = pubs.clone();
        this.inputTopics = new Topic[this.subs.length];
        this.outputTopics = new Topic[this.pubs.length];
        this.values = new Boolean[this.subs.length];

        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        for (int i = 0; i < this.subs.length; i++) {
            inputTopics[i] = manager.getTopic(this.subs[i]);
            inputTopics[i].subscribe(this);
        }
        for (int i = 0; i < this.pubs.length; i++) {
            outputTopics[i] = manager.getTopic(this.pubs[i]);
            outputTopics[i].addPublisher(this);
        }
    }

    @Override
    public String getName() {
        return gateName + "(" + String.join(",", subs) + ")=" + String.join(",", pubs);
    }

    @Override
    public void reset() {
        for (int i = 0; i < values.length; i++) {
            values[i] = Boolean.FALSE;
        }
    }

    @Override
    public void callback(String topic, Message msg) {
        int index = indexOf(subs, topic);
        if (index < 0) {
            return;
        }

        Boolean value = parseBoolean(msg);
        if (value == null) {
            values[index] = null;
            publish(new Message("invalid"));
            return;
        }

        values[index] = value;
        for (Boolean current : values) {
            if (current == null) {
                publish(new Message("invalid"));
                return;
            }
        }

        publish(new Message(evaluate(values) ? "1" : "0"));
    }

    private void publish(Message result) {
        for (Topic outputTopic : outputTopics) {
            outputTopic.publish(result);
        }
    }

    @Override
    public void close() {
        for (Topic inputTopic : inputTopics) {
            inputTopic.unsubscribe(this);
        }
        for (Topic outputTopic : outputTopics) {
            outputTopic.removePublisher(this);
        }
    }

    protected abstract boolean evaluate(Boolean[] values);

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private static Boolean parseBoolean(Message msg) {
        if (msg == null || msg.asText == null) {
            return null;
        }
        String text = msg.asText.trim().toLowerCase();
        if (text.equals("1") || text.equals("true") || text.equals("t") || text.equals("yes") || text.equals("on")) {
            return Boolean.TRUE;
        }
        if (text.equals("0") || text.equals("false") || text.equals("f") || text.equals("no") || text.equals("off")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static void validate(String gateName, String[] subs, String[] pubs, int minInputs) {
        if (isBlank(gateName) || subs == null || pubs == null || subs.length < minInputs || pubs.length < 1) {
            throw new IllegalArgumentException(gateName + " requires at least " + minInputs
                    + " input topic(s) and at least one output topic");
        }
        for (String sub : subs) {
            if (isBlank(sub)) {
                throw new IllegalArgumentException(gateName + " input topics cannot be blank");
            }
        }
        for (String pub : pubs) {
            if (isBlank(pub)) {
                throw new IllegalArgumentException(gateName + " output topics cannot be blank");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
