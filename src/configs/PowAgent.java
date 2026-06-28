package configs;

public class PowAgent extends MathBinaryAgent {
    public PowAgent(String[] subs, String[] pubs) {
        super("POW", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return Math.pow(x, y);
    }
}
