package configs;

import graph.BinOpAgent;
import graph.Agent;

import java.util.ArrayList;
import java.util.List;

public class MathExampleConfig implements Config {
    private final List<Agent> agents = new ArrayList<>();

    @Override
    public void create() {
        agents.add(new BinOpAgent("plus", "A", "B", "R1", (x, y) -> x + y));
        agents.add(new BinOpAgent("minus", "A", "B", "R2", (x, y) -> x - y));
        agents.add(new BinOpAgent("mul", "R1", "R2", "R3", (x, y) -> x * y));
    }

    @Override
    public String getName() {
        return "Math Example";
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void close() {
        for (Agent agent : agents) {
            agent.close();
        }
        agents.clear();
    }
}
