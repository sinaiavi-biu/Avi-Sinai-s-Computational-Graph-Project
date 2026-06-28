package configs;

import graph.Agent;
import graph.Message;
import graph.Topic;
import graph.TopicManagerSingleton;

public class IncAgent implements Agent {
    private final String[] subs;
    private final String[] pubs;
    private final Topic inputTopic;
    private final Topic[] outputTopics;

    public IncAgent(String[] subs, String[] pubs) {
        validate(subs, pubs);
        this.subs = subs.clone();
        this.pubs = pubs.clone();

        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        inputTopic = manager.getTopic(this.subs[0]);
        outputTopics = new Topic[this.pubs.length];
        for (int i = 0; i < this.pubs.length; i++) {
            outputTopics[i] = manager.getTopic(this.pubs[i]);
        }

        inputTopic.subscribe(this);
        for (Topic outputTopic : outputTopics) {
            outputTopic.addPublisher(this);
        }
    }

    @Override
    public String getName() {
        return subs[0] + "+1=" + String.join(",", pubs);
    }

    @Override
    public void reset() {
    }

    @Override
    public void callback(String topic, Message msg) {
        if (!subs[0].equals(topic) || msg == null || Double.isNaN(msg.asDouble)) {
            return;
        }
        Message result = new Message(msg.asDouble + 1);
        for (Topic outputTopic : outputTopics) {
            outputTopic.publish(result);
        }
    }

    @Override
    public void close() {
        inputTopic.unsubscribe(this);
        for (Topic outputTopic : outputTopics) {
            outputTopic.removePublisher(this);
        }
    }

    private static void validate(String[] subs, String[] pubs) {
        if (subs == null || pubs == null || subs.length < 1 || pubs.length < 1
                || isBlank(subs[0])) {
            throw new IllegalArgumentException("IncAgent requires one subscriber topic and at least one publisher topic");
        }
        for (String pub : pubs) {
            if (isBlank(pub)) {
                throw new IllegalArgumentException("IncAgent publisher topics cannot be blank");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
