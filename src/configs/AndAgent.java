package configs;

public class AndAgent extends BooleanGateAgent {
    public AndAgent(String[] subs, String[] pubs) {
        super("AND", subs, pubs, 2);
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        for (Boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }
}
