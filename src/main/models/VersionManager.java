package models;

public class VersionManager {
    private static final String EXTENSION_NAME = "APK-o-llama v1.3.0";
    private static String currentVersion = "1.3.0";
    
    static {
        extractVersion();
    }
    
    private static void extractVersion() {
        try {
            int vIndex = EXTENSION_NAME.indexOf("v");
            if (vIndex >= 0) {
                currentVersion = EXTENSION_NAME.substring(vIndex + 1);
            }
        } catch (Exception e) {
            // Keep default
        }
    }
    
    public static String getCurrentVersion() {
        return currentVersion;
    }
    
    public static String getExtensionName() {
        return EXTENSION_NAME;
    }
}