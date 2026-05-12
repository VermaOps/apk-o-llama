package ai;

public class AIResponse {
    private final String text;
    private final int inputTokens;
    private final int outputTokens;
    private final String provider;
    private final String model;
    private final long latencyMs;
    
    public AIResponse(String text, int inputTokens, int outputTokens, 
                      String provider, String model, long latencyMs) {
        this.text = text;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.provider = provider;
        this.model = model;
        this.latencyMs = latencyMs;
    }
    
    public String getText() { return text; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public int getTotalTokens() { return inputTokens + outputTokens; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getLatencyMs() { return latencyMs; }
    
    @Override
    public String toString() {
        return text;
    }
}