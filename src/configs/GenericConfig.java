package configs;

import graph.Agent;
import graph.ParallelAgent;
import graph.TopicManagerSingleton;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenericConfig implements Config {
    private static final int PARALLEL_AGENT_CAPACITY = 100;

    private String confFile;
    private final List<ManagedAgent> agents = new ArrayList<>();

    public void setConfFile(String confFile) {
        this.confFile = confFile;
    }

    @Override
    public void create() {
        if (confFile == null || confFile.trim().isEmpty()) {
            throw new IllegalStateException("Config file path was not set");
        }

        List<String> lines = readMeaningfulLines(confFile);
        if (lines.size() % 3 != 0) {
            throw new IllegalArgumentException("Config file must contain groups of 3 non-empty lines");
        }
        validateUniquePublishers(lines);

        for (int i = 0; i < lines.size(); i += 3) {
            String className = lines.get(i);
            String[] subs = splitTopics(lines.get(i + 1));
            String[] pubs = splitTopics(lines.get(i + 2));
            Agent agent = createAgent(className, subs, pubs);
            ParallelAgent parallelAgent = new ParallelAgent(agent, PARALLEL_AGENT_CAPACITY);
            // Agents register themselves during construction. Deployment swaps those
            // direct registrations for the ParallelAgent wrapper so callbacks run on
            // the agent worker thread instead of the publishing/request thread.
            moveRegistrationsToParallelAgent(agent, parallelAgent, subs, pubs);
            agents.add(new ManagedAgent(parallelAgent, subs, pubs));
        }
    }

    @Override
    public String getName() {
        return "Generic Config";
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void close() {
        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        for (ManagedAgent agent : agents) {
            for (String sub : agent.subs) {
                manager.getTopic(sub).unsubscribe(agent.parallelAgent);
            }
            for (String pub : agent.pubs) {
                manager.getTopic(pub).removePublisher(agent.parallelAgent);
            }
            agent.parallelAgent.close();
        }
        agents.clear();
    }

    private static List<String> readMeaningfulLines(String confFile) {
        try {
            List<String> result = new ArrayList<>();
            for (String line : Files.readAllLines(Path.of(confFile))) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read config file: " + confFile, e);
        }
    }

    private static String[] splitTopics(String line) {
        String[] raw = line.split(",");
        List<String> topics = new ArrayList<>();
        for (String topic : raw) {
            String trimmed = topic.trim();
            if (!trimmed.isEmpty()) {
                topics.add(trimmed);
            }
        }
        return topics.toArray(new String[0]);
    }

    private static Agent createAgent(String className, String[] subs, String[] pubs) {
        try {
            Class<?> cls = Class.forName(className);
            Constructor<?> constructor = cls.getConstructor(String[].class, String[].class);
            Object instance = constructor.newInstance(subs, pubs);
            return (Agent) instance;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Agent class not found: " + className, e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Agent class missing String[], String[] constructor: " + className, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            String detail = cause == null ? e.getMessage() : cause.getMessage();
            throw new IllegalArgumentException("Failed to instantiate agent: " + className
                    + (detail == null || detail.isBlank() ? "" : " - " + detail), e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Failed to instantiate agent: " + className, e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Configured class does not implement Agent: " + className, e);
        }
    }

    private static void validateUniquePublishers(List<String> lines) {
        Set<String> publishedTopics = new HashSet<>();
        for (int i = 0; i < lines.size(); i += 3) {
            String className = lines.get(i);
            String[] pubs = splitTopics(lines.get(i + 2));
            for (String pub : pubs) {
                if (!publishedTopics.add(pub)) {
                    throw new IllegalArgumentException(
                            "Topic " + pub + " has more than one configured publisher; second publisher is "
                                    + className);
                }
            }
        }
    }

    private static void moveRegistrationsToParallelAgent(Agent agent, ParallelAgent parallelAgent, String[] subs,
            String[] pubs) {
        agent.close();
        TopicManagerSingleton.TopicManager manager = TopicManagerSingleton.get();
        for (String sub : subs) {
            manager.getTopic(sub).subscribe(parallelAgent);
        }
        for (String pub : pubs) {
            manager.getTopic(pub).addPublisher(parallelAgent);
        }
    }

    private static class ManagedAgent {
        private final ParallelAgent parallelAgent;
        private final String[] subs;
        private final String[] pubs;

        private ManagedAgent(ParallelAgent parallelAgent, String[] subs, String[] pubs) {
            this.parallelAgent = parallelAgent;
            this.subs = subs.clone();
            this.pubs = pubs.clone();
        }
    }
}
