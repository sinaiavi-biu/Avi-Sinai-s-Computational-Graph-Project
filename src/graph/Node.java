package graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Node {
    private String name;
    private List<Node> edges;
    private Message message;

    public Node(String name) {
        this.name = name;
        this.edges = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Node> getEdges() {
        return edges;
    }

    public void setEdges(List<Node> edges) {
        this.edges = edges == null ? new ArrayList<>() : edges;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public void addEdge(Node n) {
        if (n != null && !edges.contains(n)) {
            edges.add(n);
        }
    }

    public boolean hasCycles() {
        return hasCycles(new HashSet<>(), new HashSet<>());
    }

    private boolean hasCycles(Set<Node> visiting, Set<Node> visited) {
        if (visiting.contains(this)) {
            return true;
        }
        if (visited.contains(this)) {
            return false;
        }

        visiting.add(this);
        for (Node edge : edges) {
            if (edge.hasCycles(visiting, visited)) {
                return true;
            }
        }
        visiting.remove(this);
        visited.add(this);
        return false;
    }
}
