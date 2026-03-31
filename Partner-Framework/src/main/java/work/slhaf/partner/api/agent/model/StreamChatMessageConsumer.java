package work.slhaf.partner.api.agent.model;

public abstract class StreamChatMessageConsumer {
    private final StringBuilder responseText = new StringBuilder();

    public void onDelta(String delta) {
        consumeDelta(delta);
        responseText.append(delta);
    }

    public String collectResponse() {
        return responseText.toString();
    }

    protected abstract void consumeDelta(String delta);
}
