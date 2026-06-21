#!/bin/bash
# APK-o-Llama Build Script v1.3.0
# Builds Burp Suite extension for Android APK security analysis
# Supports: Ollama, OpenAI, Claude AI providers

set -e

echo "=========================================="
echo "APK-o-Llama Build Script v1.3.0"
echo "AI-Powered Android Security Analysis"
echo "=========================================="
echo ""

# Configuration
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main"
BUILD_DIR="$PROJECT_DIR/build"
OUTPUT_JAR="$PROJECT_DIR/apk-o-llama.jar"
STANDALONE_JAR="$PROJECT_DIR/apk-o-llama-standalone.jar"

# Burp JAR location (macOS default - adjust for your setup)
BURP_JAR="/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar"

# Check if Burp JAR exists
if [ ! -f "$BURP_JAR" ]; then
    echo "ERROR: Burp Suite JAR not found!"
    echo "Expected at: $BURP_JAR"
    echo ""
    echo "Please set BURP_JAR environment variable:"
    echo "  export BURP_JAR=/path/to/your/burpsuite.jar"
    echo ""
    read -p "Enter Burp JAR path: " user_burp_jar
    if [ -f "$user_burp_jar" ]; then
        BURP_JAR="$user_burp_jar"
    else
        echo "ERROR: Invalid path: $user_burp_jar"
        exit 1
    fi
fi

echo "[1/9] Checking prerequisites..."
echo "  Java version:"
java -version 2>&1 | head -1
echo "  Burp JAR: $(basename "$BURP_JAR")"
echo "  Project directory: $PROJECT_DIR"
echo "  Source directory: $SRC_DIR"

# Check source directory structure
echo ""
echo "[2/9] Checking project structure..."
if [ ! -d "$SRC_DIR" ]; then
    echo "  ✗ Source directory not found: $SRC_DIR"
    echo "  Creating directory structure..."
    mkdir -p "$SRC_DIR"/{ai,analyzers,burp,burp/ui,core,models,rules,scanner,utils}
    echo "  ✓ Created directory structure"
    echo "  Please place your Java files in the appropriate directories"
    exit 1
fi

# Verify all required directories exist
REQUIRED_DIRS=("ai" "analyzers" "burp" "burp/ui" "core" "models" "rules" "scanner" "utils")
for dir in "${REQUIRED_DIRS[@]}"; do
    if [ ! -d "$SRC_DIR/$dir" ]; then
        echo "  ⚠️ Missing directory: $SRC_DIR/$dir"
        echo "  Creating it..."
        mkdir -p "$SRC_DIR/$dir"
    fi
done

# Count Java files by directory
echo ""
echo "[3/9] Scanning for Java files..."

DIRECTORIES=("ai" "analyzers" "burp" "burp/ui" "core" "models" "rules" "scanner" "utils")

echo "  Directory breakdown:"
TOTAL_FILES=0
for dir in "${DIRECTORIES[@]}"; do
    if [ -d "$SRC_DIR/$dir" ]; then
        count=$(find "$SRC_DIR/$dir" -name "*.java" 2>/dev/null | wc -l)
        if [ "$count" -gt 0 ]; then
            echo "    ✓ $dir/: $count file(s)"
            TOTAL_FILES=$((TOTAL_FILES + count))
        else
            echo "    ⚠️ $dir/: No Java files (but directory exists)"
        fi
    fi
done

if [ "$TOTAL_FILES" -eq 0 ]; then
    echo "  ✗ No Java files found!"
    echo "  Current structure:"
    find "$SRC_DIR" -type d | sort | sed 's/^/    /'
    exit 1
fi

echo "  Total Java files: $TOTAL_FILES"

# List all Java files
echo ""
echo "[4/9] Listing all source files:"
find "$SRC_DIR" -name "*.java" | while read -r file; do
    rel_path="${file#$SRC_DIR/}"
    echo "    $rel_path"
done

# Check critical files
echo ""
echo "[5/9] Checking critical files..."

CRITICAL_FILES=(
    # Burp extension core
    "burp/BurpExtender.java"
    "burp/ui/MainTab.java"                    # COMPLETELY REWRITTEN - 3 tabs, config UI
    
    # NEW: Configuration and Version Management
    "models/Configuration.java"                # NEW - Persistent config with file I/O
    "models/VersionManager.java"               # NEW - Version tracking
    
    # Core engine
    "core/FindingCollector.java"               # ENHANCED - AI filtering methods
    "core/StandaloneTest.java"                 # ENHANCED - CLI mode
    
    # Analyzers - UPDATED with 3 new analyzers
    "analyzers/Analyzer.java"
    "analyzers/ManifestAnalyzer.java"          # ENHANCED
    "analyzers/SecretScanner.java"             # ENHANCED - SMALI, OpenAI patterns
    "analyzers/CryptographyAnalyzer.java"      # NEW - Crypto weakness detection
    "analyzers/EnhancedManifestAnalyzer.java"  # NEW - Advanced manifest checks
    "analyzers/BinaryAnalyzer.java"            # NEW - Binary file analysis
    
    # Models - UPDATED
    "models/Finding.java"                      # ENHANCED - AI fields and methods
    "models/ScanResult.java"
    "models/Severity.java"
    "models/AIStatus.java"                     # ENHANCED - TIMEOUT, RATE_LIMITED, CANCELLED
    
    # AI Integration - Complete set
    "ai/AIProvider.java"
    "ai/AIResponse.java"
    "ai/AIProviderFactory.java"
    "ai/OllamaClient.java"
    "ai/OllamaProvider.java"
    "ai/OllamaRequest.java"
    "ai/OllamaRequestManager.java"
    "ai/OpenAIProvider.java"
    "ai/ClaudeProvider.java"
    "ai/ConversationHistory.java"
    
    # Rules engine
    "rules/RuleEngine.java"
    "rules/RuleRegistry.java"
    
    # Scanner
    "scanner/FileScanner.java"
    "scanner/FileType.java"
    
    # Utilities
    "utils/EntropyCalculator.java"
    "utils/XmlParser.java"
)

echo "  Checking ${#CRITICAL_FILES[@]} critical files:"
missing_files=()
found_count=0
for file in "${CRITICAL_FILES[@]}"; do
    if [ ! -f "$SRC_DIR/$file" ]; then
        missing_files+=("$file")
        echo "    ✗ Missing: $file"
    else
        echo "    ✓ Found: $file"
        ((found_count++))
    fi
done

echo ""
echo "  Found $found_count of ${#CRITICAL_FILES[@]} critical files"

if [ ${#missing_files[@]} -gt 0 ]; then
    echo ""
    echo "  ⚠️ Missing files:"
    printf "    %s\n" "${missing_files[@]}"
    
    read -p "  Continue anyway? (y/n): " continue_choice
    if [ "$continue_choice" != "y" ] && [ "$continue_choice" != "Y" ]; then
        exit 1
    fi
else
    echo "  ✓ All critical files found"
fi

# Create build directory
echo ""
echo "[6/9] Creating build directories..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Find all Java files
JAVA_FILES=$(find "$SRC_DIR" -name "*.java")

echo "  Compiling $TOTAL_FILES Java files..."

# Compile with proper classpath
COMPILE_LOG="$PROJECT_DIR/compile.log"
echo "  Compile log: $COMPILE_LOG"

# Create classpath with Burp JAR
CLASS_PATH="$BURP_JAR"

# Check for external dependencies
if [ -f "$PROJECT_DIR/libs" ] && [ -d "$(find "$PROJECT_DIR/libs" -name "*.jar" 2>/dev/null | head -1)" ]; then
    echo "  Adding external libraries..."
    for jar in "$PROJECT_DIR"/libs/*.jar; do
        if [ -f "$jar" ]; then
            CLASS_PATH="$CLASS_PATH:$jar"
            echo "    Added: $(basename "$jar")"
        fi
    done
fi

echo "  Classpath: $(echo "$CLASS_PATH" | tr ':' '\n' | sed 's|^|    |')"

# Compile with enhanced flags
javac -d "$BUILD_DIR" \
      -cp "$CLASS_PATH" \
      -Xlint:unchecked \
      -Xlint:deprecation \
      $JAVA_FILES 2>&1 | tee "$COMPILE_LOG"

COMPILE_STATUS=$?

if [ $COMPILE_STATUS -eq 0 ]; then
    echo "  ✓ Compilation successful"
    echo "  Compiled classes in: $BUILD_DIR/"
else
    echo "  ✗ Compilation failed"
    echo ""
    echo "=== COMPILATION ERRORS ==="
    grep -A 2 -B 2 "error:" "$COMPILE_LOG" | head -50
    echo ""
    echo "See full log: $COMPILE_LOG"
    
    # Check for common errors
    if grep -q "package.*does.not.exist" "$COMPILE_LOG"; then
        echo ""
        echo "=== PACKAGE ISSUE DETECTED ==="
        echo "Files might have wrong package declarations"
        echo "Current package declarations:"
        find "$SRC_DIR" -name "*.java" -exec grep -l "package " {} \; | head -10 | xargs grep -h "^package " | sed 's/^/    /'
    fi
    
    exit 1
fi

# Check for warnings
WARNINGS=$(grep -c "warning:" "$COMPILE_LOG" || true)
if [ "$WARNINGS" -gt 0 ]; then
    echo "  ⚠️ Found $WARNINGS warnings (see compile.log)"
fi

# Package the JAR
echo ""
echo "[7/9] Packaging JAR..."

# Create directory structure in build for packaging
mkdir -p "$BUILD_DIR/META-INF"
cat > "$BUILD_DIR/META-INF/MANIFEST.MF" << EOF
Manifest-Version: 1.0
Created-By: APK-o-Llama Build Script
Main-Class: burp.BurpExtender
Build-Date: $(date)
EOF

# Create JAR with proper package structure
cd "$BUILD_DIR"

# Find all class files
CLASS_FILES=$(find . -name "*.class")

echo "  Packing $(echo "$CLASS_FILES" | wc -l) class files..."

# Create JAR with manifest
jar -cfm "$OUTPUT_JAR" META-INF/MANIFEST.MF $CLASS_FILES

cd "$PROJECT_DIR"

echo "  ✓ Created JAR: $(basename "$OUTPUT_JAR")"

# Verify the JAR
echo ""
echo "[8/9] Verifying JAR..."

if [ ! -f "$OUTPUT_JAR" ]; then
    echo "  ✗ JAR creation failed!"
    exit 1
fi

JAR_SIZE=$(du -h "$OUTPUT_JAR" | cut -f1)
CLASS_COUNT=$(jar -tf "$OUTPUT_JAR" | grep "\.class$" | wc -l)

echo "  JAR file: $(basename "$OUTPUT_JAR")"
echo "  Size: $JAR_SIZE"
echo "  Total classes: $CLASS_COUNT"

echo ""
echo "  Important packages in JAR:"
jar -tf "$OUTPUT_JAR" | grep "\.class$" | sed 's/\.class$//' | sed 's/\//./g' | sort | grep -E "(ai|models|burp|analyzers|rules|scanner|core)" | sed 's/^/    /'

echo ""
echo "  All packages:"
jar -tf "$OUTPUT_JAR" | grep "\.class$" | sed 's/\.class$//' | sed 's/\//./g' | sort | uniq | sed 's/^/    /'

# Create standalone version (without Burp dependencies)
echo ""
echo "[9/9] Creating standalone version..."

# Create a manifest for standalone version
cat > "$BUILD_DIR/META-INF/MANIFEST.MF.standalone" << EOF
Manifest-Version: 1.0
Created-By: APK-o-Llama Build Script
Main-Class: core.StandaloneTest
Build-Date: $(date)
EOF

cd "$BUILD_DIR"
jar -cfm "$STANDALONE_JAR" META-INF/MANIFEST.MF.standalone $CLASS_FILES
cd "$PROJECT_DIR"

if [ -f "$STANDALONE_JAR" ]; then
    echo "  ✓ Created standalone JAR: $(basename "$STANDALONE_JAR")"
    echo "    Run with: java -jar \"$STANDALONE_JAR\" /path/to/apk"
else
    echo "  ⚠️ Could not create standalone JAR"
fi

# Generate build info
echo ""
echo "=========================================="
echo "BUILD SUCCESSFUL! 🎉"
echo "=========================================="
echo ""
echo "Output files:"
echo "  • $OUTPUT_JAR (Burp Extension)"
if [ -f "$STANDALONE_JAR" ]; then
    echo "  • $STANDALONE_JAR (Standalone CLI)"
fi
echo ""
echo "Stats:"
echo "  Source files: $TOTAL_FILES"
echo "  Compiled classes: $CLASS_COUNT"
echo "  Compile warnings: $WARNINGS"
echo ""
echo "To install in Burp Suite:"
echo "  1. Open Burp Suite"
echo "  2. Go to Extender → Extensions"
echo "  3. Click 'Add'"
echo "  4. Select Java as extension type"
echo "  5. Browse to: $OUTPUT_JAR"
echo ""
echo "For standalone testing:"
if [ -f "$STANDALONE_JAR" ]; then
    echo "  java -jar \"$STANDALONE_JAR\" /path/to/apk/directory"
else
    echo "  java -cp \"$OUTPUT_JAR\" core.StandaloneTest /path/to/apk/directory"
fi
echo ""
echo "AI Provider Support:"
echo "  • Ollama: http://localhost:11434 (default)"
echo "  • OpenAI: Requires OPENAI_API_KEY environment variable"
echo "  • Claude: Requires CLAUDE_API_KEY environment variable"
echo ""
echo "To configure AI provider:"
echo "  export AI_PROVIDER=ollama|openai|claude"
echo "  export OPENAI_API_KEY=sk-...  (for OpenAI)"
echo "  export CLAUDE_API_KEY=sk-ant-... (for Claude)"
echo ""
echo "For Ollama, ensure it's running:"
echo "  ollama serve"
echo "  ollama pull qwen2.5-coder:7b"
echo ""
echo "=========================================="