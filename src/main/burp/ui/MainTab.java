package burp.ui;

import burp.api.montoya.MontoyaApi;
import ai.OllamaProvider;
import ai.OllamaRequestManager;
import ai.OpenAIProvider;
import ai.OllamaRequest;
import core.FindingCollector;
import models.Finding;
import models.ScanResult;
import models.AIStatus;
import rules.RuleEngine;
import scanner.FileScanner;
import scanner.FileType;
import java.util.concurrent.ExecutionException;
import models.Configuration;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

import ai.AIProvider;
import ai.AIProviderFactory;
import ai.AIResponse;
import ai.ClaudeProvider;
import ai.ConversationHistory;
import javax.swing.table.TableRowSorter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

import javax.swing.filechooser.FileNameExtensionFilter;
import models.Severity;  
import java.util.concurrent.CancellationException;
import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import models.VersionManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class MainTab extends JPanel implements OllamaRequestManager.StatusUpdateListener {
    
    private final MontoyaApi api;
    private JTextField directoryField;
    private JButton analyzeButton;
    private JProgressBar progressBar;
    private final JTable findingsTable;
    private final DefaultTableModel tableModel;
    private JTextArea detailsArea;
    private JTextArea rightDetailsArea;  // New: right panel text area
    private JTextArea promptArea;
    private JTextArea responseArea;
    private JButton askAIButton;
    private JButton cancelButton;
    private JButton clearChatButton;  // New button
    private JButton consoleAskButton;
    private JButton consoleCancelButton;
    // NEW: Conversation history
    private ConversationHistory conversationHistory;
    
    // NEW: Reference to releases button for color updates
    private JButton releasesButton;
    
    // Status panel reference for updates
    private JTextArea statusPlaceholder;

    private FindingCollector currentFindings;
    private AIProvider aiProvider;
    private OllamaRequestManager requestManager;
    private Properties configProperties;
    private File configFile;

    // AI Console request tracking
    private OllamaRequest currentConsoleRequest;
    private SwingWorker<String, Void> currentAIWorker;

    // Maps for tracking requests
    private final Map<String, Integer> findingIdToRowMap;
    private final Map<Integer, List<OllamaRequest>> rowToRequestsMap;
    
    // Column indices
    private static final int COL_NUMBER = 0;      // Serial number column
    private static final int COL_SEVERITY = 1;
    private static final int COL_TITLE = 2;
    private static final int COL_CATEGORY = 3;
    private static final int COL_FILE = 4;
    private static final int COL_CONFIDENCE = 5;
    private static final int COL_AI_STATUS = 6;
    
    // Model listing components
    private JList<String> modelList;
    private DefaultListModel<String> modelListModel;
    private JPanel modelPanel;

    public MainTab(MontoyaApi api) {
        this.api = api;
        this.currentFindings = new FindingCollector();
        Configuration config = Configuration.getInstance();
        // Initialize AI provider with error handling
        try {
            this.aiProvider = AIProviderFactory.createProvider(config);
        } catch (Exception e) {
            api.logging().logToError("Failed to create AI provider: " + e.getMessage());
            // Fallback to a default provider or handle gracefully
            // For now, we'll re-throw as RuntimeException to prevent UI from breaking
            throw new RuntimeException("Failed to initialize AI provider: " + e.getMessage(), e);
        }
        this.requestManager = new OllamaRequestManager(aiProvider);
        this.requestManager.addStatusUpdateListener(this);
        
        this.findingIdToRowMap = new HashMap<>();
        this.rowToRequestsMap = new HashMap<>();
        this.conversationHistory = new ConversationHistory();
        
        setLayout(new BorderLayout());
        
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        JPanel findingsPanel = new JPanel(new BorderLayout());
        
                // Updated column names - added "#" column at beginning
        String[] columns = {"#", "Severity", "Title", "Category", "File", "Confidence", "AI Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case COL_NUMBER:
                        return Integer.class;
                    case COL_SEVERITY:
                        return Severity.class;
                    case COL_CONFIDENCE:
                        return Double.class;
                    case COL_AI_STATUS:
                        return String.class;
                    case COL_TITLE:
                    case COL_CATEGORY:
                    case COL_FILE:
                        return String.class;
                    default:
                        return Object.class;
                }
            }
        };
        
        // Perform background version check on startup
        performBackgroundVersionCheck();
        
        // Load saved version check state and update button color
        Configuration config2 = Configuration.getInstance();
        updateReleasesButtonColor(config.isUpdateAvailable());

        findingsTable = new JTable(tableModel);
        findingsTable.setDefaultRenderer(Double.class, new ConfidenceRenderer());
        findingsTable.setDefaultRenderer(Object.class, new AIStatusRenderer());
        findingsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        findingsTable.setRowHeight(25);
        
        // Enable table sorting with custom comparators
        findingsTable.setAutoCreateRowSorter(true);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        findingsTable.setRowSorter(sorter);
        
        // Configure custom comparators for each column
        sorter.setComparator(COL_SEVERITY, new SeverityComparator());
        sorter.setComparator(COL_CONFIDENCE, new ConfidenceComparator());

        // Add custom renderer for AI Status column
        findingsTable.getColumnModel().getColumn(COL_AI_STATUS).setCellRenderer(new AIStatusRenderer());
        
        // Add mouse listener for retry clicks
        findingsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = findingsTable.columnAtPoint(e.getPoint());
                int viewRow = findingsTable.rowAtPoint(e.getPoint());
                
                if (viewRow >= 0 && column == COL_AI_STATUS) {
                    // Convert view index to model index for sorting compatibility
                    int modelRow = findingsTable.convertRowIndexToModel(viewRow);
                    
                    String statusStr = (String) tableModel.getValueAt(modelRow, COL_AI_STATUS);
                    AIStatus aiStatus = getAIStatusFromDisplay(statusStr);
                    
                    if (aiStatus != null && aiStatus.isRetryable()) {
                        retryRowAI(viewRow); // Pass view row for UI operations
                    }
                }
            }
        });
        
        findingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedFinding();
                updateButtonStates();
            }
        });
        
        JScrollPane tableScroll = new JScrollPane(findingsTable);
        
        // Left details area (existing functionality)
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        JScrollPane leftDetailsScroll = new JScrollPane(detailsArea);

        // Right details area (reserved for future features)
        rightDetailsArea = new JTextArea();
        rightDetailsArea.setEditable(false);
        rightDetailsArea.setLineWrap(true);
        rightDetailsArea.setWrapStyleWord(true);
        rightDetailsArea.setText("");  // Empty placeholder
        JScrollPane rightDetailsScroll = new JScrollPane(rightDetailsArea);

        // Create inner split pane for left/right details areas
        JSplitPane detailsSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                                    leftDetailsScroll, rightDetailsScroll);
        detailsSplitPane.setDividerLocation(0.25);  // Split 25% to left
        detailsSplitPane.setResizeWeight(0.25);     // Maintain 25% proportions
        detailsSplitPane.setOneTouchExpandable(false); // Keep simple

        // Main split pane (table above, details split below)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                                tableScroll, detailsSplitPane);
        mainSplitPane.setDividerLocation(200);
        
        findingsPanel.add(mainSplitPane, BorderLayout.CENTER);
        
        // Add button panel below findings
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        askAIButton = new JButton("Ask Ollama");
        askAIButton.addActionListener(e -> askAIForSelected());
        buttonPanel.add(askAIButton);
        
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancelSelectedAI());
        cancelButton.setEnabled(false);
        buttonPanel.add(cancelButton);
        
        // NEW: Export button - always enabled
        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportFindings());
        exportButton.setEnabled(true); // Always enabled as per requirement
        buttonPanel.add(exportButton);

        JPanel findingsContainer = new JPanel(new BorderLayout());
        findingsContainer.add(findingsPanel, BorderLayout.CENTER);
        findingsContainer.add(buttonPanel, BorderLayout.SOUTH);
        
        tabbedPane.addTab("Findings", findingsContainer);
        
        JPanel promptPanel = createPromptPanel();
        tabbedPane.addTab("AI Console", promptPanel);
        
        // Adding Configuration tab
        JPanel configPanel = createConfigurationPanel();
        tabbedPane.addTab("Configuration", configPanel);

        add(tabbedPane, BorderLayout.CENTER);
        
        // Set column widths
        TableColumnModel columnModel = findingsTable.getColumnModel();
        
        // Make "#" column as narrow as possible
        columnModel.getColumn(COL_NUMBER).setMinWidth(25);
        columnModel.getColumn(COL_NUMBER).setMaxWidth(35);
        columnModel.getColumn(COL_NUMBER).setPreferredWidth(30);
        columnModel.getColumn(COL_NUMBER).setResizable(false);

        columnModel.getColumn(COL_SEVERITY).setPreferredWidth(80);
        columnModel.getColumn(COL_TITLE).setPreferredWidth(250);
        columnModel.getColumn(COL_CATEGORY).setPreferredWidth(100);
        columnModel.getColumn(COL_FILE).setPreferredWidth(150);
        columnModel.getColumn(COL_CONFIDENCE).setPreferredWidth(80);
        columnModel.getColumn(COL_AI_STATUS).setPreferredWidth(120);
    }
    
    private class ConfidenceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            // Convert table row index to model row index for sorting
            int modelRow = table.convertRowIndexToModel(row);
            
            Component c = super.getTableCellRendererComponent(table, value, 
                isSelected, hasFocus, modelRow, column);
            
            // Always set confidence text to white
            c.setForeground(Color.WHITE);
            
            // Format as percentage
            if (value instanceof Double) {
                double confidence = (Double) value;
                setText(String.format("%.0f%%", confidence * 100));
            }
            
            return c;
        }
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Left panel with all existing components
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        leftPanel.add(new JLabel("APK Directory:"));
        
        directoryField = new JTextField(40);
        leftPanel.add(directoryField);
        
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseForDirectory());
        leftPanel.add(browseButton);
        
        analyzeButton = new JButton("Analyze");
        analyzeButton.addActionListener(e -> startAnalysis());
        leftPanel.add(analyzeButton);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 25));
        leftPanel.add(progressBar);
        
        // Right panel with Support Development button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        // Add New Releases button first (will appear left of Support Development due to FlowLayout.RIGHT)
        releasesButton = new JButton("New releases");
        releasesButton.addActionListener(e -> handleNewReleasesClick());
        rightPanel.add(releasesButton);

        JButton githubButton = new JButton("GitHub");
        githubButton.addActionListener(e -> {
            // Open GitHub support page in default browser
            try {
                String url = "https://github.com/VermaOps/";
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(new java.net.URI(url));
                } else {
                    // Fallback for systems where Desktop.browse is not supported
                    showBrowserError(MainTab.this, "Desktop browse action not supported on this system.");
                }
            } catch (java.net.URISyntaxException ex) {
                // This should never happen with the hardcoded URL, but handle gracefully
                showBrowserError(MainTab.this, "Invalid URL format.");
            } catch (java.io.IOException ex) {
                // Browser couldn't be opened
                showBrowserError(MainTab.this, "Browser couldn't be opened. Please check your system settings.");
            }
        });
        rightPanel.add(githubButton);
        
        // Add both panels to main panel
        panel.add(leftPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void showBrowserError(Component parent, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                parent != null ? parent : MainTab.this,
                message,
                "Browser Error",
                JOptionPane.WARNING_MESSAGE
            );
        });
    }

    /**
     * Handles click on New Releases button
     */
    private void handleNewReleasesClick() {
        // Switch to Configuration tab and get reference
        JTabbedPane tabbedPane = null;
        for (java.awt.Component comp : this.getComponents()) {
            if (comp instanceof JTabbedPane) {
                tabbedPane = (JTabbedPane) comp;
                // Find Configuration tab (index 2)
                tabbedPane.setSelectedIndex(2);
                break;
            }
        }
        
        // Get configuration
        Configuration config = Configuration.getInstance();
        
        // Try to find status area if not already referenced or if reference is stale
        JTextArea targetStatusArea = this.statusPlaceholder;
        if (targetStatusArea == null && tabbedPane != null) {
            // Try to find status area in the Configuration tab
            java.awt.Component configPanel = tabbedPane.getComponentAt(2);
            if (configPanel instanceof JPanel) {
                targetStatusArea = findStatusAreaInComponent((JPanel) configPanel);
                if (targetStatusArea != null) {
                    this.statusPlaceholder = targetStatusArea;
                }
            }
        }
        
        // Update status panel with checking message
        if (targetStatusArea != null) {
            targetStatusArea.setText("System Status Information\n\n" +
                                    "• Checking for new version on GitHub...\n" +
                                    "• Please wait...\n\nAuthor: VermaOps | GitHub");
        } else {
            // Fallback: log to Burp output
            api.logging().logToOutput("Status panel not found, but version check continues in background");
        }
        
        // Store final reference for inner class
        final JTabbedPane finalTabbedPane = tabbedPane;
        
        // Perform version check in background
        SwingWorker<VersionCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected VersionCheckResult doInBackground() {
                return checkForNewVersion();
            }
            
            @Override
            protected void done() {
                try {
                    VersionCheckResult result = get();
                    Configuration config = Configuration.getInstance();
                    
                    // Find status area again (may have changed or been created after tab switch)
                    JTextArea finalStatusArea = MainTab.this.statusPlaceholder;
                    if (finalStatusArea == null && finalTabbedPane != null) {
                        java.awt.Component configPanel = finalTabbedPane.getComponentAt(2);
                        if (configPanel instanceof JPanel) {
                            finalStatusArea = findStatusAreaInComponent((JPanel) configPanel);
                            if (finalStatusArea != null) {
                                MainTab.this.statusPlaceholder = finalStatusArea;
                            }
                        }
                    }
                    
                    if (result.error != null) {
                        // Error occurred
                        if (finalStatusArea != null) {
                            finalStatusArea.setText("System Status Information\n\n" +
                                                    "• Version Check: FAILED\n" +
                                                    "• Error: " + result.error + "\n" +
                                                    "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: VermaOps | GitHub");
                        }
                        return;
                    }
                    
                    if (result.updateAvailable) {
                        // Update available
                        config.setUpdateAvailable(true);
                        config.setLatestVersion(result.latestVersion);
                        config.setLastVersionCheckTime(System.currentTimeMillis());
                        config.setVersionCheckError(null);
                        config.saveToFile();
                        
                        // Update button color
                        updateReleasesButtonColor(true);
                        
                        // Show in status panel
                        if (finalStatusArea != null) {
                            finalStatusArea.setText("System Status Information\n\n" +
                                                    "• New version available: " + result.latestVersion + "\n" +
                                                    "• Opening GitHub release page...\n" +
                                                    "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: VermaOps | GitHub");
                        }
                        
                        // Open GitHub releases page in browser
                        openGitHubReleasesPage();
                        
                    } else {
                        // No update available
                        config.setUpdateAvailable(false);
                        config.setLatestVersion(result.latestVersion);
                        config.setLastVersionCheckTime(System.currentTimeMillis());
                        config.setVersionCheckError(null);
                        config.saveToFile();
                        
                        // Update button color
                        updateReleasesButtonColor(false);
                        
                        // Show in status panel
                        if (finalStatusArea != null) {
                            finalStatusArea.setText("System Status Information\n\n" +
                                                    "• No new releases found.\n" +
                                                    "• You are already using the latest version: " + 
                                                    VersionManager.getCurrentVersion() + "\n" +
                                                    "• Latest on GitHub: " + result.latestVersion + "\n" +
                                                    "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: VermaOps | GitHub");
                        }
                    }
                    
                } catch (Exception e) {
                    // Find status area one more time for error case
                    JTextArea errorStatusArea = MainTab.this.statusPlaceholder;
                    if (errorStatusArea == null && finalTabbedPane != null) {
                        java.awt.Component configPanel = finalTabbedPane.getComponentAt(2);
                        if (configPanel instanceof JPanel) {
                            errorStatusArea = findStatusAreaInComponent((JPanel) configPanel);
                        }
                    }
                    if (errorStatusArea != null) {
                        errorStatusArea.setText("System Status Information\n\n" +
                                                "• Version Check: FAILED\n" +
                                                "• Error: " + e.getMessage() + "\n" +
                                                "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: VermaOps | GitHub");
                    }
                }
            }
        };
        worker.execute();
    }

    /**
     * Recursively searches for a JTextArea that looks like the status panel
     */
    private JTextArea findStatusAreaInComponent(java.awt.Component component) {
        if (component == null) return null;
        
        // Direct match
        if (component instanceof JTextArea) {
            JTextArea textArea = (JTextArea) component;
            String text = textArea.getText();
            if (text != null && text.contains("System Status Information")) {
                return textArea;
            }
        }
        
        // Search children
        if (component instanceof javax.swing.JPanel) {
            javax.swing.JPanel panel = (javax.swing.JPanel) component;
            for (java.awt.Component child : panel.getComponents()) {
                JTextArea result = findStatusAreaInComponent(child);
                if (result != null) return result;
            }
        } else if (component instanceof javax.swing.JScrollPane) {
            javax.swing.JScrollPane scrollPane = (javax.swing.JScrollPane) component;
            JTextArea result = findStatusAreaInComponent(scrollPane.getViewport().getView());
            if (result != null) return result;
        } else if (component instanceof javax.swing.JTabbedPane) {
            javax.swing.JTabbedPane tabbedPane = (javax.swing.JTabbedPane) component;
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                JTextArea result = findStatusAreaInComponent(tabbedPane.getComponentAt(i));
                if (result != null) return result;
            }
        }
        
        return null;
    }

    /**
    * Opens GitHub releases page in default browser
    */
    private void openGitHubReleasesPage() {
        try {
            String url = "https://github.com/VermaOps/apk-o-llama/releases";
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(new java.net.URI(url));
            } else {
                showBrowserError(MainTab.this, "Desktop browse action not supported on this system.");
            }
        } catch (Exception ex) {
            showBrowserError(MainTab.this, "Could not open browser: " + ex.getMessage());
        }
    }

    /**
    * Updates the New Releases button color based on update availability
    */
    private void updateReleasesButtonColor(boolean updateAvailable) {
        SwingUtilities.invokeLater(() -> {
            if (releasesButton != null) {
                if (updateAvailable) {
                    releasesButton.setBackground(Color.YELLOW); // Yellow
                    releasesButton.setOpaque(true);
                    releasesButton.setBorderPainted(false);
                } else {
                    releasesButton.setBackground(null);
                    releasesButton.setOpaque(false);
                    releasesButton.setBorderPainted(true);
                }
            }
        });
    }

    /**
    * Simple class to hold version check results
    */
    private static class VersionCheckResult {
        String latestVersion;
        boolean updateAvailable;
        String error;
        
        VersionCheckResult(String latestVersion, boolean updateAvailable, String error) {
            this.latestVersion = latestVersion;
            this.updateAvailable = updateAvailable;
            this.error = error;
        }
    }

    /**
    * Checks GitHub for the latest release version
    */
    private VersionCheckResult checkForNewVersion() {
        String currentVersion = VersionManager.getCurrentVersion();
        
        try {
            // GitHub API URL for releases
            URL url = new URI("https://api.github.com/repos/VermaOps/apk-o-llama/releases/latest").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000); // 10 second timeout
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Parse JSON to get tag_name
                String json = response.toString();
                String tagName = extractTagName(json);
                
                if (tagName != null) {
                    // Remove 'v' prefix if present
                    String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    
                    // Compare versions
                    boolean updateAvailable = compareVersions(latestVersion, currentVersion) > 0;
                    
                    return new VersionCheckResult(latestVersion, updateAvailable, null);
                } else {
                    return new VersionCheckResult(null, false, "Could not parse version from GitHub response");
                }
                
            } else if (responseCode == 403) {
                // Rate limited
                return new VersionCheckResult(null, false, "GitHub API rate limit exceeded. Try again later.");
            } else {
                return new VersionCheckResult(null, false, "GitHub returned error code: " + responseCode);
            }
            
        } catch (java.net.UnknownHostException e) {
            return new VersionCheckResult(null, false, "No internet connection. GitHub unreachable.");
        } catch (java.net.SocketTimeoutException e) {
            return new VersionCheckResult(null, false, "Connection timeout. GitHub is slow or unreachable.");
        } catch (Exception e) {
            return new VersionCheckResult(null, false, "Error checking for updates: " + e.getMessage());
        }
    }

    /**
    * Extracts tag_name from GitHub API JSON response
    */
    private String extractTagName(String json) {
        try {
            Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Fallback to simple approach
        }
        return null;
    }

    /**
     * Compares two version strings
     * Returns: 
     *   positive if v1 > v2
     *   zero if v1 == v2
     *   negative if v1 < v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private JPanel createPromptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel label = new JLabel("Ask AI about the findings:");
        panel.add(label, BorderLayout.NORTH);
        
        promptArea = new JTextArea(3, 40);
        promptArea.setLineWrap(true);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        panel.add(promptScroll, BorderLayout.CENTER);
        
        // CHANGE: Store references to class fields instead of local variables
        consoleAskButton = new JButton("Ask AI");
        consoleAskButton.addActionListener(e -> askAI());
        
        consoleCancelButton = new JButton("Cancel");
        consoleCancelButton.addActionListener(e -> cancelAIConsoleRequest());
        consoleCancelButton.setEnabled(false); // Initially disabled

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(consoleAskButton);
        buttonPanel.add(consoleCancelButton);

        // Add Clear button - always enabled
        JButton consoleClearButton = new JButton("Clear");
        consoleClearButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                MainTab.this,
                "LLM response will be cleared. The prompt input will be preserved.\nDo you want to continue?",
                "Clear Response",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (choice == JOptionPane.YES_OPTION) {
                clearAIResponse();
            }
        });
        consoleClearButton.setEnabled(true); // Always enabled as per requirement
        buttonPanel.add(consoleClearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        responseArea = new JTextArea();  // Keep as responseArea
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setText("Click \"Ask AI\" to start analysis with Ollama\n");
        JScrollPane responseScroll = new JScrollPane(responseArea);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
                                            panel, responseScroll);
        splitPane.setDividerLocation(250);
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(splitPane, BorderLayout.CENTER);
        
        return wrapper;
    }

    private JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        Configuration config = Configuration.getInstance();
        
        JPanel configContent = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        int row = 0;
        
        // ========== AI PROVIDER CONFIGURATION SECTION ==========
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel providerLabel = new JLabel("AI Provider Configuration");
        providerLabel.setFont(providerLabel.getFont().deriveFont(Font.BOLD, 14f));
        configContent.add(providerLabel, gbc);
        
        // Provider dropdown
        gbc.gridwidth = 1;
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Provider:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> providerCombo = new JComboBox<>(new String[]{"Ollama", "OpenAI", "Claude"});
        String currentProvider = config.getActiveProvider();
        if ("openai".equalsIgnoreCase(currentProvider)) {
            providerCombo.setSelectedItem("OpenAI");
        } else if ("claude".equalsIgnoreCase(currentProvider)) {
            providerCombo.setSelectedItem("Claude");
        } else {
            providerCombo.setSelectedItem("Ollama");
        }
        configContent.add(providerCombo, gbc);
        
        // Endpoint URL field
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Endpoint URL:"), gbc);
        gbc.gridx = 1;
        JTextField endpointField = new JTextField(getDefaultEndpointForProvider((String) providerCombo.getSelectedItem()), 40);
        configContent.add(endpointField, gbc);
        
        // API Key field
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1;
        JPasswordField apiKeyField = new JPasswordField(40);
        apiKeyField.setEnabled(!"Ollama".equals(providerCombo.getSelectedItem()));
        updateApiKeyFieldValue(apiKeyField, (String) providerCombo.getSelectedItem(), config);
        configContent.add(apiKeyField, gbc);
        
        // Model field (manual input)
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1;
        JTextField modelField = new JTextField(getDefaultModelForProvider((String) providerCombo.getSelectedItem(), config), 40);
        configContent.add(modelField, gbc);
        
        // Test Connection button
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JButton testButton = new JButton("Test Connection");
        JLabel testResultLabel = new JLabel(" ");
        testResultLabel.setForeground(new Color(0, 150, 0));
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.add(testButton);
        testPanel.add(testResultLabel);
        configContent.add(testPanel, gbc);
        
        // System Status and Available Models panel (side by side)
        gbc.gridy = row++;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.4;
        
        JPanel splitStatusPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        splitStatusPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "System Status",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION
        ));
        
        // Left side - Available Models (only for Ollama)
        JPanel modelsPanel = new JPanel(new BorderLayout());
        modelsPanel.setBorder(BorderFactory.createTitledBorder("Available Models"));
        
        DefaultListModel<String> modelListModel = new DefaultListModel<>();
        JList<String> modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelList.setVisibleRowCount(5);
        modelList.setEnabled(false);

        // Add mouse listener to populate selected model into model field
        modelList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selectedModel = modelList.getSelectedValue();
                if (selectedModel != null && modelField != null) {
                    modelField.setText(selectedModel);
                    modelField.setEditable(true);
                }
            }
        });
        
        JScrollPane modelScrollPane = new JScrollPane(modelList);
        modelsPanel.add(modelScrollPane, BorderLayout.CENTER);
        
        JLabel modelNoteLabel = new JLabel("Models appear here after successful connection test");
        modelNoteLabel.setFont(modelNoteLabel.getFont().deriveFont(Font.ITALIC, 11f));
        modelNoteLabel.setForeground(Color.GRAY);
        modelNoteLabel.setHorizontalAlignment(JLabel.CENTER);
        modelsPanel.add(modelNoteLabel, BorderLayout.SOUTH);
        
        // Right side - Status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        
        JTextArea statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setText("System Status Information\n\n• Connection: Not Tested\n• Last Check: Never\n\nSelect provider and click 'Test Connection'");
        statusArea.setMargin(new Insets(10, 10, 10, 10));
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        // Store reference to class field for later updates
        this.statusPlaceholder = statusArea;

        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusPanel.add(statusScroll, BorderLayout.CENTER);
        
        splitStatusPanel.add(modelsPanel);
        splitStatusPanel.add(statusPanel);
        configContent.add(splitStatusPanel, gbc);
        
        // Reset weight
        gbc.weighty = 0;
        
        // Store references for test connection
        endpointField.putClientProperty("modelList", modelList);
        endpointField.putClientProperty("modelListModel", modelListModel);
        endpointField.putClientProperty("modelNoteLabel", modelNoteLabel);
        endpointField.putClientProperty("statusArea", statusArea);
        
        // ========== PROVIDER CHANGE LISTENER ==========
        providerCombo.addActionListener(e -> {
            String selected = (String) providerCombo.getSelectedItem();
            
            endpointField.setText(getDefaultEndpointForProvider(selected));
            apiKeyField.setText("");
            apiKeyField.setEnabled(!"Ollama".equals(selected));
            modelField.setText(getDefaultModelForProvider(selected, config));
            
            // Clear models panel and disable for non-Ollama providers
            modelListModel.clear();
            modelList.setEnabled(false);
            if (!"Ollama".equals(selected)) {
                modelNoteLabel.setText("Available models only for Ollama provider");
                modelNoteLabel.setForeground(Color.GRAY);
            } else {
                modelNoteLabel.setText("Models appear here after successful connection test");
                modelNoteLabel.setForeground(Color.GRAY);
            }
            
            statusArea.setText("System Status Information\n\n• Connection: Not Tested\n• Last Check: Never\n\nSelect provider and click 'Test Connection'");
            testResultLabel.setText(" ");
        });
        
        // ========== TEST CONNECTION ACTION ==========
        testButton.addActionListener(e -> {
            String selected = (String) providerCombo.getSelectedItem();
            String endpoint = endpointField.getText().trim();
            String model = modelField.getText().trim();
            String apiKey = new String(apiKeyField.getPassword());
            
            testResultLabel.setText("Testing...");
            testResultLabel.setForeground(Color.BLACK);
            statusArea.setText("Testing connection to " + selected + "...\nPlease wait...");
            testButton.setEnabled(false);
            
            // Clear models
            if ("Ollama".equals(selected)) {
                modelListModel.clear();
                modelList.setEnabled(false);
                modelNoteLabel.setText("Fetching models...");
            }
            
            // Run in background thread
            new Thread(() -> {
                boolean connected = false;
                String error = null;
                List<String> models = new ArrayList<>();
                
                try {
                    // Test connection based on provider
                    if ("Ollama".equals(selected)) {
                        // Check if Ollama is running
                        java.net.URL url = new java.net.URI(endpoint + "/api/tags").toURL();
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        int responseCode = conn.getResponseCode();
                        conn.disconnect();
                        
                        if (responseCode == 200) {
                            connected = true;
                            // Fetch models
                            models = fetchModels(endpoint);
                        } else {
                            error = "Ollama returned status " + responseCode;
                        }
                    } else if ("OpenAI".equals(selected)) {
                        if (apiKey == null || apiKey.isEmpty()) {
                            error = "API Key is required";
                        } else {
                            java.net.URL url = new java.net.URI(endpoint + "/v1/models").toURL();
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                            conn.setConnectTimeout(5000);
                            conn.setReadTimeout(5000);
                            int responseCode = conn.getResponseCode();
                            conn.disconnect();
                            
                            if (responseCode == 200) {
                                connected = true;
                                models = fetchOpenAIModels(apiKey, endpoint);
                            } else {
                                error = "Invalid API Key or endpoint";
                            }
                        }
                    } else if ("Claude".equals(selected)) {
                        if (apiKey == null || apiKey.isEmpty()) {
                            error = "API Key is required";
                        } else {
                            try {
                                // Use GET to /v1/models (returns 404 but validates API key format)
                                java.net.URL url = new java.net.URI(endpoint + "/v1/models").toURL();
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("GET");
                                conn.setRequestProperty("x-api-key", apiKey);
                                conn.setRequestProperty("anthropic-version", "2023-06-01");
                                conn.setConnectTimeout(5000);
                                conn.setReadTimeout(5000);
                                
                                int responseCode = conn.getResponseCode();
                                conn.disconnect();
                                
                                // Claude returns 404 for /v1/models, but that means the API key is valid
                                // (invalid keys return 401)
                                if (responseCode == 200) {
                                    connected = true;
                                    models = fetchClaudeModels(apiKey, endpoint);
                                } else if (responseCode == 401) {
                                    error = "Invalid API Key";
                                } else {
                                    error = "Connection failed (code: " + responseCode + ")";
                                }
                            } catch (Exception ex) {
                                error = ex.getMessage();
                            }
                        }
                    }
                    
                } catch (java.net.ConnectException ex) {
                    error = "Cannot connect to " + endpoint + " - Service not running";
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                
                // Update UI on EDT
                final boolean finalConnected = connected;
                final String finalError = error;
                final List<String> finalModels = models;
                
                javax.swing.SwingUtilities.invokeLater(() -> {
                    String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    
                    if (finalError != null) {
                        testResultLabel.setText("✗ Error: " + finalError);
                        testResultLabel.setForeground(Color.RED);
                        statusArea.setText("System Status Information\n\n" +
                            "• Provider: " + selected + "\n" +
                            "• Connection: ✗ Failed\n" +
                            "• Error: " + finalError + "\n" +
                            "• Last Check: " + currentTime + "\n\n" +
                            "Author: VermaOps | GitHub");
                        if ("Ollama".equals(selected)) {
                            modelNoteLabel.setText("Failed to fetch models");
                            modelNoteLabel.setForeground(Color.RED);
                        }
                    } else if (finalConnected) {
                        testResultLabel.setText("✓ Connection successful!");
                        testResultLabel.setForeground(new Color(0, 150, 0));
                        statusArea.setText("System Status Information\n\n" +
                            "• Provider: " + selected + "\n" +
                            "• Connection: ✓ Connected\n" +
                            "• Endpoint: " + endpoint + "\n" +
                            "• Model: " + model + "\n" +
                            "• Last Check: " + currentTime + "\n\n" +
                            "Author: VermaOps | GitHub");
                        
                        if (finalModels != null && !finalModels.isEmpty()) {
                            for (String m : finalModels) {
                                modelListModel.addElement(m);
                            }
                            modelList.setEnabled(true);
                            modelNoteLabel.setText(finalModels.size() + " model(s) available - Click to populate");
                            modelNoteLabel.setForeground(new Color(0, 100, 0));
                        } else if ("Ollama".equals(selected)) {
                            modelNoteLabel.setText("No models installed. Run 'ollama pull <model>'");
                            modelNoteLabel.setForeground(new Color(200, 100, 0));
                        }
                    } else {
                        testResultLabel.setText("✗ Connection failed");
                        testResultLabel.setForeground(Color.RED);
                        statusArea.setText("System Status Information\n\n" +
                            "• Provider: " + selected + "\n" +
                            "• Connection: ✗ Failed\n" +
                            "• Endpoint: " + endpoint + "\n" +
                            "• Last Check: " + currentTime + "\n\n" +
                            "Author: VermaOps | GitHub");
                        if ("Ollama".equals(selected)) {
                            modelNoteLabel.setText("Connect to see available models");
                            modelNoteLabel.setForeground(Color.GRAY);
                        }
                    }
                    
                    testButton.setEnabled(true);
                });
            }).start();
        });
        
        // ========== NEW TWO-COLUMN ROW: SCAN CONFIGURATION | ADVANCED SETTINGS ==========
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.3;
        
        JPanel twoColumnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        
        // LEFT COLUMN: Scan Configuration
        JPanel scanConfigPanel = new JPanel(new GridBagLayout());
        scanConfigPanel.setBorder(BorderFactory.createTitledBorder("Scan Configuration"));
        GridBagConstraints scanGbc = new GridBagConstraints();
        scanGbc.fill = GridBagConstraints.HORIZONTAL;
        scanGbc.insets = new Insets(5, 5, 5, 5);
        scanGbc.gridx = 0;
        scanGbc.gridy = 0;
        scanGbc.gridwidth = 1;
        
        // Scan binary files
        JCheckBox scanBinaryCheck = new JCheckBox("Scan binary files", config.isScanBinaryFiles());
        scanConfigPanel.add(scanBinaryCheck, scanGbc);
        scanGbc.gridy++;
        
        // Enable entropy detection
        JCheckBox entropyCheck = new JCheckBox("Enable entropy-based detection", config.isEntropyDetectionEnabled());
        scanConfigPanel.add(entropyCheck, scanGbc);
        scanGbc.gridy++;
        
        // Scan comments for secrets
        JCheckBox scanCommentsCheck = new JCheckBox("Scan comments for secrets", config.isScanComments());
        scanConfigPanel.add(scanCommentsCheck, scanGbc);
        scanGbc.gridy++;
        
        // Flag keywords inside string literals
        JCheckBox flagStringLiteralsCheck = new JCheckBox("Flag keywords inside string literals", config.isFlagStringLiterals());
        scanConfigPanel.add(flagStringLiteralsCheck, scanGbc);
        scanGbc.gridy++;
        
        // String literal severity dropdown
        JPanel severityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        severityPanel.add(new JLabel("String literal severity:"));
        JComboBox<String> severityCombo = new JComboBox<>(new String[]{"LOW", "MEDIUM", "HIGH", "INFO"});
        severityCombo.setSelectedItem(config.getStringLiteralSeverity().getDisplayName());
        severityPanel.add(severityCombo);
        scanConfigPanel.add(severityPanel, scanGbc);
        scanGbc.gridy++;
        
        // Debug mode
        JCheckBox debugModeCheck = new JCheckBox("Debug mode (verbose output)", config.isDebugMode());
        scanConfigPanel.add(debugModeCheck, scanGbc);
        
        // RIGHT COLUMN: Advanced Settings
        JPanel advancedPanel = new JPanel(new GridBagLayout());
        advancedPanel.setBorder(BorderFactory.createTitledBorder("Advanced Settings"));
        GridBagConstraints advGbc = new GridBagConstraints();
        advGbc.fill = GridBagConstraints.HORIZONTAL;
        advGbc.insets = new Insets(5, 5, 5, 5);
        advGbc.gridx = 0;
        advGbc.gridy = 0;
        advGbc.gridwidth = 1;
        
        // Max File Size
        advGbc.gridx = 0;
        advancedPanel.add(new JLabel("Max File Size (MB):"), advGbc);
        advGbc.gridx = 1;
        JTextField maxSizeField = new JTextField(String.valueOf(config.getMaxFileSizeMB()), 10);
        advancedPanel.add(maxSizeField, advGbc);
        advGbc.gridy++;
        
        // Entropy Threshold
        advGbc.gridx = 0;
        advancedPanel.add(new JLabel("Entropy Threshold:"), advGbc);
        advGbc.gridx = 1;
        JTextField entropyField = new JTextField(String.valueOf(config.getEntropyThreshold()), 10);
        advancedPanel.add(entropyField, advGbc);
        advGbc.gridy++;
        
        // Max Tokens
        advGbc.gridx = 0;
        advancedPanel.add(new JLabel("Max Tokens:"), advGbc);
        advGbc.gridx = 1;
        JTextField maxTokensField = new JTextField(String.valueOf(config.getMaxTokens()), 10);
        advancedPanel.add(maxTokensField, advGbc);
        advGbc.gridy++;
        
        // Connect Timeout
        advGbc.gridx = 0;
        advancedPanel.add(new JLabel("Connect Timeout (ms):"), advGbc);
        advGbc.gridx = 1;
        JTextField connectTimeoutField = new JTextField(String.valueOf(config.getConnectTimeout()), 10);
        advancedPanel.add(connectTimeoutField, advGbc);
        advGbc.gridy++;
        
        // Read Timeout
        advGbc.gridx = 0;
        advancedPanel.add(new JLabel("Read Timeout (ms):"), advGbc);
        advGbc.gridx = 1;
        JTextField readTimeoutField = new JTextField(String.valueOf(config.getReadTimeout()), 10);
        advancedPanel.add(readTimeoutField, advGbc);
        
        twoColumnRow.add(scanConfigPanel);
        twoColumnRow.add(advancedPanel);
        configContent.add(twoColumnRow, gbc);
        
        // Reset weight
        gbc.weighty = 0;
        
        // ========== SAVE BUTTON ==========
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 5, 5, 5);
        JButton saveButton = new JButton("Save Configuration");
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        configContent.add(saveButton, gbc);
        
        saveButton.addActionListener(e -> {
            try {
                String selected = (String) providerCombo.getSelectedItem();
                String endpoint = endpointField.getText().trim();
                String apiKey = new String(apiKeyField.getPassword());
                String model = modelField.getText().trim();
                
                // Save provider selection
                if ("OpenAI".equals(selected)) {
                    config.setActiveProvider("openai");
                } else if ("Claude".equals(selected)) {
                    config.setActiveProvider("claude");
                } else {
                    config.setActiveProvider("ollama");
                }
                
                // Save endpoint
                if ("ollama".equals(config.getActiveProvider())) {
                    config.setOllamaEndpoint(endpoint);
                } else if ("openai".equals(config.getActiveProvider())) {
                    config.setOpenAIBaseUrl(endpoint);
                } else if ("claude".equals(config.getActiveProvider())) {
                    config.setClaudeBaseUrl(endpoint);
                }
                
                // Save API key
                if ("openai".equals(config.getActiveProvider())) {
                    config.setOpenAIKey(apiKey);
                } else if ("claude".equals(config.getActiveProvider())) {
                    config.setClaudeKey(apiKey);
                } else {
                    config.setOllamaApiKey(apiKey);
                }
                
                // Save model
                config.setOllamaModel(model);
                config.setOpenAIModel(model);
                config.setClaudeModel(model);
                
                // Save scan settings
                config.setMaxFileSizeMB(Integer.parseInt(maxSizeField.getText().trim()));
                config.setEntropyThreshold(Double.parseDouble(entropyField.getText().trim()));
                config.setScanBinaryFiles(scanBinaryCheck.isSelected());
                config.setEntropyDetectionEnabled(entropyCheck.isSelected());
                config.setScanComments(scanCommentsCheck.isSelected());
                config.setFlagStringLiterals(flagStringLiteralsCheck.isSelected());
                config.setStringLiteralSeverity((String) severityCombo.getSelectedItem());
                config.setDebugMode(debugModeCheck.isSelected());
                
                // Save advanced settings
                config.setMaxTokens(Integer.parseInt(maxTokensField.getText().trim()));
                config.setConnectTimeout(Integer.parseInt(connectTimeoutField.getText().trim()));
                config.setReadTimeout(Integer.parseInt(readTimeoutField.getText().trim()));
                
                config.saveToFile();
                
                // Recreate AI provider
                aiProvider = AIProviderFactory.createProvider(config);
                requestManager = new OllamaRequestManager(aiProvider);
                requestManager.addStatusUpdateListener(MainTab.this);
                
                // Update Status panel with the saved model information
                if (statusArea != null) {
                    String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String providerName = (String) providerCombo.getSelectedItem();
                    // Using existing 'endpoint' variable declared above - NO NEW DECLARATION
                    String savedModel = modelField.getText().trim();
                    
                    statusArea.setText("System Status Information\n\n" +
                        "• Provider: " + providerName + "\n" +
                        "• Connection: ✓ Configured\n" +
                        "• Endpoint: " + endpoint + "\n" +
                        "• Model: " + savedModel + "\n" +
                        "• Status: Ready for AI calls\n" +
                        "• Last Saved: " + currentTime + "\n\n" +
                        "Click 'Test Connection' to verify connectivity.\n\nAuthor: VermaOps | GitHub");
                }
                
                JOptionPane.showMessageDialog(panel, 
                    "Configuration saved successfully!\nSettings will apply to next scan and AI requests.", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, 
                    "Invalid number format: " + ex.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, 
                    "Error saving configuration: " + ex.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(configContent);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private String getDefaultEndpointForProvider(String provider) {
        switch (provider) {
            case "OpenAI": return "https://api.openai.com";
            case "Claude": return "https://api.anthropic.com";
            default: return "http://localhost:11434";
        }
    }

    private String getDefaultModelForProvider(String provider, Configuration config) {
        switch (provider) {
            case "OpenAI": 
                String openaiModel = config.getOpenAIModel();
                return (openaiModel != null && !openaiModel.isEmpty()) ? openaiModel : "gpt-3.5-turbo";
            case "Claude": 
                String claudeModel = config.getClaudeModel();
                return (claudeModel != null && !claudeModel.isEmpty()) ? claudeModel : "claude-3-sonnet-20240229";
            default: 
                String ollamaModel = config.getOllamaModel();
                return (ollamaModel != null && !ollamaModel.isEmpty()) ? ollamaModel : "qwen2.5-coder:7b";
        }
    }

    private void updateApiKeyFieldValue(JPasswordField field, String provider, Configuration config) {
        if ("OpenAI".equals(provider) && config.getOpenAIKey() != null) {
            field.setText(config.getOpenAIKey());
        } else if ("Claude".equals(provider) && config.getClaudeKey() != null) {
            field.setText(config.getClaudeKey());
        } else {
            field.setText("");
        }
    }

    private AIProvider createTestProvider(String provider, String endpoint, String model, String apiKey) throws Exception {
        Configuration tempConfig = Configuration.getInstance();
        switch (provider) {
            case "OpenAI":
                return new OpenAIProvider(apiKey, endpoint, model, 
                    tempConfig.getConnectTimeout(), tempConfig.getReadTimeout(), tempConfig.getMaxTokens());
            case "Claude":
                return new ClaudeProvider(apiKey, endpoint, model,
                    tempConfig.getConnectTimeout(), tempConfig.getReadTimeout(), tempConfig.getMaxTokens());
            default:
                return new OllamaProvider(endpoint, model,
                    tempConfig.getConnectTimeout(), tempConfig.getReadTimeout(), tempConfig.getMaxTokens());
        }
    }

    // Inner class for models result
    private static class ModelsResult {
        boolean connected = false;
        List<String> models = null;
        String error = null;
    }

    // fetchModels method (keep existing implementation)
    private List<String> fetchModels(String endpoint) throws Exception {
        List<String> models = new ArrayList<>();
        
        URL url = new URI(endpoint + "/api/tags").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        try {
            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String json = response.toString();
                java.util.regex.Pattern pattern = 
                    java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(json);
                
                while (matcher.find()) {
                    models.add(matcher.group(1));
                }
            }
        } finally {
            conn.disconnect();
        }
        
        return models;
    }

    private List<String> fetchOpenAIModels(String apiKey, String baseUrl) throws Exception {
        List<String> models = new ArrayList<>();
        
        URL url = new URI(baseUrl + "/v1/models").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        try {
            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String json = response.toString();
                Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(json);
                
                while (matcher.find()) {
                    String modelId = matcher.group(1);
                    // Filter to chat models only (GPT series)
                    if (modelId.startsWith("gpt-")) {
                        models.add(modelId);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        
        return models;
    }
    
    private List<String> fetchClaudeModels(String apiKey, String baseUrl) throws Exception {
        List<String> models = new ArrayList<>();
        
        // Use the correct Models API endpoint
        URL url = new URI(baseUrl + "/v1/models").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        
        int responseCode = conn.getResponseCode();
        
        if (responseCode == 200) {
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Parse JSON response to extract model IDs
            String json = response.toString();
            Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            
            while (matcher.find()) {
                String modelId = matcher.group(1);
                // Filter out any non-model entries if needed
                if (!modelId.startsWith("claude-")) {
                    continue;
                }
                models.add(modelId);
            }
        } else if (responseCode == 401) {
            throw new Exception("Invalid API Key");
        } else {
            throw new Exception("Failed to fetch models (HTTP " + responseCode + ")");
        }
        
        conn.disconnect();
        return models;
    }

    private JPanel createOllamaModelsStatusPanel(JTextField endpointField, JButton testButton, JLabel testResultLabel) {
        JPanel container = new JPanel(new GridLayout(1, 2, 10, 0));
        container.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "System Status",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION
        ));
        
        // Left side - Available Models
        JPanel modelsPanel = new JPanel(new BorderLayout());
        modelsPanel.setBorder(BorderFactory.createTitledBorder("Available Models"));
        
        DefaultListModel<String> modelListModel = new DefaultListModel<>();
        JList<String> modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelList.setVisibleRowCount(5);
        modelList.setEnabled(false);
        
        JScrollPane modelScrollPane = new JScrollPane(modelList);
        modelsPanel.add(modelScrollPane, BorderLayout.CENTER);
        
        JLabel modelNoteLabel = new JLabel("Models appear here after successful connection test");
        modelNoteLabel.setFont(modelNoteLabel.getFont().deriveFont(Font.ITALIC, 11f));
        modelNoteLabel.setForeground(Color.GRAY);
        modelNoteLabel.setHorizontalAlignment(JLabel.CENTER);
        modelsPanel.add(modelNoteLabel, BorderLayout.SOUTH);
        
        // Right side - Status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        
        JTextArea statusPlaceholder = new JTextArea();
        statusPlaceholder.setEditable(false);
        statusPlaceholder.setText("System Status Information\n\n" +
                                "• Ollama Connection: Not Tested\n" +
                                "• Model Status: Not Loaded\n" +
                                "• Last Check: Never\n\n" +
                                "Click 'Test Connection' to update status.\n\nAuthor: VermaOps | GitHub");
        statusPlaceholder.setMargin(new Insets(10, 10, 10, 10));
        statusPlaceholder.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        // Store reference to class field
        this.statusPlaceholder = statusPlaceholder;

        JScrollPane statusScrollPane = new JScrollPane(statusPlaceholder);
        statusPanel.add(statusScrollPane, BorderLayout.CENTER);
        
        container.add(modelsPanel);
        container.add(statusPanel);
        
        // Store references for test connection
        endpointField.putClientProperty("modelList", modelList);
        endpointField.putClientProperty("modelListModel", modelListModel);
        endpointField.putClientProperty("modelNoteLabel", modelNoteLabel);
        endpointField.putClientProperty("statusPlaceholder", statusPlaceholder);
        
        // Enhanced test connection logic
        testButton.addActionListener(e -> {
            testResultLabel.setText("Testing...");
            testResultLabel.setForeground(Color.BLACK);
            
            modelListModel.clear();
            modelList.setEnabled(false);
            
            if (statusPlaceholder != null) {
                statusPlaceholder.setText("System Status Information\n\n" +
                                        "• Testing connection to Ollama...\n" +
                                        "• Please wait...\n" +
                                        "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: VermaOps | GitHub");
            }
            
            SwingWorker<ModelsResult, Void> worker = new SwingWorker<>() {
                @Override
                protected ModelsResult doInBackground() {
                    ModelsResult result = new ModelsResult();
                    try {
                        OllamaProvider testClient = new OllamaProvider(
                            endpointField.getText().trim(),
                            "test-model"
                        );
                        
                        result.connected = testClient.isAvailable();
                        
                        if (result.connected) {
                            result.models = fetchModels(endpointField.getText().trim());
                        }
                    } catch (Exception ex) {
                        result.error = ex.getMessage();
                    }
                    return result;
                }
                
                @Override
                protected void done() {
                    try {
                        ModelsResult result = get();
                        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        
                        if (result.error != null) {
                            testResultLabel.setText("✗ Error: " + result.error);
                            testResultLabel.setForeground(Color.RED);
                            modelNoteLabel.setText("Failed to fetch models");
                            
                            if (statusPlaceholder != null) {
                                statusPlaceholder.setText("System Status Information\n\n" +
                                                        "• Ollama Connection: ✗ Failed\n" +
                                                        "• Error: " + result.error + "\n" +
                                                        "• Last Check: " + currentTime + "\n\n" +
                                                        "Click 'Test Connection' to update status.\n\nAuthor: VermaOps | GitHub");
                            }
                        } else if (result.connected) {
                            testResultLabel.setText("✓ Connection successful!");
                            testResultLabel.setForeground(new Color(0, 150, 0));
                            
                            if (result.models != null && !result.models.isEmpty()) {
                                for (String model : result.models) {
                                    modelListModel.addElement(model);
                                }
                                modelList.setEnabled(true);
                                modelNoteLabel.setText(result.models.size() + " model(s) available");
                                
                                if (statusPlaceholder != null) {
                                    statusPlaceholder.setText("System Status Information\n\n" +
                                                            "• Ollama Connection: ✓ Connected\n" +
                                                            "• Model Status: " + result.models.size() + " model(s) loaded\n" +
                                                            "• Last Check: " + currentTime + "\n\n" +
                                                            "Click 'Test Connection' to update status.\n\nAuthor: VermaOps | GitHub");
                                }
                            } else {
                                modelNoteLabel.setText("No models installed. Run 'ollama pull <model>'");
                                modelNoteLabel.setForeground(new Color(200, 100, 0));
                                
                                if (statusPlaceholder != null) {
                                    statusPlaceholder.setText("System Status Information\n\n" +
                                                            "• Ollama Connection: ✓ Connected\n" +
                                                            "• Model Status: No models installed\n" +
                                                            "• Last Check: " + currentTime + "\n\n" +
                                                            "Click 'Test Connection' to update status.\n\nAuthor: VermaOps | GitHub");
                                }
                            }
                        } else {
                            testResultLabel.setText("✗ Connection failed - Check if Ollama is running");
                            testResultLabel.setForeground(Color.RED);
                            modelNoteLabel.setText("Connect to see available models");
                            
                            if (statusPlaceholder != null) {
                                statusPlaceholder.setText("System Status Information\n\n" +
                                                        "• Ollama Connection: ✗ Failed\n" +
                                                        "• Model Status: Not Loaded\n" +
                                                        "• Last Check: " + currentTime + "\n\n" +
                                                        "Click 'Test Connection' to update status.\n\nAuthor: VermaOps | GitHub");
                            }
                        }
                    } catch (Exception ex) {
                        testResultLabel.setText("✗ Error: " + ex.getMessage());
                        testResultLabel.setForeground(Color.RED);
                        modelNoteLabel.setText("Failed to load models");
                        
                        if (statusPlaceholder != null) {
                            statusPlaceholder.setText("System Status Information\n\n" +
                                                    "• Ollama Connection: ✗ Error\n" +
                                                    "• Error: " + ex.getMessage() + "\n" +
                                                    "• Last Check: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\n" +
                                                    "Click 'Test Connection' to update status.\n\nAuthor: VermaOps | GitHub");
                        }
                    }
                }
            };
            worker.execute();
        });
        
        return container;
    }

    private JPanel createAdvancedSettingsPanel(Configuration config) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Advanced Settings"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        
        int row = 0;
        
        // Common settings for all providers
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Max Tokens:"), gbc);
        
        gbc.gridx = 1;
        JTextField maxTokensField = new JTextField(String.valueOf(config.getMaxTokens()), 10);
        maxTokensField.setName("max_tokens");
        panel.add(maxTokensField, gbc);
        
        row++;
        
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Temperature:"), gbc);
        
        gbc.gridx = 1;
        JTextField temperatureField = new JTextField("0.7", 10);
        temperatureField.setName("temperature");
        panel.add(temperatureField, gbc);
        
        row++;
        
        // Timeouts
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Connect Timeout (ms):"), gbc);
        
        gbc.gridx = 1;
        JTextField connectTimeoutField = new JTextField(String.valueOf(config.getConnectTimeout()), 10);
        connectTimeoutField.setName("connect_timeout");
        panel.add(connectTimeoutField, gbc);
        
        row++;
        
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Read Timeout (ms):"), gbc);
        
        gbc.gridx = 1;
        JTextField readTimeoutField = new JTextField(String.valueOf(config.getReadTimeout()), 10);
        readTimeoutField.setName("read_timeout");
        panel.add(readTimeoutField, gbc);
        
        // Store references for saving
        panel.putClientProperty("maxTokensField", maxTokensField);
        panel.putClientProperty("temperatureField", temperatureField);
        panel.putClientProperty("connectTimeoutField", connectTimeoutField);
        panel.putClientProperty("readTimeoutField", readTimeoutField);
        
        return panel;
    }

    private void saveAdvancedSettings(Configuration config, JPanel advancedPanel) {
        JTextField maxTokensField = (JTextField) advancedPanel.getClientProperty("maxTokensField");
        JTextField temperatureField = (JTextField) advancedPanel.getClientProperty("temperatureField");
        JTextField connectTimeoutField = (JTextField) advancedPanel.getClientProperty("connectTimeoutField");
        JTextField readTimeoutField = (JTextField) advancedPanel.getClientProperty("readTimeoutField");
        
        if (maxTokensField != null) {
            try {
                config.setMaxTokens(Integer.parseInt(maxTokensField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (connectTimeoutField != null) {
            try {
                config.setConnectTimeout(Integer.parseInt(connectTimeoutField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (readTimeoutField != null) {
            try {
                config.setReadTimeout(Integer.parseInt(readTimeoutField.getText().trim()));
            } catch (NumberFormatException ignored) {}
        }
        // Temperature is saved per-provider, not globally
    }

    private void browseForDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void startAnalysis() {
        String directory = directoryField.getText().trim();
        
        if (directory.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please select an APK directory", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Reset all state
        tableModel.setRowCount(0);
        detailsArea.setText("");
        rightDetailsArea.setText("");
        currentFindings = new FindingCollector();
        findingIdToRowMap.clear();
        rowToRequestsMap.clear();
        askAIButton.setEnabled(false);
        cancelButton.setEnabled(false);
        
        analyzeButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Analyzing...");
        
        SwingWorker<ScanResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ScanResult doInBackground() throws Exception {
                long startTime = System.currentTimeMillis();
                
                FileScanner scanner = new FileScanner();
                Map<FileType, List<File>> files = scanner.scan(directory);
                
                RuleEngine engine = new RuleEngine();
                List<Finding> findings = engine.analyzeFiles(files);
                
                long duration = System.currentTimeMillis() - startTime;
                int totalFiles = files.values().stream()
                    .mapToInt(List::size)
                    .sum();
                
                return new ScanResult(directory, findings, duration, totalFiles);
            }
            
            @Override
            protected void done() {
                try {
                    ScanResult result = get();
                    displayResults(result);
                    
                    api.logging().logToOutput("Analysis complete: " + 
                        result.getTotalFindings() + " findings");
                    
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainTab.this,
                        "Analysis failed: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    
                    api.logging().logToError("Analysis error: " + e.getMessage());
                    
                } finally {
                    analyzeButton.setEnabled(true);
                    progressBar.setValue(100);
                    progressBar.setString("Complete");
                }
            }
        };
        
        worker.execute();
    }
    
    private void displayResults(ScanResult result) {
        currentFindings.addFindings(result.getFindings());
        
        int row = 0;
        for (Finding f : result.getFindings()) {
            // Serial number = row + 1 (1-indexed)
            tableModel.addRow(new Object[]{
                row + 1,                         // # column - serial number
                f.getSeverity(),
                f.getTitle(),
                f.getCategory(),
                new File(f.getFilePath()).getName(),
                f.getConfidence(),
                AIStatus.NOT_REQUESTED.getDisplayName()
            });
            
            // Store mapping
            findingIdToRowMap.put(f.getId(), row);
            rowToRequestsMap.put(row, new ArrayList<>());
            row++;
        }
        
        // Update button states based on current selection
        updateButtonStates();
        
        detailsArea.setText(String.format("""
            Rule Based Analysis has Completed!
            
            Files Scanned: %d
            Scan Duration: %d ms
            Total Findings: %d
            
            Select findings and click "Ask Ollama" for AI analysis.
            """,
            result.getFilesScanned(),
            result.getScanDurationMs(),
            result.getTotalFindings()
        ));
    }
    
    private void displaySelectedFinding() {
        int row = findingsTable.getSelectedRow();
        if (row < 0) return;
        
        // Convert view index to model index for sorting compatibility
        int modelRow = findingsTable.convertRowIndexToModel(row);

        Finding finding = currentFindings.getAllFindings().get(modelRow);
        
        // Get AI response if available
        String aiAnalysis = "";
        List<OllamaRequest> requests = rowToRequestsMap.get(modelRow);
        if (requests != null && !requests.isEmpty()) {
            for (OllamaRequest request : requests) {
                if (request.getStatus() == AIStatus.COMPLETED && request.getResponse() != null) {
                    aiAnalysis = request.getResponse();
                    break;
                }
            }
        }
        
        // Get severity emoji and color marker
        String severityEmoji;
        String severityMarker;
        switch (finding.getSeverity()) {
            case CRITICAL:
                severityEmoji = "🔴";
                severityMarker = "█▓▒░ CRITICAL ░▒▓█";
                break;
            case HIGH:
                severityEmoji = "🟠"; 
                severityMarker = "▓▒░ HIGH ░▒▓";
                break;
            case MEDIUM:
                severityEmoji = "🟡";
                severityMarker = "▒░ MEDIUM ░▒";
                break;
            case LOW:
                severityEmoji = "🟢";
                severityMarker = "░ LOW ░";
                break;
            default:
                severityEmoji = "⚪";
                severityMarker = finding.getSeverity().toString();
        }

        // Truncate filename if too long
        String fileName = new File(finding.getFilePath()).getName();
        if (fileName.length() > 25) {
            fileName = "..." + fileName.substring(fileName.length() - 22);
        }
        
        // Create confidence bar visualization
        int confidenceBars = (int) (finding.getConfidence() * 10);
        String confidenceBar = "█".repeat(confidenceBars) + 
                            "░".repeat(10 - confidenceBars);

        // Display rule-based findings in LEFT panel
        detailsArea.setText(String.format("""
                ════════════════════════════════════════════════════
                |                        🔍 SECURITY FINDING                                 |
                ════════════════════════════════════════════════════
            
                ════════════════════════════════════════════════════
                |    🐞 ISSUE                                                                         |
                ════════════════════════════════════════════════════
                    %s %s

                ════════════════════════════════════════════════════
                |    📊 METADATA                                                                 |
                ════════════════════════════════════════════════════
                |    • %s Severity:      %s
                |
                |    • 🏷️  Category:    %s
                |
                |    • 📈 Confidence:  %s %.0f%%
                |
                |    • 📁 File:        %s
                |
                |    • 📄 Line:        %d
                ════════════════════════════════════════════════════

                ════════════════════════════════════════════════════
                |    📝 DESCRIPTION                                                             |
                ════════════════════════════════════════════════════
                    %s

                ════════════════════════════════════════════════════
                |    🔬 EVIDENCE                                                                  |
                ════════════════════════════════════════════════════
                    %s
            """,
            severityEmoji,
            finding.getTitle(),
            severityEmoji,
            severityMarker,
            finding.getCategory(),
            confidenceBar,
            finding.getConfidence() * 100,
            fileName,
            finding.getLineNumber(),
            wrapText(finding.getDescription(), 55),
            wrapText(finding.getEvidence(), 55)
        ));
        
        // Display AI analysis in RIGHT panel
        if (!aiAnalysis.isEmpty()) {
            rightDetailsArea.setText(String.format("""
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                |                                                                                                      🤖 AI ANALYSIS                                                                                                     |
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                AI analysis is for reference only, DYOR before coming to a conclusion.
                ----------------------------------------------------------------------------------

                %s
                """,
                wrapText(aiAnalysis, 120)
            ));
        } else {
            rightDetailsArea.setText("""
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                |                                                                                                      🤖 AI ANALYSIS                                                                                                     |
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                
                Click "Ask Ollama" to generate AI analysis.
                """);
        }
    }
    
    private String wrapText(String text, int width) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= width) return text;
        
        StringBuilder wrapped = new StringBuilder();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + width, text.length());
            if (end < text.length()) {
                // Try to break at word boundary
                int breakPos = text.lastIndexOf(' ', end);
                if (breakPos > pos) {
                    end = breakPos;
                }
            }
            wrapped.append(text.substring(pos, end).trim());
            if (end < text.length()) {
                wrapped.append("\n");
            }
            pos = end;
        }
        return wrapped.toString();
    }

    private void askAIForSelected() {
        int[] selectedRows = findingsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one finding to analyze.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Check Ollama availability first
        if (!aiProvider.isAvailable()) {
            int response = JOptionPane.showConfirmDialog(this,
                "Ollama is not available. Would you like to check connection settings?",
                "Connection Error",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE);
            
            if (response == JOptionPane.YES_OPTION) {
                // Find the APK-o-llama's internal tabbed pane
                for (java.awt.Component comp : this.getComponents()) {
                    if (comp instanceof JTabbedPane) {
                        JTabbedPane internalTabbedPane = (JTabbedPane) comp;
                        // Switch to Configuration tab (index 2)
                        internalTabbedPane.setSelectedIndex(2);
                        break;
                    }
                }
            }
            return;
        }

        // Build list of selected findings
        List<Finding> selectedFindings = new ArrayList<>();
        Map<Finding, Integer> findingToRowMap = new HashMap<>();
        
        for (int viewRow : selectedRows) {
            int modelRow = findingsTable.convertRowIndexToModel(viewRow);
            Finding finding = currentFindings.getAllFindings().get(modelRow);
            
            // Check if this finding already has an active request
            List<OllamaRequest> existingRequests = requestManager.getRequestsForFinding(finding.getId());
            boolean hasActiveRequest = existingRequests.stream()
                .anyMatch(r -> r.getStatus() == AIStatus.PENDING || 
                            r.getStatus() == AIStatus.IN_PROGRESS);
            
            if (hasActiveRequest) {
                String status = existingRequests.stream()
                    .map(r -> r.getStatus().getDisplayName())
                    .findFirst().orElse("Unknown");
                
                int choice = JOptionPane.showConfirmDialog(this,
                    "This finding already has a " + status + " request. Create new one?",
                    "Request Already Exists",
                    JOptionPane.YES_NO_OPTION);
                
                if (choice != JOptionPane.YES_OPTION) {
                    continue;
                }
            }
            
            selectedFindings.add(finding);
            findingToRowMap.put(finding, modelRow);
        }
        
        if (selectedFindings.isEmpty()) {
            return;
        }
        
        // Update button states
        updateButtonStates();
        
        // Create base prompt
        String promptBase = """
            You are a security researcher with 10 years of experience, writing a bug bounty–style vulnerability report. 
            Based on the details below, Determine whether this finding represents a valid security vulnerability.
            1. If it is not a valid security issue, respond only with: "invalid bug" and provide a short explanation.
            2. If it is a valid issue, generate a clear, professional write-up suitable for submission to a bug bounty program.

            Vulnerability Details-
            * Title: %s
            * Severity: %s
            * Category: %s
            * Affected File / Location: %s
            * Evidence: %s
            
            Write the report using this structure:
            1. Summary
            * Briefly explain what the vulnerability is and where it was found.

            2. Description / Technical Details
            * Explain the issue clearly and technically.

            3. Impact
            * Explain what an attacker could achieve by exploiting this issue.

            4. Steps to Reproduce (if applicable)
            * Provide clear, logical steps that demonstrate how the issue can be observed or verified.

            5. Mitigation
            * Provide various mitigation strategies.

            Guidelines
            * Keep the writing **concise, clear, and professional**.
            * Use language and tone appropriate for **bug bounty platforms (HackerOne / Bugcrowd style reports)**.
            * Avoid unnecessary verbosity, but ensure the explanation is complete and understandable.
            * If something is missing fill the gaps, only if necessary.
        """;
    
        // Submit batch requests and store them in rowToRequestsMap
        List<OllamaRequest> batchRequests = requestManager.submitBatch(selectedFindings, promptBase, findingToRowMap);
        
        // Clear old requests for these findings and store new ones
        for (OllamaRequest request : batchRequests) {
            Integer modelRow = findingIdToRowMap.get(request.getFinding().getId());
            if (modelRow != null) {
                // Clear old requests list and add new one
                List<OllamaRequest> requests = new ArrayList<>();
                requests.add(request);
                rowToRequestsMap.put(modelRow, requests);
            }
        }
        
        // Show progress
        progressBar.setString("AI Analysis: 0/" + selectedFindings.size());
        
        api.logging().logToOutput("Started AI analysis for " + selectedFindings.size() + " findings");
    }

    private void askAI() {
        String prompt = promptArea.getText().trim();
        
        if (prompt.isEmpty()) {
            return;
        }
        
        if (!aiProvider.isAvailable()) {
            String errorMsg = "\n============================================================\n" +
                            "✗ ERROR: Ollama is not available\n" +
                            "Make sure Ollama is running (ollama serve)\n" +
                            "============================================================\n\n";
            responseArea.append(errorMsg);
            return;
        }
        
        // Cancel any existing request first
        if (currentConsoleRequest != null && !currentConsoleRequest.isCancelled() && 
            (currentConsoleRequest.getStatus() == AIStatus.PENDING || 
            currentConsoleRequest.getStatus() == AIStatus.IN_PROGRESS)) {
            cancelAIConsoleRequest();
            // Small delay to ensure cleanup
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        
        // Add user message to history (for context, NOT displayed)
        conversationHistory.addUserMessage(prompt);
        
        // Clear input
        //promptArea.setText("");
        
        // Get model name for display
        String modelName = aiProvider.getModel();
        
        // Capture start time
        long startTime = System.currentTimeMillis();
        
        // Append analysis started block (NO timestamp, NO "You:")
        String startedBlock = "============================================================\n" +
                            "Analyzing with " + modelName + "...\n" +
                            "============================================================\n";
        responseArea.append(startedBlock);
        
        // Build prompt with conversation context (for AI memory)
        String contextualPrompt = conversationHistory.getConversationContext() + prompt;
        
        // Create a dummy Finding for console requests (needed for OllamaRequest)
        Finding consoleFinding = new Finding(
            "console-" + System.currentTimeMillis(),
            "AI Console Request",
            Severity.INFO,
            "Console",
            "",
            -1,
            "",
            "",
            0.0
        );
        
        // Create and track the request
        currentConsoleRequest = new OllamaRequest(consoleFinding, contextualPrompt, -1);
        currentConsoleRequest.setStatus(AIStatus.IN_PROGRESS);
        
        // Auto-scroll
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
        
        // Update button states
        JButton askButton = findAskButton();
        JButton cancelButton = findCancelButton();
        if (consoleAskButton != null) consoleAskButton.setEnabled(false);
        if (consoleCancelButton != null) consoleCancelButton.setEnabled(true);
        
        currentAIWorker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                // Pass the request to enable cancellation
                AIResponse response = aiProvider.generateText(contextualPrompt, Map.of());
                return response.getText();
            }
            
            @Override
            protected void done() {
                try {
                    // Check if cancelled
                    if (isCancelled() || currentConsoleRequest.isCancelled()) {
                        String cancelledBlock = "✗ REQUEST CANCELLED\n" +
                                            "============================================================\n\n";
                        responseArea.append(cancelledBlock);
                        return;
                    }
                    
                    String response = get();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // Add assistant response to history (for future context)
                    conversationHistory.addAssistantMessage(response);
                    
                    // Format duration nicely
                    String durationStr;
                    if (duration < 1000) {
                        durationStr = duration + "ms";
                    } else {
                        durationStr = String.format("%.2fs", duration / 1000.0);
                    }
                    
                    // Estimate token usage
                    int promptTokens = 0;
                    int responseTokens = 0;
                    int totalTokens = 0;

                    if (aiProvider instanceof OllamaProvider) {
                        OllamaProvider ollamaProvider = (OllamaProvider) aiProvider;
                        promptTokens = ollamaProvider.estimateTokenCount(prompt);
                        responseTokens = ollamaProvider.estimateTokenCount(response);
                        totalTokens = promptTokens + responseTokens;
                    } else {
                        // Fallback estimation for other providers
                        promptTokens = (int) Math.ceil(prompt.length() / 4.0);
                        responseTokens = (int) Math.ceil(response.length() / 4.0);
                        totalTokens = promptTokens + responseTokens;
                    }
                    
                    // Append completion block
                    String completeBlock ="✓ ANALYSIS COMPLETE\n" +
                                        "Model: " + modelName + " | Time: " + durationStr + 
                                        " | Tokens: ~" + totalTokens + "\n" +
                                        "============================================================\n\n";
                    responseArea.append(completeBlock);
                    
                    // Append the actual response (properly formatted)
                    responseArea.append(response + "\n\n");
                    
                } catch (InterruptedException | CancellationException e) {
                    // Handle cancellation
                    String cancelledBlock = "✗ REQUEST CANCELLED\n" +
                                        "============================================================\n\n";
                    responseArea.append(cancelledBlock);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof InterruptedException || 
                        (cause.getMessage() != null && cause.getMessage().contains("cancelled"))) {
                        String cancelledBlock = "✗ REQUEST CANCELLED\n" +
                                            "============================================================\n\n";
                        responseArea.append(cancelledBlock);
                    } else {
                        String errorBlock ="✗ ANALYSIS FAILED\n" +
                                        "Error: " + (cause != null ? cause.getMessage() : e.getMessage()) + "\n" +
                                        "============================================================\n\n";
                        responseArea.append(errorBlock);
                    }
                } finally {
                    // Clear current request reference
                    currentConsoleRequest = null;
                    currentAIWorker = null;
                    
                    // Reset button states
                    SwingUtilities.invokeLater(() -> {
                        if (consoleAskButton != null) consoleAskButton.setEnabled(true);
                        if (consoleCancelButton != null) consoleCancelButton.setEnabled(false);
                    });
                    
                    // Auto-scroll to bottom
                    SwingUtilities.invokeLater(() -> {
                        JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
                        JScrollBar vertical = scrollPane.getVerticalScrollBar();
                        vertical.setValue(vertical.getMaximum());
                    });
                }
            }
        };
        
        currentAIWorker.execute();
    }

    private void cancelAIConsoleRequest() {
        // Cancel the worker first
        if (currentAIWorker != null && !currentAIWorker.isDone()) {
            currentAIWorker.cancel(true);
        }
        
        // Cancel the request
        if (currentConsoleRequest != null && !currentConsoleRequest.isCancelled()) {
            currentConsoleRequest.cancel();
        }
        
        // Force disconnect any lingering HTTP connections
        // (handled by OllamaProvider's cancellation checks)
        
        // Append cancellation message if not already done
        if (currentConsoleRequest != null && currentConsoleRequest.getStatus() != AIStatus.CANCELLED) {
            String cancelMsg = "\n============================================================\n" +
                            "✗ REQUEST CANCELLED BY USER\n" +
                            "============================================================\n\n";
            responseArea.append(cancelMsg);
        }
        
        // Reset UI state
        if (consoleAskButton != null) consoleAskButton.setEnabled(true);
        if (consoleCancelButton != null) consoleCancelButton.setEnabled(false);
        
        // Clear references
        currentConsoleRequest = null;
        currentAIWorker = null;
        
        // Auto-scroll
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void clearAIResponse() {
        // Clear the response area completely
        responseArea.setText("Click \"Ask AI\" to start analysis with Ollama\n");
        
        // Optional: Add a subtle visual reset (no functional impact)
        responseArea.setCaretPosition(0);
    }

    private JButton findAskButton() {
        // Find the AI Console tab's button panel
        for (java.awt.Component comp : this.getComponents()) {
            if (comp instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) comp;
                java.awt.Component consolePanel = tabbedPane.getComponentAt(1); // AI Console tab
                if (consolePanel instanceof JPanel) {
                    return findButtonInPanel((JPanel) consolePanel, "Ask AI");
                }
            }
        }
        return null;
    }

    private JButton findCancelButton() {
        // Find the AI Console tab's button panel
        for (java.awt.Component comp : this.getComponents()) {
            if (comp instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) comp;
                java.awt.Component consolePanel = tabbedPane.getComponentAt(1); // AI Console tab
                if (consolePanel instanceof JPanel) {
                    return findButtonInPanel((JPanel) consolePanel, "Cancel");
                }
            }
        }
        return null;
    }

    private JButton findButtonInPanel(JPanel panel, String buttonText) {
        for (java.awt.Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                // Recursively search sub-panels
                JButton result = findButtonInPanel((JPanel) comp, buttonText);
                if (result != null) return result;
            } else if (comp instanceof JButton) {
                JButton button = (JButton) comp;
                if (buttonText.equals(button.getText())) {
                    return button;
                }
            }
        }
        return null;
    }

    /**
     * Performs background version check on startup
     */
    private void performBackgroundVersionCheck() {
        SwingWorker<VersionCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected VersionCheckResult doInBackground() {
                return checkForNewVersion();
            }
            
            @Override
            protected void done() {
                try {
                    VersionCheckResult result = get();
                    Configuration loadedConfig = Configuration.getInstance();
                    
                    if (result.error != null) {
                        // Error occurred, log but don't show in UI
                        api.logging().logToOutput("Background version check failed: " + result.error);
                        loadedConfig.setVersionCheckError(result.error);
                        return;
                    }
                    
                    // Update configuration
                    loadedConfig.setUpdateAvailable(result.updateAvailable);
                    loadedConfig.setLatestVersion(result.latestVersion);
                    loadedConfig.setLastVersionCheckTime(System.currentTimeMillis());
                    loadedConfig.setVersionCheckError(null);
                    loadedConfig.saveToFile();
                    
                    // Update button color
                    SwingUtilities.invokeLater(() -> {
                        updateReleasesButtonColor(result.updateAvailable);
                    });
                    
                    // Log result
                    if (result.updateAvailable) {
                        api.logging().logToOutput("New version available: " + result.latestVersion);
                    } else {
                        api.logging().logToOutput("Already using latest version: " + result.latestVersion);
                    }
                    
                } catch (Exception e) {
                    api.logging().logToError("Error in background version check: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }
    
    private void retryRowAI(int viewRow) {
        // Convert view index to model index for sorting compatibility
        int modelRow = findingsTable.convertRowIndexToModel(viewRow);
        
        List<OllamaRequest> requests = rowToRequestsMap.get(modelRow);
        if (requests != null) {
            boolean anyRetried = false;
            for (OllamaRequest request : requests) {
                if (request.getStatus().isRetryable()) {
                    requestManager.retryRequest(request.getRequestId());
                    anyRetried = true;
                }
            }
            
            if (anyRetried) {
                api.logging().logToOutput("Retrying AI analysis for row " + viewRow);
            }
        }
    }

    private void cancelSelectedAI() {
        int[] selectedRows = findingsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }
        
        boolean anyCancelled = false;
        for (int viewRow : selectedRows) {
            int modelRow = findingsTable.convertRowIndexToModel(viewRow);
            List<OllamaRequest> requests = rowToRequestsMap.get(modelRow);
            
            if (requests != null) {
                for (OllamaRequest request : requests) {
                    if (request.getStatus().isCancellable()) {
                        requestManager.cancelRequest(request.getRequestId());
                        anyCancelled = true;
                    }
                }
            }
        }
        
        if (anyCancelled) {
            api.logging().logToOutput("Cancelled AI analysis for selected findings");
            updateButtonStates();
        }
    }

    // Implement StatusUpdateListener methods
    @Override
    public void onStatusUpdate(OllamaRequest request) {
        SwingUtilities.invokeLater(() -> {
            Integer row = findingIdToRowMap.get(request.getFinding().getId());
            if (row != null) {
                // Get the most recent request for this finding
                List<OllamaRequest> requests = rowToRequestsMap.get(row);
                if (requests != null && !requests.isEmpty()) {
                    // Sort by creation time to get the latest
                    OllamaRequest latest = requests.stream()
                        .max(Comparator.comparing(OllamaRequest::getCreatedAt))
                        .orElse(request);
                    
                    // Only update UI if this is the latest request
                    if (latest.getRequestId().equals(request.getRequestId())) {
                        tableModel.setValueAt(request.getStatus().getDisplayName(), row, COL_AI_STATUS);
                        
                        // If request completed, update the finding with AI analysis
                        if (request.getStatus() == AIStatus.COMPLETED && request.getResponse() != null) {
                            request.getFinding().setAiAnalysis(request.getResponse());
                            request.getFinding().setAiStatus(Finding.AiAnalysisStatus.COMPLETED);
                            
                            // Refresh details if this row is selected
                            int selectedViewRow = findingsTable.getSelectedRow();
                            if (selectedViewRow >= 0) {
                                int selectedModelRow = findingsTable.convertRowIndexToModel(selectedViewRow);
                                if (selectedModelRow == row) {
                                    displaySelectedFinding();
                                }
                            }
                        }
                    }
                }
                
                updateProgressBar();
                updateButtonStates();
            }
        });
    }

    @Override
    public void onBatchComplete(List<OllamaRequest> completedRequests) {
        SwingUtilities.invokeLater(() -> {
            // Update button states
            updateButtonStates();
            
            // Update progress bar
            progressBar.setString("AI Analysis Complete");
            progressBar.setValue(100);
            
            api.logging().logToOutput("Batch AI analysis completed: " + 
                completedRequests.size() + " requests processed");
        });
    }

    private void updateProgressBar() {
        int totalRequests = 0;
        int completedRequests = 0;
        int failedRequests = 0;
        
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String statusStr = (String) tableModel.getValueAt(row, COL_AI_STATUS);
            AIStatus status = getAIStatusFromDisplay(statusStr);
            
            if (status != AIStatus.NOT_REQUESTED) {
                totalRequests++;
                if (status == AIStatus.COMPLETED) {
                    completedRequests++;
                } else if (status.isRetryable()) {
                    failedRequests++;
                }
            }
        }
        
        if (totalRequests > 0) {
            int progress = (int) ((completedRequests / (double) totalRequests) * 100);
            progressBar.setValue(progress);
            progressBar.setString(String.format("AI Analysis: %d/%d (Failed: %d)", 
                completedRequests, totalRequests, failedRequests));
        }
    }

    private void updateButtonStates() {
        int[] selectedRows = findingsTable.getSelectedRows();
        
        if (selectedRows.length == 0) {
            // No selection - disable both buttons
            askAIButton.setEnabled(false);
            cancelButton.setEnabled(false);
            return;
        }
        
        boolean hasAskable = false;
        boolean hasCancellable = false;
        
        for (int viewRow : selectedRows) {
            int modelRow = findingsTable.convertRowIndexToModel(viewRow);
            String statusStr = (String) tableModel.getValueAt(modelRow, COL_AI_STATUS);
            AIStatus status = getAIStatusFromDisplay(statusStr);
            
            if (status != null) {
                // Check if status allows "Ask Ollama"
                if (status == AIStatus.NOT_REQUESTED || 
                    status == AIStatus.COMPLETED ||
                    status == AIStatus.CANCELLED || 
                    status.isRetryable()) {
                    hasAskable = true;
                }
                
                // Check if status allows "Cancel"
                if (status.isCancellable()) {
                    hasCancellable = true;
                }
            }
        }
        
        // Enable/disable buttons based on selection states
        askAIButton.setEnabled(hasAskable);
        cancelButton.setEnabled(hasCancellable);
    }

    /**
     * Exports findings to a CSV file based on selection state
     */
    private void exportFindings() {
        // Check if there are any findings at all
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(
                this,
                "No data available to export.",
                "Export Warning",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Check if any rows are selected
        int[] selectedRows = findingsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(
                this,
                "No row selected.",
                "Export Warning",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Show format selection dialog
        String[] formats = {"CSV", "HTML"};
        int formatChoice = JOptionPane.showOptionDialog(
            this,
            "Select Export Format:",
            "Export Format",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            formats,
            formats[0]
        );
        
        if (formatChoice == JOptionPane.CLOSED_OPTION) {
            return; // User cancelled
        }
        
        String selectedFormat = formats[formatChoice];
        String projectName = "";

        // If HTML selected, ask for project name
        if ("HTML".equals(selectedFormat)) {
            // Suggest default from directory name if available
            String defaultName = "";
            String directory = directoryField.getText().trim();
            if (!directory.isEmpty()) {
                File dir = new File(directory);
                defaultName = dir.getName();
            }
            
            Object input = JOptionPane.showInputDialog(
                this,
                "Enter project/report name:",
                "Report Title",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                defaultName
            );

            if (input == null) {
                return; // User cancelled
            }
            projectName = input.toString().trim();
            
            if (projectName == null) {
                return; // User cancelled
            }
            
            projectName = projectName.trim();
        }
        // Configure file chooser based on format
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Findings as " + selectedFormat);
        
        String extension;
        String description;
        if ("CSV".equals(selectedFormat)) {
            extension = "csv";
            description = "CSV files (*.csv)";
            fileChooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        } else {
            extension = "html";
            description = "HTML files (*.html)";
            fileChooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        }
        
        // Suggest a default filename with timestamp
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        fileChooser.setSelectedFile(new File("apk-ollama-findings-" + timestamp + "." + extension));
        
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // Ensure correct extension
            final File fileToSave;
            if (!selectedFile.getName().toLowerCase().endsWith("." + extension)) {
                fileToSave = new File(selectedFile.getAbsolutePath() + "." + extension);
            } else {
                fileToSave = selectedFile;
            }
            
            // Store selected rows for background thread
            final int[] rowsToExport = selectedRows.clone();
            final String format = selectedFormat;
            final String reportName = projectName;

            // Perform export in background to avoid UI freeze
            SwingWorker<Void, Void> exportWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if ("CSV".equals(format)) {
                        exportSelectedRowsToCsv(fileToSave, rowsToExport);
                    } else {
                        exportSelectedRowsToHtml(fileToSave, rowsToExport, reportName);
                    }
                    return null;
                }
                
                @Override
                protected void done() {
                    try {
                        get(); // Check for exceptions
                        JOptionPane.showMessageDialog(
                            MainTab.this,
                            "Exported " + rowsToExport.length + " finding(s) successfully to:\n" + fileToSave.getAbsolutePath(),
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        api.logging().logToOutput("Exported " + rowsToExport.length + " findings to: " + fileToSave.getAbsolutePath());
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(
                            MainTab.this,
                            "Error exporting findings: " + e.getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                        api.logging().logToError("Export failed: " + e.getMessage());
                    }
                }
            };
            
            exportWorker.execute();
        }
    }

    /**
     * Writes only selected findings to CSV file
     */
    private void exportSelectedRowsToCsv(File file, int[] selectedViewRows) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header - added "#" column
            writer.println("#,Severity,Title,Category,File,Line Number,Confidence,AI Status,Description,Evidence,AI Analysis");
            
            // Write data rows for selected findings only
            for (int viewRow : selectedViewRows) {
                int modelRow = findingsTable.convertRowIndexToModel(viewRow);
                Finding finding = currentFindings.getAllFindings().get(modelRow);
                
                // Get serial number from the table
                int serialNumber = (Integer) tableModel.getValueAt(modelRow, COL_NUMBER);
                
                StringBuilder line = new StringBuilder();
                
                // Serial number (from stored value)
                line.append(serialNumber).append(",");
                
                // Severity
                line.append(escapeCsv(finding.getSeverity().toString())).append(",");
                
                // Title
                line.append(escapeCsv(finding.getTitle())).append(",");
                
                // Category
                line.append(escapeCsv(finding.getCategory())).append(",");
                
                // File (just filename, not full path)
                String fileName = new File(finding.getFilePath()).getName();
                line.append(escapeCsv(fileName)).append(",");
                
                // Line Number
                line.append(finding.getLineNumber()).append(",");
                
                // Confidence (as percentage)
                line.append(String.format("%.0f%%", finding.getConfidence() * 100)).append(",");
                
                // AI Status - get from table
                String aiStatus = (String) tableModel.getValueAt(modelRow, COL_AI_STATUS);
                line.append(escapeCsv(aiStatus)).append(",");
                
                // Description
                line.append(escapeCsv(finding.getDescription())).append(",");
                
                // Evidence
                line.append(escapeCsv(finding.getEvidence())).append(",");
                
                // AI Analysis (if available)
                String aiAnalysis = finding.getAiAnalysis();
                if (aiAnalysis == null || aiAnalysis.isEmpty()) {
                    aiAnalysis = "No AI analysis available";
                }
                line.append(escapeCsv(aiAnalysis));
                
                writer.println(line.toString());
            }
        }
    }

    /**
     * Exports selected findings to HTML report format
     */
    private void exportSelectedRowsToHtml(File file, int[] selectedViewRows, String projectName) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            // Collect selected findings with their serial numbers
            List<Object[]> selectedData = new ArrayList<>();
            for (int viewRow : selectedViewRows) {
                int modelRow = findingsTable.convertRowIndexToModel(viewRow);
                Finding finding = currentFindings.getAllFindings().get(modelRow);
                int serialNumber = (Integer) tableModel.getValueAt(modelRow, COL_NUMBER);
                selectedData.add(new Object[]{serialNumber, finding});
            }
            
            // Calculate severity counts
            Map<Severity, Integer> severityCounts = new EnumMap<>(Severity.class);
            for (Severity severity : Severity.values()) {
                severityCounts.put(severity, 0);
            }
            for (Object[] data : selectedData) {
                Finding finding = (Finding) data[1];
                severityCounts.merge(finding.getSeverity(), 1, Integer::sum);
            }
            
            // Generate timestamp
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // Generate HTML
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("    <meta charset=\"UTF-8\">");
            writer.println("    <title>APK-o-llama Findings Report</title>");
            writer.println("    <style>");
            writer.println("        body { font-family: Arial, sans-serif; margin: 20px; background-color: #ffffff; }");
            writer.println("        h1 { color: #333; border-bottom: 2px solid #666; padding-bottom: 10px; }");
            writer.println("        h2 { color: #444; margin-top: 30px; }");
            writer.println("        h3 { color: #555; margin: 10px 0; }");
            writer.println("        .summary { background-color: #f5f5f5; padding: 15px; border-radius: 5px; margin: 20px 0; }");
            writer.println("        .summary-item { margin: 5px 0; }");
            writer.println("        .finding { border: 1px solid #ddd; margin: 15px 0; padding: 15px; border-radius: 5px; }");
            writer.println("        .finding-header { display: flex; align-items: center; margin-bottom: 10px; }");
            writer.println("        .finding-number { font-weight: bold; color: #666; margin-right: 10px; }");
            writer.println("        .severity-badge { padding: 3px 10px; border-radius: 3px; font-weight: bold; margin-right: 10px; }");
            writer.println("        .severity-critical { background-color: #ff4444; color: white; }");
            writer.println("        .severity-high { background-color: #ff8800; color: white; }");
            writer.println("        .severity-medium { background-color: #ffcc00; color: black; }");
            writer.println("        .severity-low { background-color: #33b5e5; color: white; }");
            writer.println("        .severity-info { background-color: #aaaaaa; color: white; }");
            writer.println("        .finding-title { font-size: 18px; font-weight: bold; }");
            writer.println("        .finding-meta { color: #666; font-size: 14px; margin: 5px 0; }");
            writer.println("        .finding-section { margin: 15px 0 0 0; }");
            writer.println("        .section-label { font-weight: bold; color: #444; margin-bottom: 5px; }");
            writer.println("        .section-content { background-color: #f9f9f9; padding: 10px; border-left: 3px solid #33b5e5; white-space: pre-wrap; }");
            writer.println("        .evidence { font-family: monospace; background-color: #f0f0f0; }");
            writer.println("        .footer { margin-top: 30px; color: #888; font-size: 12px; text-align: center; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");
            
            // Header
            String reportTitle = projectName == null || projectName.isEmpty() ? 
                "APK-o-llama Findings Report" : 
                projectName + " Vulnerability Report";

            writer.println("    <div style=\"text-align: center;\">");
            writer.println("        <h1>" + escapeHtml(reportTitle) + "</h1>");
            writer.println("        <p>Generated: " + timestamp + "</p>");
            writer.println("    </div>");
            
            // Summary section
            writer.println("    <div class=\"summary\">");
            writer.println("        <h2>Summary</h2>");
            writer.println("        <div class=\"summary-item\">Total Selected Findings: " + selectedData.size() + "</div>");
            writer.println("        <div class=\"summary-item\">Severity Breakdown:</div>");
            writer.println("        <ul style=\"list-style-type: none; padding-left: 0;\">");
            for (Severity severity : Severity.values()) {
                int count = severityCounts.get(severity);
                if (count > 0) {
                    String severityClass = getSeverityCssClass(severity);
                    writer.println("            <li style=\"margin-bottom: 8px;\"><span class=\"severity-badge " + severityClass + "\">" + 
                        severity.getDisplayName() + "</span>: " + count + "</li>");
                }
            }
            writer.println("        </ul>");
            writer.println("    </div>");
            
            // Detailed findings
            writer.println("    <h2>Detailed Findings</h2>");
            
            for (Object[] data : selectedData) {
                int serialNumber = (Integer) data[0];
                Finding finding = (Finding) data[1];
                String severityClass = getSeverityCssClass(finding.getSeverity());
                String fileName = new File(finding.getFilePath()).getName();
                
                writer.println("    <div class=\"finding\">");
                writer.println("        <div class=\"finding-header\">");
                writer.println("            <span class=\"finding-number\">#" + serialNumber + "</span>");
                writer.println("            <span class=\"severity-badge " + severityClass + "\">" + 
                    finding.getSeverity().getDisplayName() + "</span>");
                writer.println("            <span class=\"finding-title\">" + escapeHtml(finding.getTitle()) + "</span>");
                writer.println("        </div>");
                writer.println("        <div class=\"finding-meta\">");
                writer.println("            Category: " + escapeHtml(finding.getCategory()) + "<br>");
                writer.println("            File: " + escapeHtml(fileName) + "<br>");
                if (finding.getLineNumber() > 0) {
                    writer.println("            Line: " + finding.getLineNumber() + "<br>");
                }
                writer.println("            Confidence: " + String.format("%.0f%%", finding.getConfidence() * 100) + "<br>");
                writer.println("        </div>");
                
                writer.println("        <div class=\"finding-section\">");
                writer.println("            <div class=\"section-label\">Description:</div>");
                writer.println("            <div class=\"section-content\">" + escapeHtml(finding.getDescription()) + "</div>");
                writer.println("        </div>");
                
                writer.println("        <div class=\"finding-section\">");
                writer.println("            <div class=\"section-label\">Evidence:</div>");
                writer.println("            <div class=\"section-content evidence\">" + escapeHtml(finding.getEvidence()) + "</div>");
                writer.println("        </div>");
                
                // Recommendation (placeholder if not available)
                writer.println("        <div class=\"finding-section\">");
                writer.println("            <div class=\"section-label\">Recommendation:</div>");
                writer.println("            <div class=\"section-content\">Review this finding in context. " +
                    "Consider implementing proper security controls based on the finding type.</div>");
                writer.println("        </div>");
                
                writer.println("    </div>");
            }
            
            // Footer
            writer.println("    <div class=\"footer\">");
            writer.println("        Generated by APK-o-llama v" + VersionManager.getCurrentVersion() + "<br>");
            writer.println("        Author: VermaOps | GitHub: https://github.com/VermaOps/apk-o-llama");
            writer.println("    </div>");
            
            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * Returns CSS class for severity badge
     */
    private String getSeverityCssClass(Severity severity) {
        switch (severity) {
            case CRITICAL: return "severity-critical";
            case HIGH: return "severity-high";
            case MEDIUM: return "severity-medium";
            case LOW: return "severity-low";
            case INFO: return "severity-info";
            default: return "severity-info";
        }
    }

    /**
     * Escapes HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    
    /**
     * Escape special characters for CSV format
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        
        // Replace double quotes with double double quotes
        String escaped = value.replace("\"", "\"\"");
        
        // Wrap in double quotes if contains comma, newline, or double quote
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        
        return escaped;
    }

    private void appendToChat(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            
            String formattedMessage;
            if ("System".equals(sender)) {
                formattedMessage = String.format("\n[%s] 🤖 %s:\n%s\n", timestamp, sender, message);
            } else if ("User".equals(sender)) {
                formattedMessage = String.format("\n[%s] 👤 %s:\n%s\n", timestamp, sender, message);
            } else {
                formattedMessage = String.format("\n[%s] 🤖 %s:\n%s\n", timestamp, sender, message);
            }
            
            responseArea.append(formattedMessage);
            
            // Auto-scroll to bottom
            JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private AIStatus getAIStatusFromDisplay(String displayName) {
        for (AIStatus status : AIStatus.values()) {
            if (status.getDisplayName().equals(displayName)) {
                return status;
            }
        }
        return null;
    }

    // Custom cell renderer for AI Status column
    private class AIStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            // Convert table row index to model row index for sorting
            int modelRow = table.convertRowIndexToModel(row);

            Component c = super.getTableCellRendererComponent(table, value, 
                isSelected, hasFocus, row, column);
            
            if (column == COL_AI_STATUS && value instanceof String) {
                String statusStr = (String) value;
                AIStatus status = getAIStatusFromDisplay(statusStr);
                
                if (status != null) {
                    switch (status) {
                        case COMPLETED:
                            c.setForeground(new Color(255, 255, 255)); // White
                            setToolTipText("AI analysis completed successfully");
                            break;
                        case IN_PROGRESS:
                            c.setForeground(new Color(255, 255, 255)); // // White
                            setToolTipText("AI analysis in progress...");
                            break;
                        case PENDING:
                            c.setForeground(new Color(255, 255, 255)); // // White
                            setToolTipText("Waiting for AI analysis");
                            break;
                        case CANCELLED:
                            c.setForeground(new Color(128, 128, 128)); // Gray
                            setToolTipText("AI analysis was cancelled");
                            break;
                        case FAILED:
                        case TIMEOUT:
                        case RATE_LIMITED:
                            c.setForeground(new Color(255, 0, 0)); // Red
                            setToolTipText("Click to retry AI analysis");
                            setFont(getFont().deriveFont(Font.BOLD));
                            break;
                        default:
                            c.setForeground(table.getForeground());
                            setToolTipText(null);
                    }
                }
                
                // Special styling for clickable retry statuses
                if (status != null && status.isRetryable()) {
                    setText("<html><u>" + value + "</u></html>");
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setText(value.toString());
                    setCursor(Cursor.getDefaultCursor());
                }
            }
            
            // Set confidence column text color to white
            if (column == COL_CONFIDENCE) {
                c.setForeground(Color.WHITE);
            }
            
            return c;
        }
    }

    // Cleanup when tab is closed
    public void cleanup() {
        if (requestManager != null) {
            requestManager.shutdown();
        }
    }

    // Custom comparator for Severity column
    private class SeverityComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            if (!(o1 instanceof Severity) || !(o2 instanceof Severity)) {
                return 0;
            }
            
            Severity s1 = (Severity) o1;
            Severity s2 = (Severity) o2;
            
            // Compare using the numeric level (Critical=4, High=3, Medium=2, Low=1, Info=0)
            return Integer.compare(s1.getLevel(), s2.getLevel());
        }
    }

    // Custom comparator for Confidence column
    private class ConfidenceComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            if (!(o1 instanceof Double) || !(o2 instanceof Double)) {
                return 0;
            }
            
            Double d1 = (Double) o1;
            Double d2 = (Double) o2;
            
            // Compare numeric values (0.0-1.0)
            return Double.compare(d1, d2);
        }
    }

    // Inner class for test connection results to avoid unchecked casts
    private static class TestConnectionResult {
        boolean connected;
        List<String> models;
        String error;
        
        TestConnectionResult(boolean connected, List<String> models, String error) {
            this.connected = connected;
            this.models = models != null ? models : new ArrayList<>();
            this.error = error;
        }
    }
}