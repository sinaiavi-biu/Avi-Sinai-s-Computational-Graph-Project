package configs;

public class OrAgent extends BooleanGateAgent {
    public OrAgent(String[] subs, String[] pubs) {
        super("OR", subs, pubs, 2);
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        for (Boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }
}
