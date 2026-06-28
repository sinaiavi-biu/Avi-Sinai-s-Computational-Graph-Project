package configs;

public class MaxAgent extends MathBinaryAgent {
    public MaxAgent(String[] subs, String[] pubs) {
        super("MAX", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return Math.max(x, y);
    }
}
