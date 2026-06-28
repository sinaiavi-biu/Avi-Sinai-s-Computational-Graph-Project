package configs;

public class MinAgent extends MathBinaryAgent {
    public MinAgent(String[] subs, String[] pubs) {
        super("MIN", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return Math.min(x, y);
    }
}
