package configs;

public class BufAgent extends BooleanGateAgent {
    public BufAgent(String[] subs, String[] pubs) {
        super("BUF", subs, pubs, 1);
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        return values[0];
    }
}
