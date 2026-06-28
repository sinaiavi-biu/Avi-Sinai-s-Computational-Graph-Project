package configs;

public class NorAgent extends OrAgent {
    public NorAgent(String[] subs, String[] pubs) {
        super(subs, pubs);
    }

    @Override
    public String getName() {
        return super.getName().replaceFirst("^OR", "NOR");
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        return !super.evaluate(values);
    }
}
