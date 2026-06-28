package configs;

import graph.Agent;
import graph.Message;
import graph.Topic;
import graph.TopicManagerSingleton;

abstract class MathBinaryAgent implements Agent {
    private final String opName;
    private final String[] subs;
    private final String[] pubs;
    private final Topic[] inputTopics;
    private final Topic[] outputTopics;
    private final double[] values;
    private final boolean[] validValues;

    MathBinaryAgent(String opName, String[] subs, String[] pubs) {
        validate(opName, subs, pubs);
        this.opName = opName;
        this.subs = subs.clone();
        this.pubs = pubs.clone();

        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        inputTopics = new Topic[this.subs.length];
        for (int i = 0; i < this.subs.length; i++) {
            inputTopics[i] = manager.getTopic(this.subs[i]);
        }
        outputTopics = new Topic[this.pubs.length];
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
        return opName + "(" + String.join(",", subs) + ")=" + String.join(",", pubs);
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

        double resultValue = evaluate(values.clone());
        if (!Double.isFinite(resultValue)) {
            return;
        }
        Message result = new Message(resultValue);
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

    protected abstract double evaluate(double x, double y);

    protected double evaluate(double[] values) {
        double result = values[0];
        for (int i = 1; i < values.length; i++) {
            result = evaluate(result, values[i]);
        }
        return result;
    }

    private static void validate(String opName, String[] subs, String[] pubs) {
        if (isBlank(opName) || subs == null || pubs == null || subs.length < 2 || pubs.length < 1
                || hasBlank(subs)) {
            throw new IllegalArgumentException(opName + " requires at least two input topics and at least one output topic");
        }
        for (String pub : pubs) {
            if (isBlank(pub)) {
                throw new IllegalArgumentException(opName + " output topics cannot be blank");
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
