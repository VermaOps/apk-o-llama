package ai;

import java.util.List;
import java.util.Map;

public interface AIProvider {
    
    /**
     * Generate text from a prompt
     * @param prompt The input prompt
     * @param options Optional parameters (temperature, max_tokens, etc.)
     * @return Unified response with text and metadata
     * @throws Exception if generation fails
     */
    AIResponse generateText(String prompt, Map<String, Object> options) throws Exception;
    
    /**
     * Chat with message history
     * @param messages List of messages with 'role' and 'content'
     * @param options Optional parameters
     * @return Unified response
     * @throws Exception if generation fails
     */
    AIResponse chat(List<Map<String, String>> messages, Map<String, Object> options) throws Exception;
    
    /**
     * Check if provider is available/configured
     */
    boolean isAvailable();
    
    /**
     * Get provider name (e.g., "ollama", "openai", "claude")
     */
    String getProviderName();
    
    /**
     * Get current model name
     */
    String getModel();
}