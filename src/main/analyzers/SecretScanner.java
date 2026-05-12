package analyzers;

import models.Finding;
import models.Severity;
import utils.EntropyCalculator;
import models.Configuration;  
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import models.Configuration;

public class SecretScanner implements Analyzer {
    
    //private static final double ENTROPY_THRESHOLD = 4.5;
    private static final int MIN_SECRET_LENGTH = 16;
    private static final int COMMENT_CONTEXT_RANGE = 6; // Lines above/below for multi-line comment detection

    private static final List<SecretPattern> PATTERNS = List.of(
        new SecretPattern("Google API Key", Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"), Severity.CRITICAL, 0.95),
        new SecretPattern("AWS Access Key", Pattern.compile("AKIA[0-9A-Z]{16}"), Severity.CRITICAL, 0.98),
        new SecretPattern("Stripe API Key", Pattern.compile("sk_live_[0-9a-zA-Z]{24,}"), Severity.CRITICAL, 0.95),
        new SecretPattern("ChatGPT/OpenAI API Key", Pattern.compile("sk-[0-9a-zA-Z]{48,51}"), Severity.CRITICAL, 0.96),
        new SecretPattern("GitHub Token", Pattern.compile("ghp_[0-9a-zA-Z]{36}"), Severity.HIGH, 0.90),
        new SecretPattern("Generic API Key", Pattern.compile("(?i)api[_-]?key[\\s:=]+['\"]([0-9a-zA-Z_\\-]{20,})['\"]"), Severity.HIGH, 0.70),
        new SecretPattern("Password", Pattern.compile("(?i)password[\\s:=]+['\"]([^'\"]{6,})['\"]"), Severity.CRITICAL, 0.80),
        new SecretPattern("OpenAI Organization ID", Pattern.compile("org-[0-9a-zA-Z]{24}"), Severity.MEDIUM, 0.80),
        new SecretPattern("OpenAI Session Token", Pattern.compile("sess-[0-9a-zA-Z]{64}"), Severity.HIGH, 0.85),
        new SecretPattern("Anthropic/Claude API Key", Pattern.compile("sk-ant-[a-zA-Z0-9]{8,}-[0-9a-zA-Z]{24,50}"), Severity.CRITICAL, 0.93)
    );
    
    private static final List<Pattern> SAFE_PATTERNS = List.of(
        Pattern.compile("^import\\s+"),           // Import statements
        Pattern.compile("^package\\s+"),          // Package declarations
        Pattern.compile("^\\s*@\\w+"),            // Annotations
        Pattern.compile("//\\s*TODO"),             // TODO comments
        Pattern.compile("//\\s*FIXME"),            // FIXME comments
        Pattern.compile("\\*\\s*@(param|return|throws)") // Javadoc tags
    );
    
    private static final Set<String> LOW_SIGNAL_KEYWORDS = Set.of(
        "license", "workspace", "tenant", "accountid", "syncfusion",
        "ckeditor", "licensekey", "viber", "calllink", "shareid",
        "webspellcheckerbundlepath", "wscservice", "wscbundle",
        "npm_config_globalignorefile", "jenkins_url", "jenkins_links",
        "git+ssh", "npm_config_globalconfig", "const_url", "jirascript.src",
        "nreum", "agentid", "xpid", "trustkey", "cspapiurl", "keen", "Gmap"
    );
    
    private static final List<String> SECRET_KEYWORDS = List.of(
        // Credential-related (HIGH SIGNAL)
        "api_key", "client_id", "client_secret", "aws_id", "aws_secret", 
        "app_id", "storage_bucket", "private_key", "pubkey",
        
        // Password-related (HIGH SIGNAL)
        "passcode", "passwd", "password", "pwd",
        
        // Crypto-related (HIGH SIGNAL)
        "cryptosecret", "cryptosecretkey", "cryptokey", "initializationvector",
        
        // Token-related (HIGH SIGNAL)
        "openai_key", "openai_secret", "chatgpt_api", "llm_key", "llm_secret",
        
        // These are useful but require assignment check
        "firebase_url", "firebase.auth.API_KEY"
    );
    
    @Override
    public String getAnalyzerName() {
        return "SecretScanner";
    }
    
    @Override
    public List<Finding> analyze(File file) throws Exception {
        List<Finding> findings = new ArrayList<>();
        
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n");
        
        // Skip if file is mostly imports/packages (build files)
        if (isBuildFile(content)) {
            return findings;
        }

        System.out.println("Scanning for secrets: " + file.getName());
        
        for (SecretPattern pattern : PATTERNS) {
            Matcher matcher = pattern.pattern.matcher(content);
                
            while (matcher.find()) {
                String match = matcher.group();
                int lineNumber = getLineNumber(content, matcher.start());
                String lineContent = lines[lineNumber - 1];
                boolean isInComment = isCommentLine(lineContent) || isInMultiLineComment(content, matcher.start(), lines);
                
                if (isTestValue(match)) {
                    continue;
                }
                
                // Extract assignment and validate value
                AssignmentInfo assignment = extractAssignmentInfo(lineContent, pattern.name);
                Severity validatedSeverity = null;
                
                if (assignment.hasValue) {
                    validatedSeverity = classifySecretValue(assignment.value);
                }
                
                double finalConfidence = pattern.confidence;
                Severity finalSeverity;
                String descriptionSuffix = "";
                
                if (validatedSeverity != null) {
                    // Use validated severity
                    finalSeverity = validatedSeverity;
                    if (validatedSeverity == Severity.LOW) {
                        finalConfidence = pattern.confidence * 0.3;
                        descriptionSuffix = " (placeholder/test value)";
                    } else if (validatedSeverity == Severity.MEDIUM) {
                        finalConfidence = pattern.confidence * 0.6;
                        descriptionSuffix = " (suspicious value - review manually)";
                    } else {
                        descriptionSuffix = " (valid secret pattern)";
                    }
                } else {
                    finalSeverity = pattern.severity;
                }
                
                if (isInComment) {
                    finalConfidence = finalConfidence * Configuration.getInstance().getCommentSeverityMultiplier();
                    finalSeverity = adjustSeverityForComment(finalSeverity);
                }
                
                // Skip LOW severity findings in comments if they're placeholders (optional filtering)
                if (isInComment && finalSeverity == Severity.LOW && validatedSeverity == Severity.LOW) {
                    continue; // Skip placeholder comments entirely
                }
                    
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Hardcoded Secret: " + pattern.name,
                    finalSeverity,
                    "Secrets",
                    file.getAbsolutePath(),
                    lineNumber,
                    String.format("Found hardcoded %s.%s%s", pattern.name, isInComment ? " (in comment)" : "", descriptionSuffix),
                    truncate(match, 50),
                    finalConfidence
                    ));
                }
            }
        
                // Only run entropy detection if enabled in config
        if (Configuration.getInstance().isEntropyDetectionEnabled()) {
            findings.addAll(detectHighEntropyStringsWithComments(lines, file.getAbsolutePath()));
        }
        
        // Keyword detection with comment awareness
        findings.addAll(detectKeywordsWithCommentAwareness(lines, file.getAbsolutePath()));
        
        return findings;
    }
    
    private List<Finding> detectKeywordsWithCommentAwareness(String[] lines, String filePath) {
        List<Finding> findings = new ArrayList<>();
        Configuration config = Configuration.getInstance();
        boolean scanComments = config.isScanComments();
        boolean flagStringLiterals = config.isFlagStringLiterals();
        Severity stringLiteralSeverity = config.getStringLiteralSeverity();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Skip safe contexts (imports, packages, annotations)
            if (isSafeContext(line)) {
                continue;
            }
            
            boolean isCommentLine = isCommentLine(line);
            
            // Skip comment lines entirely if comment scanning is disabled
            if (isCommentLine && !scanComments) {
                continue;
            }
            
            String lineLower = line.toLowerCase();
            
            for (String keyword : SECRET_KEYWORDS) {
                // Skip low-signal keywords entirely unless they have an assignment
                if (isLowSignalKeyword(keyword)) {
                    continue;  // These generate too many false positives
                }
                
                int keywordPos = lineLower.indexOf(keyword.toLowerCase());
                if (keywordPos == -1) continue;
                
                // Skip if keyword appears without assignment in non-comment code
                AssignmentInfo assignment = extractAssignmentInfo(line, keyword);
                
                // For non-comment lines, require assignment to proceed
                if (!isCommentLine && !assignment.hasAssignment) {
                    continue;  // Keyword without value assignment - likely just a reference
                }
                
                // Check if keyword is inside string literal
                boolean insideString = !isCommentLine && isInsideStringLiteral(line, keywordPos);
                
                // Determine severity based on context
                Severity severity;
                double confidence;
                String description;
                
                // Use unified classifier for assignments with values
                Severity valueSeverity = null;
                if (assignment.hasAssignment && assignment.hasValue) {
                    valueSeverity = classifySecretValue(assignment.value);
                }
                
                if (isCommentLine) {
                    // Comment-specific logic
                    if (assignment.hasAssignment && assignment.hasValue && valueSeverity != null) {
                        switch (valueSeverity) {
                            case HIGH:
                                severity = Severity.HIGH;
                                confidence = 0.75 * config.getCommentSeverityMultiplier();
                                description = "Real secret found in comment: " + keyword;
                                break;
                            case MEDIUM:
                                severity = Severity.MEDIUM;
                                confidence = 0.55 * config.getCommentSeverityMultiplier();
                                description = "Suspicious secret in comment: " + keyword;
                                break;
                            default:
                                severity = Severity.LOW;
                                confidence = 0.30 * config.getCommentSeverityMultiplier();
                                description = "Test/placeholder secret in comment: " + keyword;
                                break;
                        }
                    } else {
                        severity = Severity.INFO;
                        confidence = 0.15 * config.getCommentSeverityMultiplier();
                        description = "Security keyword mentioned in comment: " + keyword;
                    }
                } else if (insideString) {
                    // Keyword inside string literal
                    if (flagStringLiterals) {
                        severity = stringLiteralSeverity;
                        confidence = 0.25;
                        description = "Security keyword found inside string literal: " + keyword;
                    } else {
                        // Skip entirely if not configured to flag
                        continue;
                    }
                } else if (valueSeverity != null) {
                    // Use unified classification for assignments
                    switch (valueSeverity) {
                        case HIGH:
                            severity = Severity.HIGH;
                            confidence = 0.85;
                            description = "Hardcoded secret in variable: " + keyword;
                            break;
                        case MEDIUM:
                            severity = Severity.MEDIUM;
                            confidence = 0.60;
                            description = "Suspicious value for security keyword: " + keyword;
                            break;
                        default:
                            severity = Severity.LOW;
                            confidence = 0.40;
                            description = "Placeholder/test value for security keyword: " + keyword;
                            break;
                    }
                } else if (assignment.hasAssignment) {
                    // Keyword in variable but no value extracted - downgrade to INFO
                    severity = Severity.INFO;
                    confidence = 0.30;
                    description = "Security keyword with unextractable value: " + keyword;
                } else {
                    // Fallback: This case should not be reached due to earlier continue
                    // Keep but downgrade to INFO
                    severity = Severity.INFO;
                    confidence = 0.20;
                    description = "Security keyword referenced in code: " + keyword;
                }
                
                // Extract evidence
                int start = Math.max(0, keywordPos - 30);
                int end = Math.min(line.length(), keywordPos + keyword.length() + 30);
                String evidence = line.substring(start, end);
                
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Security Keyword Found",
                    severity,
                    "Secrets",
                    filePath,
                    i + 1,
                    description + (assignment.hasValue ? " Value: " + truncate(assignment.value, 50) : ""),
                    truncate(evidence, 100),
                    confidence
                ));
                break; // One finding per line is enough
            }
            
            // Check for sensitive data logging patterns (only for non-comment lines)
            if (!isCommentLine) {
                checkSensitiveLogging(line, filePath, i + 1, findings);
            }
        }
        
        return findings;
    }
    
    private List<Finding> detectHighEntropyStringsWithComments(String[] lines, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        double entropyThreshold = Configuration.getInstance().getEntropyThreshold();
        boolean scanComments = Configuration.getInstance().isScanComments();
        
        Pattern stringPattern = Pattern.compile("['\"]([A-Za-z0-9+/=]{" + MIN_SECRET_LENGTH + ",})['\"]");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isCommentLine = isCommentLine(line);
            
            // Skip comment lines if comment scanning is disabled
            if (isCommentLine && !scanComments) {
                continue;
            }
            
            // For non-comment lines, skip commented code
            if (!isCommentLine && (line.trim().startsWith("//") || line.trim().startsWith("#"))) {
                continue;
            }
            
            Matcher matcher = stringPattern.matcher(line);
            
            while (matcher.find()) {
                String candidate = matcher.group(1);
                
                if (EntropyCalculator.isHighEntropy(candidate, entropyThreshold)) {
                    boolean nearKeyword = false;
                    String context = getContext(lines, i, 3).toLowerCase();
                    
                    for (String keyword : SECRET_KEYWORDS) {
                        if (context.contains(keyword.toLowerCase())) {
                            nearKeyword = true;
                            break;
                        }
                    }
                    
                    if (nearKeyword && !isTestValue(candidate)) {
                        double confidence = isCommentLine ? 0.65 : 0.75;
                        if (isCommentLine) {
                            confidence *= Configuration.getInstance().getCommentSeverityMultiplier();
                        }
                        
                        findings.add(new Finding(
                            UUID.randomUUID().toString(),
                            "High-Entropy String Near Security Keyword" + (isCommentLine ? " (in comment)" : ""),
                            isCommentLine ? Severity.MEDIUM : Severity.HIGH,
                            "Secrets",
                            filePath,
                            i + 1,
                            "Found high-entropy string near security keywords: " + truncate(candidate, 50),
                            truncate(candidate, 100),
                            confidence
                        ));
                    }
                }
            }
        }
        
        return findings;
    }

    private AssignmentInfo extractAssignmentInfo(String line, String keyword) {
        AssignmentInfo info = new AssignmentInfo();
        String lineLower = line.toLowerCase();
        int keywordPos = lineLower.indexOf(keyword.toLowerCase());
        
        if (keywordPos == -1) return info;
        
        // Look for assignment patterns after the keyword
        String afterKeyword = line.substring(keywordPos + keyword.length());
        
        // Check for = or : assignment
        int eqPos = afterKeyword.indexOf('=');
        int colonPos = afterKeyword.indexOf(':');
        int assignPos = -1;
        
        if (eqPos != -1 && (colonPos == -1 || eqPos < colonPos)) {
            assignPos = eqPos;
            info.hasAssignment = true;
        } else if (colonPos != -1) {
            assignPos = colonPos;
            info.hasAssignment = true;
        }
        
        if (info.hasAssignment && assignPos != -1) {
            // Extract value after assignment
            String afterAssign = afterKeyword.substring(assignPos + 1).trim();
            
            // Look for quoted value
            if (afterAssign.startsWith("\"") || afterAssign.startsWith("'")) {
                char quoteChar = afterAssign.charAt(0);
                int endQuote = afterAssign.indexOf(quoteChar, 1);
                if (endQuote != -1) {
                    info.value = afterAssign.substring(1, endQuote);
                    info.hasValue = true;
                }
            } else {
                // Unquoted value - take up to space or end of line
                int spacePos = afterAssign.indexOf(' ');
                if (spacePos != -1) {
                    info.value = afterAssign.substring(0, spacePos);
                } else {
                    info.value = afterAssign;
                }
                info.hasValue = !info.value.isEmpty();
            }
        }
        
        return info;
    }
    
    private boolean isStrongSecretValue(String value) {
        if (value == null || value.isEmpty()) return false;
        
        // Check length
        if (value.length() < MIN_SECRET_LENGTH) return false;
        
        // Check against test values
        if (isTestValue(value)) return false;
        
        // Check entropy if enabled
        if (Configuration.getInstance().isEntropyDetectionEnabled()) {
            double threshold = Configuration.getInstance().getEntropyThreshold();
            if (EntropyCalculator.isHighEntropy(value, threshold)) {
                return true;
            }
        }
        
        // Check against known patterns
        for (SecretPattern pattern : PATTERNS) {
            if (pattern.pattern.matcher(value).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Unified value validation for both keyword and pattern detection.
     * Returns severity based on value strength.
     */
    private Severity classifySecretValue(String value) {
        if (value == null || value.isEmpty()) {
            return null; // No value to classify
        }
        
        // Check for placeholders first
        if (isWeakSecretValue(value)) {
            return Severity.LOW;
        }
        
        // Check for strong secrets
        if (isStrongSecretValue(value)) {
            return Severity.HIGH;
        }
        
        // Default: suspicious but not strong
        return Severity.MEDIUM;
    }

    private boolean isWeakSecretValue(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        return lower.isEmpty() || 
               lower.equals("null") ||
               lower.equals("test") ||
               lower.equals("sample") ||
               lower.equals("example") ||
               lower.equals("placeholder") ||
               lower.equals("your_api_key") ||
               lower.equals("changeme") ||
               value.matches("^[a*]+$");
    }

    private boolean isSafeContext(String line) {
        for (Pattern safePattern : SAFE_PATTERNS) {
            if (safePattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isBuildFile(String content) {
        String trimmed = content.trim();
        // Skip files that are primarily build configuration
        if (trimmed.startsWith("dependencies {") || 
            trimmed.startsWith("plugins {") ||
            trimmed.startsWith("android {") ||
            trimmed.contains("build.gradle")) {
            return true;
        }
        return false;
    }
    
    private boolean isLowSignalKeyword(String keyword) {
        return LOW_SIGNAL_KEYWORDS.contains(keyword.toLowerCase());
    }
    
    // String literal detection state
    private static class StringContext {
        boolean insideString = false;
        char quoteChar = 0;
        
        void processChar(char c) {
            if (!insideString && (c == '"' || c == '\'')) {
                insideString = true;
                quoteChar = c;
            } else if (insideString && c == quoteChar) {
                // Check if escaped
                // Note: This simplified version works for single char lookback
                insideString = false;
                quoteChar = 0;
            }
        }
        
        void reset() {
            insideString = false;
            quoteChar = 0;
        }
    }
    
    private boolean isInsideStringLiteral(String line, int keywordStartPos) {
        StringContext context = new StringContext();
        for (int i = 0; i < keywordStartPos; i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < keywordStartPos) {
                i++; // Skip escaped character
                continue;
            }
            context.processChar(c);
        }
        return context.insideString;
    }

    private boolean isCommentLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || 
               trimmed.startsWith("#") || 
               trimmed.startsWith("*") ||
               trimmed.startsWith("/*");
    }
    
    private boolean isInMultiLineComment(String content, int position, String[] lines) {
        // Simple check: look for /* before position and */ after position
        String before = content.substring(0, position);
        String after = content.substring(position);
        
        int lastOpen = before.lastIndexOf("/*");
        int lastClose = before.lastIndexOf("*/");
        
        if (lastOpen == -1) return false;
        
        // If we're inside a multi-line comment, the last /* should be after the last */
        return lastOpen > lastClose;
    }
    
    private Severity adjustSeverityForComment(Severity original) {
        double multiplier = Configuration.getInstance().getCommentSeverityMultiplier();
        
        switch (original) {
            case CRITICAL:
                return multiplier >= 0.7 ? Severity.HIGH : Severity.MEDIUM;
            case HIGH:
                return multiplier >= 0.7 ? Severity.MEDIUM : Severity.LOW;
            case MEDIUM:
                return Severity.LOW;
            default:
                return Severity.LOW;
        }
    }
    
    // Inner class for assignment info
    private static class AssignmentInfo {
        boolean hasAssignment = false;
        boolean hasValue = false;
        String value = "";
    }
    
    private boolean containsKeywordWithWildcard(String text, String keyword) {
        String textLower = text.toLowerCase();
        String keywordLower = keyword.toLowerCase();
        
        // Simple wildcard support: * matches any sequence
        if (keywordLower.contains("*")) {
            String regex = keywordLower.replace("*", ".*");
            return textLower.matches(".*" + regex + ".*");
        }
        
        return textLower.contains(keywordLower);
    }

    private boolean isTestValue(String value) {
        String lower = value.toLowerCase();
        return lower.contains("test") || lower.contains("example") || lower.contains("demo") ||
               lower.contains("your_api_key") || lower.contains("placeholder") ||
               lower.equals("") || value.matches("^[a*]+$");
    }
    
    private String getContext(String[] lines, int lineIndex, int range) {
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, lineIndex - range);
        int end = Math.min(lines.length, lineIndex + range + 1);
        
        for (int i = start; i < end; i++) {
            context.append(lines[i]).append(" ");
        }
        
        return context.toString();
    }
    
    private int getLineNumber(String content, int position) {
        return (int) content.substring(0, position).chars().filter(ch -> ch == '\n').count() + 1;
    }
    
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
    
    private static class SecretPattern {
        String name;
        Pattern pattern;
        Severity severity;
        double confidence;
        
        SecretPattern(String name, Pattern pattern, Severity severity, double confidence) {
            this.name = name;
            this.pattern = pattern;
            this.severity = severity;
            this.confidence = confidence;
        }
    }

    private void checkSensitiveLogging(String line, String filePath, int lineNumber, List<Finding> findings) {
        // Skip commented lines
        String trimmedLine = line.trim();
        if (trimmedLine.startsWith("//") || trimmedLine.startsWith("*") || trimmedLine.startsWith("/*")) {
            return;
        }
        
        // Common logging patterns that might contain sensitive data
        String lineLower = line.toLowerCase();
        
        // Pattern 1: Android Log calls with variable concatenation
        /*if (lineLower.contains("log.") && (lineLower.contains("password") || 
                                            lineLower.contains("token") || 
                                            lineLower.contains("key") || 
                                            lineLower.contains("secret"))) {
            
            // Extract a relevant portion of the line
            int logStart = lineLower.indexOf("log.");
            int end = Math.min(line.length(), logStart + 100);
            String evidence = line.substring(Math.max(0, logStart), end);
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Potential Sensitive Data Logging",
                Severity.MEDIUM,
                "Logging",
                filePath,
                lineNumber,
                "Log statement found near sensitive data keywords. Review for potential data leakage.",
                truncate(evidence, 150),
                0.65
            ));
        }
        
        // Pattern 2: System.out.println with sensitive patterns
        if ((lineLower.contains("system.out.print") || lineLower.contains("println(")) &&
            (lineLower.contains("=\"") || lineLower.contains(":") || lineLower.contains("password"))) {
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Console Output with Potential Sensitive Data",
                Severity.LOW,
                "Logging",
                filePath,
                lineNumber,
                "System.out.println found - may contain sensitive data in production builds.",
                truncate(line, 120),
                0.60
            ));
        }*/
        
        // Pattern 3: Toast messages with variable data
        if (lineLower.contains("toast.maketext") && 
            (lineLower.contains("+") || lineLower.contains("format(") || lineLower.contains("concat("))) {
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Dynamic Toast Message",
                Severity.LOW,
                "Logging",
                filePath,
                lineNumber,
                "Toast message constructed dynamically - may leak sensitive information via UI.",
                truncate(line, 120),
                0.55
            ));
        }
    }
}
