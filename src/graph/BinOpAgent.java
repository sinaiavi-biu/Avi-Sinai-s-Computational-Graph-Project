package graph;

import java.util.function.BinaryOperator;

public class BinOpAgent implements Agent {
    private final String name;
    private final String inputTopicName1;
    private final String inputTopicName2;
    private final Topic inputTopic1;
    private final Topic inputTopic2;
    private final Topic outputTopic;
    private final BinaryOperator<Double> operator;
    private double value1;
    private double value2;
    private boolean hasValue1;
    private boolean hasValue2;

    public BinOpAgent(String name, String inputTopicName1, String inputTopicName2, String outputTopicName,
            BinaryOperator<Double> operator) {
        if (name == null || inputTopicName1 == null || inputTopicName2 == null || outputTopicName == null
                || operator == null) {
            throw new IllegalArgumentException("BinOpAgent arguments cannot be null");
        }
        this.name = name;
        this.inputTopicName1 = inputTopicName1;
        this.inputTopicName2 = inputTopicName2;
        this.operator = operator;

        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        this.inputTopic1 = manager.getTopic(inputTopicName1);
        this.inputTopic2 = manager.getTopic(inputTopicName2);
        this.outputTopic = manager.getTopic(outputTopicName);

        inputTopic1.subscribe(this);
        inputTopic2.subscribe(this);
        outputTopic.addPublisher(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void reset() {
        value1 = 0;
        value2 = 0;
        hasValue1 = true;
        hasValue2 = true;
    }

    @Override
    public void callback(String topic, Message msg) {
        double value = msg == null ? Double.NaN : msg.asDouble;
        boolean valid = !Double.isNaN(value);

        if (inputTopicName1.equals(topic)) {
            value1 = value;
            hasValue1 = valid;
        } else if (inputTopicName2.equals(topic)) {
            value2 = value;
            hasValue2 = valid;
        } else {
            return;
        }

        if (hasValue1 && hasValue2) {
            outputTopic.publish(new Message(operator.apply(value1, value2)));
        }
    }

    @Override
    public void close() {
        inputTopic1.unsubscribe(this);
        inputTopic2.unsubscribe(this);
        outputTopic.removePublisher(this);
    }
}
