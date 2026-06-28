package configs;

public class ModAgent extends MathBinaryAgent {
    public ModAgent(String[] subs, String[] pubs) {
        super("MOD", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return y == 0 ? Double.NaN : x % y;
    }
}
