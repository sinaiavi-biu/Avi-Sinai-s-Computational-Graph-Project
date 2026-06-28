package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Graph extends ArrayList<Node> {
    public boolean hasCycles() {
        for (Node node : this) {
            if (node.hasCycles()) {
                return true;
            }
        }
        return false;
    }

    public void createFromTopics() {
        clear();
        Map<String, Node> nodes = new HashMap<>();

        // The runtime graph is derived from topic registrations, not from the config
        // file, so manually published topics and edited configs appear in the view.
        for (Topic topic : TopicManagerSingleton.get().getTopics()) {
            Node topicNode = getOrCreate(nodes, "T" + topic.name);
            topicNode.setMessage(topic.getLastMessage());

            for (Agent subscriber : topic.subs) {
                Node agentNode = getOrCreate(nodes, "A" + subscriber.getName());
                topicNode.addEdge(agentNode);
            }

            for (Agent publisher : topic.pubs) {
                Node agentNode = getOrCreate(nodes, "A" + publisher.getName());
                agentNode.addEdge(topicNode);
            }
        }
    }

    private Node getOrCreate(Map<String, Node> nodes, String name) {
        Node node = nodes.get(name);
        if (node == null) {
            node = new Node(name);
            nodes.put(name, node);
            add(node);
        }
        return node;
    }
}
