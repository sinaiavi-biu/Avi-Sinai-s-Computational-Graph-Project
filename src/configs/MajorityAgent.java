package configs;

public class MajorityAgent extends BooleanGateAgent {
    public MajorityAgent(String[] subs, String[] pubs) {
        super("MAJORITY", subs, pubs, 2);
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        int trueCount = 0;
        for (Boolean value : values) {
            if (value) {
                trueCount++;
            }
        }
        return trueCount > values.length / 2;
    }
}
