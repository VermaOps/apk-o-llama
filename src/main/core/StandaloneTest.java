package core;

import models.Finding;
import models.ScanResult;
import rules.RuleEngine;
import scanner.FileScanner;
import scanner.FileType;

import java.io.File;
import java.util.List;
import java.util.Map;

public class StandaloneTest {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java StandaloneTest <apk-directory>");
            System.exit(1);
        }
        
        String directory = args[0];
        System.out.println("APK-o-llama Standalone Analysis");
        System.out.println("By: VermaOps | https://github.com/VermaOps");
        System.out.println("================================\n");
        
        long startTime = System.currentTimeMillis();
        
        System.out.println("Step 1: Scanning files...");
        FileScanner scanner = new FileScanner();
        Map<FileType, List<File>> files = scanner.scan(directory);
        
        int totalFiles = files.values().stream()
            .mapToInt(List::size)
            .sum();
        
        System.out.println("Found " + totalFiles + " files\n");
        
        System.out.println("Step 2: Running security analysis...");
        RuleEngine engine = new RuleEngine();
        List<Finding> findings = engine.analyzeFiles(files);
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\nStep 3: Generating report...\n");
        
        FindingCollector collector = new FindingCollector();
        collector.addFindings(findings);
        
        collector.printConsole();
        
        System.out.println("\n========================================");
        System.out.println("Analysis completed in " + duration + "ms");
        System.out.println("========================================");
    }
}
