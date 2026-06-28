package configs;

public class SubAgent extends MathBinaryAgent {
    public SubAgent(String[] subs, String[] pubs) {
        super("SUB", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return x - y;
    }
}
