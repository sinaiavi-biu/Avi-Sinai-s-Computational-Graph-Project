package configs;

public class NandAgent extends AndAgent {
    public NandAgent(String[] subs, String[] pubs) {
        super(subs, pubs);
    }

    @Override
    public String getName() {
        return super.getName().replaceFirst("^AND", "NAND");
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        return !super.evaluate(values);
    }
}
