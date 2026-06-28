package configs;

public class AvgAgent extends MathBinaryAgent {
    public AvgAgent(String[] subs, String[] pubs) {
        super("AVG", subs, pubs);
    }

    @Override
    protected double evaluate(double x, double y) {
        return (x + y) / 2.0;
    }

    @Override
    protected double evaluate(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
