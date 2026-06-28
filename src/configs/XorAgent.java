package configs;

public class XorAgent extends BooleanGateAgent {
    public XorAgent(String[] subs, String[] pubs) {
        super("XOR", subs, pubs, 2);
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        boolean result = false;
        for (Boolean value : values) {
            result ^= value;
        }
        return result;
    }
}
