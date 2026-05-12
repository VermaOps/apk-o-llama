// models/Configuration.java - Fixed implementation

package models;

import java.io.*;
import java.util.Properties;

public class Configuration {
    // Ollama Configuration - DEFAULTS (hardcoded)
    private String ollamaEndpoint = "http://localhost:11434";
    private String ollamaModel = "qwen2.5-coder:7b";
    private int connectTimeout = 17500;
    private int readTimeout = 52500;
    private int maxTokens = 2000;
    
    // Scan Configuration - DEFAULTS (hardcoded)
    private double entropyThreshold = 4.5;
    private int maxFileSizeMB = 10;
    private boolean scanBinaryFiles = true;
    private boolean entropyDetectionEnabled = true;
    private boolean debugMode = false;

    // Comment scanning configuration
    private boolean scanComments = true;
    private double commentSeverityMultiplier = 0.5;
    
    // String literal detection configuration
    private boolean flagStringLiterals = false;
    private String stringLiteralSeverity = "LOW";

    // Keyword detection configuration
    private boolean requireAssignmentForKeywords = true;
    private boolean enableLowSignalKeywords = false;

    // NEW: Version check state
    private String latestVersion = null;
    private boolean updateAvailable = false;
    private long lastVersionCheckTime = 0;
    private String versionCheckError = null;

    // NEW: Multi-provider AI configuration
    private String activeProvider = "ollama";  // "ollama", "openai", "claude"
    private String fallbackProvider = null;     // Optional fallback provider
    
    // OpenAI configuration
    private String openAIKey = null;            // From environment variable
    private String openAIModel = "gpt-3.5-turbo";
    private String openAIBaseUrl = "https://api.openai.com";
    
    // Claude configuration
    private String claudeKey = null;            // From environment variable
    private String claudeModel = "claude-3-sonnet-20240229";
    private String claudeBaseUrl = "https://api.anthropic.com";

    // Singleton pattern
    private static Configuration instance;
    private static final String CONFIG_FILE = "apkollama.config";
    
    // Ollama API Key (for consistency, even though not used)
    private String ollamaApiKey = null;

    private Configuration() {
        // DO NOT auto-load on startup
        // Start with defaults always
        System.out.println("Configuration initialized with DEFAULTS");
    }
    
    public static Configuration getInstance() {
        if (instance == null) {
            instance = new Configuration();
        }
        return instance;
    }
    
    // ========== RESET TO DEFAULTS METHOD ==========
    public void resetToDefaults() {
        this.ollamaEndpoint = "http://localhost:11434";
        this.ollamaModel = "qwen2.5-coder:7b";
        this.connectTimeout = 17500;
        this.readTimeout = 52500;
        this.maxTokens = 2000;
        this.entropyThreshold = 4.5;
        this.maxFileSizeMB = 10;
        this.scanBinaryFiles = true;
        this.entropyDetectionEnabled = true;
        this.debugMode = false;
        
        System.out.println("Configuration reset to defaults");
    }
    
        // ========== ENVIRONMENT VARIABLE LOADING ==========
    public void loadFromEnvironment() {
        // Load API keys from environment variables (system properties as fallback)
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey == null) {
            openaiKey = System.getProperty("OPENAI_API_KEY");
        }
        if (openaiKey != null && !openaiKey.isEmpty()) {
            this.openAIKey = openaiKey;
        }
        
        String claudeKey = System.getenv("CLAUDE_API_KEY");
        if (claudeKey == null) {
            claudeKey = System.getProperty("CLAUDE_API_KEY");
        }
        if (claudeKey != null && !claudeKey.isEmpty()) {
            this.claudeKey = claudeKey;
        }
        
        // Load provider preference from environment
        String provider = System.getenv("AI_PROVIDER");
        if (provider == null) {
            provider = System.getProperty("AI_PROVIDER");
        }
        if (provider != null && !provider.isEmpty()) {
            this.activeProvider = provider.toLowerCase();
        }
    }
    
    // ========== LOAD FROM FILE (MANUAL) ==========
    public void loadFromFile() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            System.out.println("No config file found, using defaults");
            return;
        }
        
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
            
            // Only apply if property exists, otherwise keep current value
            if (props.containsKey("ollamaEndpoint"))
                this.ollamaEndpoint = props.getProperty("ollamaEndpoint");
            if (props.containsKey("ollamaModel"))
                this.ollamaModel = props.getProperty("ollamaModel");
            if (props.containsKey("connectTimeout"))
                this.connectTimeout = Integer.parseInt(props.getProperty("connectTimeout"));
            if (props.containsKey("readTimeout"))
                this.readTimeout = Integer.parseInt(props.getProperty("readTimeout"));
            if (props.containsKey("maxTokens"))
                this.maxTokens = Integer.parseInt(props.getProperty("maxTokens"));
            if (props.containsKey("entropyThreshold"))
                this.entropyThreshold = Double.parseDouble(props.getProperty("entropyThreshold"));
            if (props.containsKey("maxFileSizeMB"))
                this.maxFileSizeMB = Integer.parseInt(props.getProperty("maxFileSizeMB"));
            if (props.containsKey("scanBinaryFiles"))
                this.scanBinaryFiles = Boolean.parseBoolean(props.getProperty("scanBinaryFiles"));
            if (props.containsKey("entropyDetectionEnabled"))
                this.entropyDetectionEnabled = Boolean.parseBoolean(props.getProperty("entropyDetectionEnabled"));
            if (props.containsKey("debugMode"))
                this.debugMode = Boolean.parseBoolean(props.getProperty("debugMode"));
            if (props.containsKey("scanComments"))
                this.scanComments = Boolean.parseBoolean(props.getProperty("scanComments"));
            if (props.containsKey("commentSeverityMultiplier"))
                this.commentSeverityMultiplier = Double.parseDouble(props.getProperty("commentSeverityMultiplier"));
            if (props.containsKey("flagStringLiterals"))
                this.flagStringLiterals = Boolean.parseBoolean(props.getProperty("flagStringLiterals"));
            if (props.containsKey("stringLiteralSeverity"))
                this.stringLiteralSeverity = props.getProperty("stringLiteralSeverity");
            // NEW: Load version check state
            if (props.containsKey("latestVersion"))
                this.latestVersion = props.getProperty("latestVersion");
            if (props.containsKey("updateAvailable"))
                this.updateAvailable = Boolean.parseBoolean(props.getProperty("updateAvailable"));
            if (props.containsKey("lastVersionCheckTime"))
                this.lastVersionCheckTime = Long.parseLong(props.getProperty("lastVersionCheckTime"));
            if (props.containsKey("versionCheckError"))
                this.versionCheckError = props.getProperty("versionCheckError");
            // NEW: Load multi-provider settings
            if (props.containsKey("activeProvider"))
                this.activeProvider = props.getProperty("activeProvider");
            if (props.containsKey("fallbackProvider"))
                this.fallbackProvider = props.getProperty("fallbackProvider");
            if (props.containsKey("openAIModel"))
                this.openAIModel = props.getProperty("openAIModel");
            if (props.containsKey("openAIBaseUrl"))
                this.openAIBaseUrl = props.getProperty("openAIBaseUrl");
            if (props.containsKey("claudeModel"))
                this.claudeModel = props.getProperty("claudeModel");
            if (props.containsKey("claudeBaseUrl"))
                this.claudeBaseUrl = props.getProperty("claudeBaseUrl");
            if (props.containsKey("ollamaApiKey"))
                this.ollamaApiKey = props.getProperty("ollamaApiKey");

            System.out.println("Configuration loaded from file: " + CONFIG_FILE);
            
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in config file: " + e.getMessage());
        }
    }
    
    // ========== SAVE TO FILE ==========
    public void saveToFile() {
        Properties props = new Properties();
        props.setProperty("ollamaEndpoint", ollamaEndpoint);
        props.setProperty("ollamaModel", ollamaModel);
        props.setProperty("connectTimeout", String.valueOf(connectTimeout));
        props.setProperty("readTimeout", String.valueOf(readTimeout));
        props.setProperty("maxTokens", String.valueOf(maxTokens));
        props.setProperty("entropyThreshold", String.valueOf(entropyThreshold));
        props.setProperty("maxFileSizeMB", String.valueOf(maxFileSizeMB));
        props.setProperty("scanBinaryFiles", String.valueOf(scanBinaryFiles));
        props.setProperty("entropyDetectionEnabled", String.valueOf(entropyDetectionEnabled));
        props.setProperty("debugMode", String.valueOf(debugMode));
        props.setProperty("scanComments", String.valueOf(scanComments));
        props.setProperty("commentSeverityMultiplier", String.valueOf(commentSeverityMultiplier));
        props.setProperty("flagStringLiterals", String.valueOf(flagStringLiterals));
        props.setProperty("stringLiteralSeverity", stringLiteralSeverity);
        
        // NEW: Save version check state
        if (latestVersion != null) {
            props.setProperty("latestVersion", latestVersion);
        }
        props.setProperty("updateAvailable", String.valueOf(updateAvailable));
        props.setProperty("lastVersionCheckTime", String.valueOf(lastVersionCheckTime));
        if (versionCheckError != null) {
            props.setProperty("versionCheckError", versionCheckError);
        }
        // NEW: Save multi-provider settings
        props.setProperty("activeProvider", activeProvider);
        if (fallbackProvider != null) {
            props.setProperty("fallbackProvider", fallbackProvider);
        }
        if (ollamaApiKey != null) {
            props.setProperty("ollamaApiKey", ollamaApiKey);
        }
        props.setProperty("openAIModel", openAIModel);
        props.setProperty("openAIBaseUrl", openAIBaseUrl);
        props.setProperty("claudeModel", claudeModel);
        props.setProperty("claudeBaseUrl", claudeBaseUrl);

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "APK-o-llama Configuration");
            System.out.println("Configuration saved to file: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
                // Load API keys from environment variables
        loadFromEnvironment();
    }
    
    // ========== GETTERS AND SETTERS ==========
    public String getOllamaEndpoint() { return ollamaEndpoint; }
    public void setOllamaEndpoint(String ollamaEndpoint) { this.ollamaEndpoint = ollamaEndpoint; }
    
    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String ollamaModel) { this.ollamaModel = ollamaModel; }
    
    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    
    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
    
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    
    public double getEntropyThreshold() { return entropyThreshold; }
    public void setEntropyThreshold(double entropyThreshold) { this.entropyThreshold = entropyThreshold; }
    
    public int getMaxFileSizeMB() { return maxFileSizeMB; }
    public void setMaxFileSizeMB(int maxFileSizeMB) { this.maxFileSizeMB = maxFileSizeMB; }
    
    public boolean isScanBinaryFiles() { return scanBinaryFiles; }
    public void setScanBinaryFiles(boolean scanBinaryFiles) { this.scanBinaryFiles = scanBinaryFiles; }
    
    public boolean isEntropyDetectionEnabled() { return entropyDetectionEnabled; }
    public void setEntropyDetectionEnabled(boolean entropyDetectionEnabled) { this.entropyDetectionEnabled = entropyDetectionEnabled; }
    
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public boolean isScanComments() { return scanComments; }
    public void setScanComments(boolean scanComments) { this.scanComments = scanComments; }
    
    public double getCommentSeverityMultiplier() { return commentSeverityMultiplier; }
    public void setCommentSeverityMultiplier(double multiplier) { this.commentSeverityMultiplier = multiplier; }
    
    // ========== STRING LITERAL DETECTION GETTERS AND SETTERS ==========
    public boolean isFlagStringLiterals() { return flagStringLiterals; }
    public void setFlagStringLiterals(boolean flag) { this.flagStringLiterals = flag; }
    
    public Severity getStringLiteralSeverity() {
        try {
            return Severity.valueOf(stringLiteralSeverity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Severity.LOW;
        }
    }
    public void setStringLiteralSeverity(String severity) { this.stringLiteralSeverity = severity; }

    // ========== KEYWORD DETECTION GETTERS AND SETTERS ==========
    public boolean isRequireAssignmentForKeywords() { return requireAssignmentForKeywords; }
    public void setRequireAssignmentForKeywords(boolean require) { this.requireAssignmentForKeywords = require; }
    
    public boolean isEnableLowSignalKeywords() { return enableLowSignalKeywords; }
    public void setEnableLowSignalKeywords(boolean enable) { this.enableLowSignalKeywords = enable; }

    // ========== MULTI-PROVIDER GETTERS AND SETTERS ==========
    public String getActiveProvider() { return activeProvider; }
    public void setActiveProvider(String provider) { this.activeProvider = provider; }
    
    public String getFallbackProvider() { return fallbackProvider; }
    public void setFallbackProvider(String provider) { this.fallbackProvider = provider; }
    public boolean hasFallbackProvider() { return fallbackProvider != null && !fallbackProvider.isEmpty(); }
    
    public String getOpenAIKey() { return openAIKey; }
    public void setOpenAIKey(String key) { this.openAIKey = key; }
    
    public String getOpenAIModel() { return openAIModel; }
    public void setOpenAIModel(String model) { this.openAIModel = model; }
    
    public String getOpenAIBaseUrl() { return openAIBaseUrl; }
    public void setOpenAIBaseUrl(String url) { this.openAIBaseUrl = url; }
    
    public String getClaudeKey() { return claudeKey; }
    public void setClaudeKey(String key) { this.claudeKey = key; }
    
    public String getClaudeModel() { return claudeModel; }
    public void setClaudeModel(String model) { this.claudeModel = model; }
    
    public String getClaudeBaseUrl() { return claudeBaseUrl; }
    public void setClaudeBaseUrl(String url) { this.claudeBaseUrl = url; }

    // ========== VERSION CHECK GETTERS AND SETTERS ==========
    public String getLatestVersion() { return latestVersion; }
    public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public void setUpdateAvailable(boolean updateAvailable) { this.updateAvailable = updateAvailable; }

    public long getLastVersionCheckTime() { return lastVersionCheckTime; }
    public void setLastVersionCheckTime(long lastVersionCheckTime) { this.lastVersionCheckTime = lastVersionCheckTime; }

    public String getVersionCheckError() { return versionCheckError; }
    public void setVersionCheckError(String versionCheckError) { this.versionCheckError = versionCheckError; }

    public String getOllamaApiKey() { return ollamaApiKey; }
    public void setOllamaApiKey(String apiKey) { this.ollamaApiKey = apiKey; }
}