package configs;

public class MulAgent extends MathBinaryAgent {
    public MulAgent(String[] subs, String[] pubs) {
        super("MUL", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return x * y;
    }
}
