package configs;

public class DivAgent extends MathBinaryAgent {
    public DivAgent(String[] subs, String[] pubs) {
        super("DIV", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return y == 0 ? Double.NaN : x / y;
    }
}
