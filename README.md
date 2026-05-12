[![Burp Suite Extension](https://img.shields.io/badge/Burp%20Suite-Extension-orange)](https://portswigger.net/burp)
[![Version](https://img.shields.io/badge/Version-1.3.0-blue)](https://github.com/VermaOps/apk-o-llama/releases)
[![Ollama](https://img.shields.io/badge/Ollama-Required-yellow)](https://ollama.com)
[![OpenAI](https://img.shields.io/badge/OpenAI-Supported-green)](https://openai.com)
[![Claude](https://img.shields.io/badge/Claude-Supported-purple)](https://anthropic.com)

# APK-o-Llama: AI-Powered APK Security Analysis for Burp Suite

## 📋 Table of Contents
- [Overview](#overview)
- [Key Highlights](#key-highlights)
- [Deep Burp Suite Integration](#deep-burp-suite-integration)
- [Security Analysis Engine](#security-analysis-engine)
  - [Static Analyzers](#static-analyzers)
  - [Detection Capabilities](#detection-capabilities)
- [AI Integration Architecture](#ai-integration-architecture)
  - [Multi-Provider Support](#multi-provider-support)
- [Provider Configuration](#provider-configuration)
- [Installation Guide](#installation-guide)
  - [Prerequisites](#prerequisites)
  - [Method 1: Pre-compiled Installation](#method-1-pre-compiled-installation-recommended)
  - [Method 2: Custom Build Installation](#method-2-custom-build-installation)
  - [Method 3: Standalone CLI Mode](#method-3-standalone-cli-mode)
- [Key Features](#key-features)
  - [Security & Privacy](#security--privacy)
  - [Export Findings](#export-findings)
  - [Analysis Capabilities](#analysis-capabilities)
  - [AI-Powered Reporting](#ai-powered-reporting)
  - [Performance Features](#performance-features)
- [Usage Workflow](#usage-workflow)
  - [APK Analysis](#apk-analysis)
  - [AI-Assisted Vulnerability Reporting](#ai-assisted-vulnerability-reporting)
  - [Multi-Finding Batch Processing](#multi-finding-batch-processing)
  - [Result Visualization](#result-visualization)
- [Screenshots](#screenshots)
- [Support Development](#support-development)
- [Report Issues](#report-issues)
- [Community & Feedback](#community--feedback)

## Overview

**APK-o-Llama** is a professional-grade Burp Suite extension that combines static APK security analysis with **multi-provider AI integration** (Ollama, OpenAI, Claude), enhanced analyzers, comprehensive configuration, and a professional UI. Designed specifically for mobile application security testers and Android bug bounty hunters, this tool transforms traditional static analysis by adding AI-powered vulnerability assessment and report generation directly within Burp Suite's interface.

## Key Highlights

- **6 Specialized Analyzers** — Comprehensive scanning for 70+ security issues
- **Multi-Provider AI Support** — Ollama (local, free), OpenAI (GPT-4/GPT-3.5), Claude (Sonnet/Haiku)
- **Comprehensive APK static analysis** — Decompiled APK scanning for 50+ security issues
- **Zero Data Leakage Options** — Local Ollama keeps data on your machine; cloud providers optional
- **AI-generated vulnerability reports** — Professional bug bounty-style write-ups for each finding
- **Multi-finding batch processing** — Analyze multiple vulnerabilities simultaneously
- **Real-time AI status tracking** — Visual feedback for pending/in-progress/completed analysis
- **Click-to-retry interface** — One-click retry for failed or timed-out AI requests
- **Severity-based color coding** — CRITICAL 🔴, HIGH 🟠, MEDIUM 🟡, LOW 🟢
- **Confidence scoring** — Machine-learning based confidence metrics (0-100%)
- **Persistent Configuration** — Save your settings to file between sessions
- **True AI conversation** — Feel the true AI conversation with Auto context-storing to your local machine up to 20 chats.
- **HTML Export** — Professional self-contained vulnerability reports
- **Automatic Version Checking** — Never miss an update

## Deep Burp Suite Integration

The extension integrates seamlessly into Burp Suite's ecosystem:

- **Dedicated "APK-o-Llama" Tab**: Central dashboard for APK analysis and AI results
- **Split-pane Interface**: Left panel for finding details, right panel for AI-generated reports
- **Configuration Tab** — Full control over Ollama settings, scan parameters, and system status
- **AI Console Tab** — Standalone AI conversation with context retention
- **Sortable Findings Table**: Multi-column sorting by severity, confidence, and AI status
- **Context-Aware UI**: Dynamic button states based on selection and AI request status
- **Progress Tracking** — Real-time progress bar showing "AI Analysis: 3/10 (Failed: 1)"

## Security Analysis Engine

### Static Analyzers

| Analyzer | File Types | Detection Focus |
|----------|------------|-----------------|
| **SecretScanner** | Java, Kotlin, Smali, XML, Config | Hardcoded API keys, passwords, tokens |
| **CryptographyAnalyzer** | Java, Kotlin, Smali | Weak algorithms, ECB mode, hardcoded keys |
| **ManifestAnalyzer** | AndroidManifest.xml | Debuggable apps, backup enabled, exported components |
| **EnhancedManifestAnalyzer** | AndroidManifest.xml | Dangerous permissions, task hijacking, WebView security |
| **BinaryAnalyzer** | Native libs, assets, certificates | Embedded secrets, private keys, certificates |

### Detection Capabilities

**Secrets & Credentials** (CRITICAL/HIGH):
- AWS Access Keys, Google API Keys, Stripe Keys
- OpenAI/ChatGPT API Keys, GitHub Tokens
- Generic passwords and API keys in code
- High-entropy strings near security keywords
- Expanded keyword list (70+ security terms)
- Comment-aware detection with configurable severity multiplier
- String literal detection with configurable severity
- Assignment validation to reduce false positives

**Cryptographic Issues** (HIGH/MEDIUM):
- DES, RC4, MD5, SHA1 usage
- ECB encryption mode
- Hardcoded keys and IVs
- Insecure random number generation (`new random`)

**Manifest Misconfigurations** (CRITICAL/HIGH):
- Debuggable applications in production
- Exported components without permissions
- Cleartext traffic allowed
- Backup enabled exposing sensitive data
- Task hijacking vulnerabilities
- Dangerous permission analysis (20+ permissions)
- Content provider security checks
- WebView security misconfigurations

**Binary Analysis** (CRITICAL/MEDIUM):
- Embedded RSA private keys
- Certificates in binary files
- Security keywords in binary content

## AI Integration Architecture

```text
┌─────────────────────────────────────────────────────────────────────┐
│                        Burp Suite Professional                      │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
┌─────────────────────────────────▼───────────────────────────────────┐
│                     APK-o-Llama Extension v1.3.0                    │
│                                                                     │
│  ┌──────────────────────────────┐    ┌───────────────────────────┐  │
│  │    FileScanner/RuleEngine    │    │    FindingCollector       │  │
│  │  APK decompilation & analysis│    │  Results aggregation      │  │
│  └──────────────────────────────┘    └───────────────────────────┘  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    AIProviderFactory                          │  │
│  │  Creates: OllamaProvider | OpenAIProvider | ClaudeProvider    │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                   OllamaRequestManager                        │  │
│  │  Async queue manager with retry, cancellation, health checks  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │  Ollama     │  │  OpenAI     │  │  Claude     │                  │
│  │  Provider   │  │  Provider   │  │  Provider   │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘                  │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          │                       │                       │
┌─────────▼─────────┐   ┌─────────▼─────────┐   ┌─────────▼──────────┐
│  Ollama API       │   │  OpenAI API       │   │  Claude API        │
│  localhost:11434  │   │  api.openai.com   │   │  api.anthropic.com │
└─────────┬─────────┘   └─────────┬─────────┘   └─────────┬──────────┘
          │                       │                       │
┌─────────▼─────────┐   ┌─────────▼─────────┐   ┌─────────▼──────────┐
│  Local LLM        │   │  GPT-4/GPT-3.5    │   │  Claude 3          │
│  (100% private)   │   │  (Cloud, paid)    │   │  (Cloud, paid)     │
└───────────────────┘   └───────────────────┘   └────────────────────┘
```

### Multi-Provider Support

APK-o-Llama now has an abstracted AI layer supporting three providers:

| Provider | Type | Cost | Privacy | Best For |
|----------|------|------|---------|----------|
| **Ollama** | Local (self-hosted) | Free | 100% private | Sensitive APKs, offline use, no API costs |
| **OpenAI** | Cloud (GPT-4/GPT-3.5) | Pay-per-token | Data sent to OpenAI | Maximum quality, fastest responses |
| **Claude** | Cloud (Claude 3 Sonnet/Haiku) | Pay-per-token | Data sent to Anthropic | Long-form analysis, safety |

**Provider Switching**: Change providers instantly from the Configuration tab. Settings persist across sessions.

## Provider Configuration

APK-o-Llama features a persistent configuration system with UI management for all three AI providers:

### Default Models by Provider

| Provider | Default Model | Notes |
|----------|---------------|-------|
| **Ollama** | `qwen2.5-coder:7b` | Specialized for code analysis |
| **OpenAI** | `gpt-3.5-turbo` | Upgrade to `gpt-4` for better quality |
| **Claude** | `claude-3-sonnet-20240229` | Balanced quality/speed |

### Configuration Options

| Parameter | Default | Range | Provider | Persistence |
|-----------|---------|-------|----------|-------------|
| **Active Provider** | Ollama | Ollama/OpenAI/Claude | All | ✅ Saved |
| **Endpoint URL** | Provider-specific | Any URL | All | ✅ Saved |
| **API Key** | None | Any | OpenAI/Claude | ✅ Saved (❌Stored) |
| **Model Name** | Provider-default | Any supported model | All | ✅ Saved |
| **Connect Timeout** | 17,500ms | Configurable | All | ✅ Saved |
| **Read Timeout** | 52,500ms | Configurable | All | ✅ Saved |
| **Max Tokens** | 2000 | 128 - 4096 | All | ✅ Saved |
| **Entropy Threshold** | 4.5 | 0.0 - 8.0 | N/A | ✅ Saved |
| **Max File Size** | 10 MB | Configurable | N/A | ✅ Saved |
| **Scan Binary Files** | true | Boolean | N/A | ✅ Saved |
| **Entropy Detection** | true | Boolean | N/A | ✅ Saved |
| **Scan Comments** | true | Boolean | N/A | ✅ Saved |
| **Flag String Literals** | false | Boolean | N/A | ✅ Saved |
| **String Literal Severity** | LOW | LOW/MEDIUM/HIGH/INFO | N/A | ✅ Saved |
| **Debug Mode** | false | Boolean | N/A | ✅ Saved |

### Provider-Specific Features

**Ollama**:
- "Test Connection" fetches available models from `/api/tags`
- Model list populates for one-click selection
- Supports any Ollama-compatible model (Llama, Mistral, CodeLlama, etc.)

**OpenAI**:
- API key authentication via Bearer token
- Validates key by calling `/v1/models`
- Supports all GPT models (gpt-3.5-turbo, gpt-4, gpt-4-turbo)

**Claude**:
- API key authentication via `x-api-key` header
- Validates key with format check
- Supports Claude 3 models (Haiku, Sonnet, Opus)

### Version Checking 
- Automatic background check on startup against GitHub API
- "New Releases" button turns **yellow** when update available
- Click to open GitHub releases page
- Status displayed in Configuration tab

## Installation Guide

### Prerequisites

1. **Ollama**: Install and verify Ollama is running
   ```bash
   # Install Ollama (macOS/Linux)
   curl -fsSL https://ollama.com/install.sh | sh
   
   # Start Ollama service
   ollama serve
   
   # Pull recommended model
   ollama pull qwen2.5-coder:7b
   ```

2. **For OpenAI (Cloud, Paid)**: Obtain API key from [OpenAI Platform](https://platform.openai.com/api-keys)

3. **For Claude (Cloud, Paid)**: Obtain API key from [Anthropic Console](https://console.anthropic.com/)

4. **Java**: OpenJDK 21 or higher
   ```bash
   java -version  # Should be 21+
   ```

5. **Burp Suite**: Professional or Community Edition

6. **APK Decompiler**: For standalone usage (jadx, apktool recommended) (Optional)

### Method 1: Pre-compiled Installation (Recommended)

1. **Download**: Get the latest `apk-o-llama.jar` from the [Releases page](https://github.com/VermaOps/apk-o-llama/releases)

2. **Install in Burp**:
   ```bash
   Burp Suite → Extender → Extensions
   Click "Add" → Select "Java" → Choose the JAR file
   ```

3. **Verify Installation**:
   - "APK-o-Llama" tab appears in Burp Suite
   - Check Ollama connection status in your browser at 127.0.0.1:11434

### Method 2: Custom Build Installation

For custom modifications and development:

1. **Clone Repository**:
   ```bash
   git clone https://github.com/VermaOps/apk-o-llama.git
   cd apk-o-llama
   ```

2. **Modify Configuration** (Optional):
   - Edit `ai/AIProviderFactory.java` for custom provider registration
   - Modify `analyzers/SecretScanner.java` for custom regex patterns
   - Adjust `ai/OllamaRequestManager.java` for queue/retry parameters
   - Add new providers by implementing `AIProvider` interface

3. **Build**:
   ```bash
   # Compile with dependencies
   ./build.sh
   ```

4. **Install Custom Build**: Load generated JAR into Burp Suite

### Method 3: Standalone CLI Mode

For users who want to run APK-o-Llama from the command line without Burp Suite, or integrate it into automated CI/CD pipelines. [NOTE: AI features not available in CLI mode]

1. **Download the Standalone JAR** file from release page.

2. **Make it executable** (optional)
   ```bash
   chmod +x apk-o-llama-v1.3.0-standalone.jar
   ```

3. **Create an alias for easy use** (optional)
   ```bash
   # Add to your .bashrc or .zshrc
   alias apk-ollama='java -jar /path/to/apk-o-llama-v1.3.0-standalone.jar'
   ```

#### Usage Examples

**Single APK Analysis**

    ```bash
    # Analyze a decompiled APK directory
    java -jar apk-o-llama-v1.3.0-standalone.jar ./decompiled-apk-folder/

    # With custom output file
    java -jar apk-o-llama-v1.3.0-standalone.jar ./decompiled-apk-folder/ >> results.json
    ```

**Batch Processing Multiple APKs**
  ```bash
  # Process multiple decompiled APK directories
  for apk in ./decompiled/*/; do
    java -jar apk-o-llama-v1.3.0-standalone.jar "$apk"
  done
  ```

**CI/CD Integration (GitHub Actions Example)**
  ```
  - name: Run APK-o-Llama Security Scan
    run: |
      java -jar apk-o-llama-v1.3.0-standalone.jar scan ./app-decompiled/ \
      if [ $? -ne 0 ]; then
        echo "Security issues found!"
        exit 1
      fi
  ```

## Key Features

### Security & Privacy
- 🔒 **100% Local Processing**: All AI analysis runs on your machine via Ollama
- 🌐 **Cloud Options**: OpenAI/Claude for maximum quality (with API keys)
- 🚫 **Zero Data Exfiltration**: No API calls to external services (ollama only)
- 🔐 **No API Keys Required**: Free local LLM, no monthly subscriptions (ollama only)
- 📁 **Offline Capable**: Works completely offline after model download
- 🛡️ **Enterprise-Ready**: Safe for sensitive/confidential APK analysis
- ⚠️ **Configuration persistence** with local file storage
- ❌ **Request cancellation**: to prevent data leaks from stuck processes

### Export Findings
- 📤 **Export findings to CSV**: Open in Excel/Google Sheets for data analysis
- 📊 **Generate professional HTML reports**: Self-contained reports perfect for sharing (AI analysis not included)
- 🎯 **Selective export**: Export only the findings you select (multi-select supported)
- 🎨 **Color-coded severity badges**: Visual indicators in HTML reports with summary breakdown
- 📝 **Custom report titles**: Name your HTML report (defaults to project directory name)
- ⚡ **Background processing**: No UI freezing, exports run smoothly

### Analysis Capabilities
- 📦 **APK Directory Scanning**: Process decompiled APK folder structures
- 🔍 **Multi-Format Support**: Java, Kotlin, Smali, XML, binary files
- 🎯 **Context-Aware Detection**: Pattern + entropy + keyword proximity
- 📊 **Confidence Scoring**: ML-inspired confidence metrics (0-100%)
- 🏷️ **Severity Classification**: CRITICAL, HIGH, MEDIUM, LOW, INFO
- 🔎 **Line-Accurate Reporting**: Exact file and line number identification
- 🧩 **Comment-Aware Filtering**: Configurable comment scanning with severity multiplier
- 📝 **String Literal Detection**: Optional flagging of keywords inside strings

### AI-Powered Reporting
- 🤖 **Multi-Provider Support**: Ollama (local/free), OpenAI (GPT), Claude (Claude 3)
- 📝 **Structured Format**: Summary → Technical Details → Impact → Steps to Reproduce → Mitigation
- 🎓 **Professional Tone**: HackerOne/Bugcrowd style language
- ⚡ **Batch Processing**: Analyze 10+ findings simultaneously
- 🔄 **Smart Retry**: One-click retry for failed/timeout requests
- 📊 **Progress Tracking**: Visual feedback for AI analysis progress
- 🎨 **Formatted Display**: Clean text formatting with proper line wrapping
- 🤖 **AI Console**: Dedicated tab for custom queries with conversation history
- 😺 **Cancellation**: Cancel in-progress AI requests
- 🔥 **Token**: Approximate token counts for cost awareness

### Performance Features
- ⚙️ **Thread-Safe Architecture**: ConcurrentHashMap, AtomicInteger for thread safety
- 📦 **Priority Queueing**: FIFO with creation-time priority
- ⏱️ **Exponential Backoff**: Smart retry delays (3.5s → 7s → 14s)
- 🧹 **Stale Request Monitoring**: Auto-timeout stuck requests (53s + 10s grace)
- 🔄 **Graceful Shutdown**: Proper cleanup of thread pools
- 💓 **Health Check**: Periodic connection verification
- 📈 **Memory Efficient**: Stream-based file processing for large directories
- 🎯 **Cancellation Support**: Immediate cancellation of in-progress requests

## Usage Workflow

### APK Analysis
1. **Manually Decompile Target APK**:
   ```bash
   jadx -d output_dir target.apk
   # or
   apktool d target.apk -o output_dir
   ```

2. **Launch Burp Suite** → Navigate to "APK-o-Llama" tab

3. **Select Decompiled Directory**:
   - Click "Browse" or paste path
   - Select the decompiled APK output directory

4. **Start Analysis**:
   - Click "Analyze" button
   - Progress bar shows scan status
   - Findings populate table with severity coloring

### AI-Assisted Vulnerability Reporting
1. **Configure AI Provider**: Configuration tab >> Select Provider >> Save configuration

2. **Select Findings**: Click row(s) to analyze (multi-select supported)

3. **Generate AI Report**:
   - Click "Ask Ollama" button
   - Each finding receives structured bug bounty report
   - Status column updates in real-time: Pending → In Progress → Completed

4. **View Results**:
   - Click any finding to view details
   - Left panel: Technical details, evidence, confidence
   - Right panel: AI-generated vulnerability report

5. **Retry Failed Requests**:
   - Failed/timeout requests show red "Click to Retry"
   - Single-click to retry with exponential backoff
    
6. **Dedicated AI Console**
   - A dedicated separate AI Console for AI conversation.
   - Switch to "AI Console" tab for custom queries
   - Ask questions about findings or general security topics
   - Cancel long-running console requests
   - Clear response area with confirmation

7. **Cancel In-Progress Requests**:
   - Select findings with Pending/In Progress status
   - Click "Cancel" to abort analysis

### Multi-Finding Batch Processing
- Select 10+ findings simultaneously
- Submit single batch request
- Track progress via progress bar: `AI Analysis: 3/10 (Failed: 1)`
- Retry all failed with one click
- Cancel in-progress requests

### Configuration Management
1. **Navigate to Configuration Tab**
2. **Select AI Provider**:
   - Ollama: Local, free, private
   - OpenAI: Cloud, paid, highest quality
   - Claude: Cloud, paid, safety-focused
3. **Configure Provider Settings**:
   - Enter endpoint URL
   - Enter API key (OpenAI/Claude only)
   - Select model (or test connection to fetch available models)
   - Adjust timeouts and max tokens
4. **Configure Scan Settings**:
   - Entropy threshold, max file size
   - Toggle binary scanning, entropy detection
   - Configure comment scanning and string literal detection
5. **Save Configuration**:
   - Saves settings

### Version Checking
- Automatic background check on startup
- "New Releases" button turns **yellow** when update available
- Click to view latest version and open GitHub releases page

### Result Visualization
| Severity | Color | Icon | Description |
|----------|-------|------|-------------|
| **CRITICAL** | 🔴 Red | █▓▒░ CRITICAL ░▒▓█ | Immediate attention required |
| **HIGH** | 🟠 Orange | ▓▒░ HIGH ░▒▓ | Serious vulnerability |
| **MEDIUM** | 🟡 Yellow | ▒░ MEDIUM ░▒ | Moderate risk |
| **LOW** | 🟢 Green | ░ LOW ░ | Minor issue |

**Confidence Visualization**:
```
██████░░░░ 60% - Potential false positive
████████░░ 80% - Likely valid
██████████ 90%+ - Confirmed
```

### AI Status Indicators 

| Status | Color | Behavior |
|--------|-------|----------|
| **Not Requested** | Default | - |
| **Pending** | White | Waiting in queue |
| **In Progress** | White | Processing |
| **Completed** | White | ✓ Done |
| **Failed** | 🔴 Red | Click to retry |
| **Timeout** | 🔴 Red | Click to retry |
| **Rate Limited** | 🔴 Red | Click to retry |
| **Cancelled** | Gray | User cancelled |

## Screenshots
| | | |
|:---:|:---:|:---:|
| <img width="1470" height="921" alt="1" src="https://github.com/user-attachments/assets/da6e3a10-0157-432c-8ee4-a621ac50de95" /> | <img width="1470" height="924" alt="Screenshot 2026-02-21 at 9 28 35ΓÇ»PM" src="https://github.com/user-attachments/assets/e8a3252e-b5fd-4d97-a434-8e29610bd909" /> | <img width="1470" height="849" alt="Screenshot 2026-05-12 at 9 56 40" src="https://github.com/user-attachments/assets/415a393b-af46-4506-955b-12422d98502b" /> |
|<img width="1470" height="888" alt="Screenshot 2026-02-21 at 9 07 12ΓÇ»PM" src="https://github.com/user-attachments/assets/cf945d69-d25a-47f2-a5c4-df4d7143574b" /> | <img width="1470" height="887" alt="Screenshot 2026-02-21 at 9 07 42ΓÇ»PM" src="https://github.com/user-attachments/assets/385a317e-8c39-48ae-b1ee-7b28d52d3d43" /> | <img width="1470" height="888" alt="Screenshot 2026-02-21 at 9 08 06ΓÇ»PM" src="https://github.com/user-attachments/assets/8344ef8f-abd0-4b0a-b94e-ddc3a80e8fbd" /> |
| <img width="1470" height="924" alt="Screenshot 2026-02-24 at 5 28 02ΓÇ»PM" src="https://github.com/user-attachments/assets/f45c4082-30d9-4a30-bc8b-af611437efab" /> | <img width="1470" height="837" alt="Screenshot 2026-02-24 at 5 28 50ΓÇ»PM" src="https://github.com/user-attachments/assets/c7264aad-c3b3-4664-a7e0-9a945c160936" /> | <img width="1470" height="835" alt="Screenshot 2026-02-24 at 5 29 21ΓÇ»PM" src="https://github.com/user-attachments/assets/606a8aa2-32dd-4fa5-a13d-299d0f81d0d9" /> |



## Support Development

If APK-o-Llama helps your mobile security testing, consider supporting its development:

**⭐ Star the Repository**: Show your support by starring the project on GitHub!

**Support Links**:
- 💰 **PayPal**: [PayPal](https://www.paypal.com/ncp/payment/7Y3836GETVF94)

Your support helps maintain the project, add new analyzers, and improve AI integration.

---

## Report Issues

Found a bug? Have a feature request?

**Bug Reports**:
- Include Burp Suite version
- APK decompiler used (jadx/apktool)
- Ollama version (`ollama --version`)
- Java version
- Steps to reproduce
- Error logs from Burp's Extender → Output/Errors

**Feature Requests**:
- New analyzer suggestions
- Additional regex patterns
- AI prompt improvements
- UI/UX enhancements

## Community & Feedback

APK-o-Llama is built for the mobile security community. Your feedback shapes its future:

- 💡 **Feature Ideas**: What analyzers do you need?
- 🐛 **Bug Reports**: Help make it more stable
- 📚 **Documentation**: What's unclear?
- 🔧 **Contributions**: PRs welcome! (Implement new providers via AIProvider interface)

---

<div align="center">

**Built with ❤️ by [VermaOps](https://github.com/VermaOps)**

[![GitHub Stars](https://img.shields.io/github/stars/VermaOps/apk-o-llama?style=social)](https://github.com/VermaOps/apk-o-llama/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/VermaOps/apk-o-llama)](https://github.com/VermaOps/apk-o-llama/issues)
[![GitHub Forks](https://img.shields.io/github/forks/VermaOps/apk-o-llama?style=social)](https://github.com/VermaOps/apk-o-llama/network/members)

**⭐ Star this repo if you find it useful for mobile security testing!**

</div>
