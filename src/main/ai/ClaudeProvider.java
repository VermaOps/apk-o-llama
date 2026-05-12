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

public class ClaudeProvider implements AIProvider {
    
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxTokens;
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    
    public ClaudeProvider(String apiKey, String model, int connectTimeout, int readTimeout, int maxTokens) {
        this(apiKey, "https://api.anthropic.com", model, connectTimeout, readTimeout, maxTokens);
    }
    
    public ClaudeProvider(String apiKey, String baseUrl, String model,
                          int connectTimeout, int readTimeout, int maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxTokens = maxTokens;
    }
    
    @Override
    public AIResponse generateText(String prompt, Map<String, Object> options) throws Exception {
        List<Map<String, String>> messages = List.of(
            Map.of("role", "user", "content", prompt)
        );
        return chat(messages, options);
    }
    
    @Override
    public AIResponse chat(List<Map<String, String>> messages, Map<String, Object> options) throws Exception {
        long startTime = System.currentTimeMillis();
        
        int maxTokensOpt = options.containsKey("max_tokens") ? 
            (int) options.get("max_tokens") : maxTokens;
        double temperature = options.containsKey("temperature") ? 
            (double) options.get("temperature") : 0.7;
        
        // Extract system message if present
        String systemMessage = null;
        StringBuilder messagesBuilder = new StringBuilder();
        messagesBuilder.append("[");
        int msgCount = 0;
        
        for (Map<String, String> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                systemMessage = msg.get("content");
            } else {
                if (msgCount > 0) {
                    messagesBuilder.append(",");
                }
                messagesBuilder.append("{")
                              .append("\"role\":\"").append(escapeJson(msg.get("role"))).append("\",")
                              .append("\"content\":\"").append(escapeJson(msg.get("content"))).append("\"")
                              .append("}");
                msgCount++;
            }
        }
        messagesBuilder.append("]");
        
        StringBuilder requestBody = new StringBuilder();
        requestBody.append("{")
                   .append("\"model\":\"").append(escapeJson(model)).append("\",")
                   .append("\"max_tokens\":").append(maxTokensOpt).append(",")
                   .append("\"temperature\":").append(temperature).append(",");
        
        if (systemMessage != null) {
            requestBody.append("\"system\":\"").append(escapeJson(systemMessage)).append("\",");
        }
        
        requestBody.append("\"messages\":").append(messagesBuilder.toString()).append("}");
        
        String response = sendRequest(requestBody.toString());
        long latency = System.currentTimeMillis() - startTime;
        
        // Parse response - extract content text
        String text = extractClaudeResponse(response);
        
        int inputTokens = estimateTokenCount(messages.toString());
        int outputTokens = estimateTokenCount(text);
        
        return new AIResponse(text, inputTokens, outputTokens, "claude", model, latency);
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE");
    }
    
    public boolean validateApiKey() {
        if (!isAvailable()) {
            return false;
        }
        try {
            URL url = new URI(baseUrl + "/v1/messages").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            // Minimal request body to validate key without consuming tokens
            String testModel = model != null && !model.isEmpty() ? model : "claude-3-haiku-20240307";
            String jsonRequest = "{\"model\":\"" + testModel + "\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "claude";
    }
    
    @Override
    public String getModel() {
        return model;
    }
    
    private String sendRequest(String jsonRequest) throws Exception {
        URL url = new URI(baseUrl + "/v1/messages").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 401) {
            throw new Exception("Invalid API key");
        } else if (responseCode == 429) {
            throw new Exception("Rate limit exceeded");
        } else if (responseCode != 200) {
            StringBuilder error = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    error.append(line);
                }
            }
            throw new Exception("Claude error " + responseCode + ": " + error.toString());
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
        
        return response.toString();
    }
    
    private String extractClaudeResponse(String json) {
        // Extract text from Claude's response format: {"content":[{"text":"..."}]}
        String searchPattern = "\"text\":\"";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex == -1) return "";
        startIndex += searchPattern.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return "";
        String text = json.substring(startIndex, endIndex);
        return text.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}