package ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class OllamaProvider implements AIProvider {
    
    private final String endpoint;
    private final String model;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxTokens;
    
    public OllamaProvider(String endpoint, String model, int connectTimeout, int readTimeout, int maxTokens) {
        this.endpoint = endpoint;
        this.model = model;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxTokens = maxTokens;
    }
    
    public OllamaProvider(String endpoint, String model) {
        this(endpoint, model, 17500, 52500, 2000);
    }
    
    @Override
    public AIResponse generateText(String prompt, Map<String, Object> options) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Merge options with defaults
        int maxTokensOpt = options.containsKey("max_tokens") ? 
            (int) options.get("max_tokens") : maxTokens;
        double temperature = options.containsKey("temperature") ? 
            (double) options.get("temperature") : 0.7;
        
        String jsonRequest = String.format("""
            {
                "model": "%s",
                "prompt": %s,
                "stream": false,
                "options": {
                    "temperature": %f,
                    "num_predict": %d
                }
            }
            """,
            model,
            escapeJson(prompt),
            temperature,
            maxTokensOpt
        );
        
        String response = sendRequest(jsonRequest);
        long latency = System.currentTimeMillis() - startTime;
        
        // Estimate tokens (Ollama doesn't return token counts)
        int inputTokens = estimateTokenCount(prompt);
        int outputTokens = estimateTokenCount(response);
        
        return new AIResponse(response, inputTokens, outputTokens, "ollama", model, latency);
    }
    
    @Override
    public AIResponse chat(List<Map<String, String>> messages, Map<String, Object> options) throws Exception {
        // Convert chat messages to single prompt for Ollama
        StringBuilder prompt = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("system".equals(role)) {
                prompt.append("System: ").append(content).append("\n\n");
            } else if ("user".equals(role)) {
                prompt.append("User: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                prompt.append("Assistant: ").append(content).append("\n");
            }
        }
        prompt.append("Assistant: ");
        
        return generateText(prompt.toString(), options);
    }
    
    @Override
    public boolean isAvailable() {
        try {
            URL url = new URI(endpoint + "/api/tags").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    @Override
    public String getModel() {
        return model;
    }
    
    // Add these exception classes as inner classes
    public static class OllamaTimeoutException extends Exception {
        public OllamaTimeoutException(String message) {
            super(message);
        }
    }

    public static class OllamaRateLimitException extends Exception {
        public OllamaRateLimitException(String message) {
            super(message);
        }
    }

    public static class OllamaServiceUnavailableException extends Exception {
        public OllamaServiceUnavailableException(String message) {
            super(message);
        }
    }

    private String sendRequest(String jsonRequest) throws Exception {
        URL url = new URI(endpoint + "/api/generate").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 429) {
            throw new Exception("Rate limit exceeded");
        } else if (responseCode == 503) {
            throw new Exception("Service unavailable");
        } else if (responseCode != 200) {
            throw new Exception("Ollama returned status " + responseCode);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        conn.disconnect();
        
        return extractResponse(response.toString());
    }
    
    private String extractResponse(String json) {
        try {
            int start = json.indexOf("\"response\":\"") + 12;
            if (start < 12) return json;
            int end = json.indexOf("\",\"", start);
            if (end < 0) end = json.indexOf("\"}", start);
            if (end < 0) return json;
            String response = json.substring(start, end);
            return response.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) {
            return json;
        }
    }
    
    private String escapeJson(String str) {
        return "\"" + str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
    
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    // Add these instance methods for configuration access
    public String getEndpoint() {
        return endpoint;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}