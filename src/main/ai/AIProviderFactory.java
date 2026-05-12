package ai;

import models.Configuration;

public class AIProviderFactory {
    
    public static AIProvider createProvider(Configuration config) throws Exception {
        String provider = config.getActiveProvider();
        
        switch (provider.toLowerCase()) {
            case "ollama":
                return new OllamaProvider(
                    config.getOllamaEndpoint(),
                    config.getOllamaModel(),
                    config.getConnectTimeout(),
                    config.getReadTimeout(),
                    config.getMaxTokens()
                );
                
            case "openai":
                String openaiKey = config.getOpenAIKey();
                if (openaiKey == null || openaiKey.isEmpty()) {
                    throw new Exception("OpenAI API key not configured");
                }
                return new OpenAIProvider(
                    openaiKey,
                    config.getOpenAIBaseUrl(),
                    config.getOpenAIModel(),
                    config.getConnectTimeout(),
                    config.getReadTimeout(),
                    config.getMaxTokens()
                );
                
            case "claude":
                String claudeKey = config.getClaudeKey();
                if (claudeKey == null || claudeKey.isEmpty()) {
                    throw new Exception("Claude API key not configured");
                }
                return new ClaudeProvider(
                    claudeKey,
                    config.getClaudeBaseUrl(),
                    config.getClaudeModel(),
                    config.getConnectTimeout(),
                    config.getReadTimeout(),
                    config.getMaxTokens()
                );
                
            default:
                throw new Exception("Unknown provider: " + provider);
        }
    }
    
    public static AIProvider createWithFallback(Configuration config) throws Exception {
        AIProvider primary = createProvider(config);
        
        // If fallback is configured and primary is not available, try fallback
        if (!primary.isAvailable() && config.hasFallbackProvider()) {
            String fallbackProvider = config.getFallbackProvider();
            Configuration tempConfig = Configuration.getInstance();
            tempConfig.setActiveProvider(fallbackProvider);
            tempConfig.setOpenAIKey(config.getOpenAIKey());
            tempConfig.setClaudeKey(config.getClaudeKey());
            return createProvider(tempConfig);
        }
        
        return primary;
    }
}