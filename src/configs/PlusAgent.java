package configs;

import graph.Agent;
import graph.Message;
import graph.Topic;
import graph.TopicManagerSingleton;

public class PlusAgent implements Agent {
    private final String[] subs;
    private final String[] pubs;
    private final Topic[] inputTopics;
    private final Topic[] outputTopics;
    private final double[] values;
    private final boolean[] validValues;

    public PlusAgent(String[] subs, String[] pubs) {
        validate(subs, pubs);
        this.subs = subs.clone();
        this.pubs = pubs.clone();

        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        inputTopics = new Topic[this.subs.length];
        outputTopics = new Topic[this.pubs.length];
        for (int i = 0; i < this.subs.length; i++) {
            inputTopics[i] = manager.getTopic(this.subs[i]);
        }
        for (int i = 0; i < this.pubs.length; i++) {
            outputTopics[i] = manager.getTopic(this.pubs[i]);
        }
        values = new double[this.subs.length];
        validValues = new boolean[this.subs.length];

        reset();
        for (Topic inputTopic : inputTopics) {
            inputTopic.subscribe(this);
        }
        for (Topic outputTopic : outputTopics) {
            outputTopic.addPublisher(this);
        }
    }

    @Override
    public String getName() {
        return String.join("+", subs) + "=" + String.join(",", pubs);
    }

    @Override
    public void reset() {
        for (int i = 0; i < values.length; i++) {
            values[i] = 0;
            validValues[i] = true;
        }
    }

    @Override
    public void callback(String topic, Message msg) {
        double value = msg == null ? Double.NaN : msg.asDouble;
        boolean valid = !Double.isNaN(value);

        int index = indexOf(subs, topic);
        if (index < 0) {
            return;
        }

        values[index] = value;
        validValues[index] = valid;

        for (boolean validValue : validValues) {
            if (!validValue) {
                return;
            }
        }

        double sum = 0;
        for (double current : values) {
            sum += current;
        }
        Message result = new Message(sum);
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

    private static void validate(String[] subs, String[] pubs) {
        if (subs == null || pubs == null || subs.length < 2 || pubs.length < 1
                || hasBlank(subs)) {
            throw new IllegalArgumentException("PlusAgent requires at least two subscriber topics and at least one publisher topic");
        }
        for (String pub : pubs) {
            if (isBlank(pub)) {
                throw new IllegalArgumentException("PlusAgent publisher topics cannot be blank");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean hasBlank(String[] values) {
        for (String value : values) {
            if (isBlank(value)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
