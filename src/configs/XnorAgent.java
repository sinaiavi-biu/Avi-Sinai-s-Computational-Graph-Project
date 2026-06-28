package configs;

public class XnorAgent extends XorAgent {
    public XnorAgent(String[] subs, String[] pubs) {
        super(subs, pubs);
    }

    @Override
    public String getName() {
        return super.getName().replaceFirst("^XOR", "XNOR");
    }

    @Override
    protected boolean evaluate(Boolean[] values) {
        return !super.evaluate(values);
    }
}
