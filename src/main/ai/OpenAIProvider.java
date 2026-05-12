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

public class OpenAIProvider implements AIProvider {
    
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxTokens;
    
    public OpenAIProvider(String apiKey, String model, int connectTimeout, int readTimeout, int maxTokens) {
        this(apiKey, "https://api.openai.com", model, connectTimeout, readTimeout, maxTokens);
    }
    
    public OpenAIProvider(String apiKey, String baseUrl, String model, 
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
        
        // Build JSON request manually (without Gson)
        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> msg = messages.get(i);
            messagesJson.append("{")
                       .append("\"role\":\"").append(escapeJson(msg.get("role"))).append("\",")
                       .append("\"content\":\"").append(escapeJson(msg.get("content"))).append("\"")
                       .append("}");
            if (i < messages.size() - 1) {
                messagesJson.append(",");
            }
        }
        messagesJson.append("]");
        
        String jsonRequest = String.format("""
            {
                "model": "%s",
                "messages": %s,
                "max_tokens": %d,
                "temperature": %f,
                "stream": false
            }
            """,
            escapeJson(model),
            messagesJson.toString(),
            maxTokensOpt,
            temperature
        );
        
        String response = sendRequest(jsonRequest);
        long latency = System.currentTimeMillis() - startTime;
        
        // Parse JSON response manually
        String text = extractJsonValue(response, "content");
        int inputTokens = extractIntValue(response, "prompt_tokens");
        int outputTokens = extractIntValue(response, "completion_tokens");
        
        return new AIResponse(text, inputTokens, outputTokens, "openai", model, latency);
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
            URL url = new URI(baseUrl + "/v1/models").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public String getModel() {
        return model;
    }
    
    private String sendRequest(String jsonRequest) throws Exception {
        URL url = new URI(baseUrl + "/v1/chat/completions").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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
            throw new Exception("OpenAI error " + responseCode + ": " + error.toString());
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
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private String extractJsonValue(String json, String key) {
        String searchPattern = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex == -1) {
            // Try without quotes for numeric values
            searchPattern = "\"" + key + "\":";
            startIndex = json.indexOf(searchPattern);
            if (startIndex == -1) return "";
            startIndex += searchPattern.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
            if (endIndex == -1) return "";
            return json.substring(startIndex, endIndex).trim();
        }
        startIndex += searchPattern.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return "";
        return json.substring(startIndex, endIndex);
    }
    
    private int extractIntValue(String json, String key) {
        String searchPattern = "\"" + key + "\":";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex == -1) return 0;
        startIndex += searchPattern.length();
        int endIndex = json.indexOf(",", startIndex);
        if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
        if (endIndex == -1) return 0;
        try {
            return Integer.parseInt(json.substring(startIndex, endIndex).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}