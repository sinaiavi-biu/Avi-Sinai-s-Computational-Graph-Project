package configs;

public class NotAgent extends BooleanGateAgent {
    public NotAgent(String[] subs, String[] pubs) {
        super("NOT", subs, pubs, 1);
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        return !values[0];
    }
}
