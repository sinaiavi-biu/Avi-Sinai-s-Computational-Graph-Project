package graph;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Topic {
    public final String name;
    final List<Agent> subs;
    final List<Agent> pubs;
    private volatile Message lastMessage;

    Topic(String name) {
        this.name = name;
        this.subs = new CopyOnWriteArrayList<>();
        this.pubs = new CopyOnWriteArrayList<>();
    }

    public void subscribe(Agent a) {
        if (a != null && !subs.contains(a)) {
            subs.add(a);
        }
    }

    public void unsubscribe(Agent a) {
        subs.remove(a);
    }

    public void publish(Message msg) {
        lastMessage = msg;
        for (Agent subscriber : subs) {
            subscriber.callback(name, msg);
        }
    }

    public void addPublisher(Agent a) {
        if (a != null && !pubs.contains(a)) {
            pubs.add(a);
        }
    }

    public void removePublisher(Agent a) {
        pubs.remove(a);
    }

    public int subscriberCount() {
        return subs.size();
    }

    public int publisherCount() {
        return pubs.size();
    }

    public Message getLastMessage() {
        return lastMessage;
    }
}
