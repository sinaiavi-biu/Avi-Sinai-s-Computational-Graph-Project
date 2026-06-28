package graph;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class TopicManagerSingleton {
    private TopicManagerSingleton() {
    }

    public static TopicManager get() {
        return TopicManager.instance;
    }

    public static class TopicManager {
        private static final TopicManager instance = new TopicManager();
        private final ConcurrentHashMap<String, Topic> topics;

        private TopicManager() {
            topics = new ConcurrentHashMap<>();
        }

        public Topic getTopic(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Topic name cannot be null");
            }
            return topics.computeIfAbsent(name, Topic::new);
        }

        public Collection<Topic> getTopics() {
            return topics.values();
        }

        public void removeTopic(String name) {
            if (name != null) {
                topics.remove(name);
            }
        }

        public void clear() {
            topics.clear();
        }
    }
}
