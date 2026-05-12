package ai;

import models.Finding;
import models.AIStatus;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import ai.OllamaProvider.OllamaTimeoutException;
import ai.OllamaProvider.OllamaRateLimitException;
import ai.OllamaProvider.OllamaServiceUnavailableException;

public class OllamaRequestManager {
    private static final int MAX_CONCURRENT_REQUESTS = 1;
    private static final int MAX_RETRIES = 3;
    private static final int REQUEST_TIMEOUT_SECONDS = 53;
    private static final int RETRY_DELAY_MS = 3500;
    
    private final AIProvider aiProvider;
    private final Map<String, OllamaRequest> requests;
    private final PriorityBlockingQueue<OllamaRequest> requestQueue;
    private final ExecutorService executorService;
    private final ScheduledExecutorService retryExecutor;
    private final AtomicInteger activeRequests;
    
    private SwingWorker<Void, RequestUpdate> statusWorker;
    private List<StatusUpdateListener> listeners;
    
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 60;
    private static final long MAX_IDLE_TIME_SECONDS = 300;
    private Instant lastRequestTime;
    private volatile boolean connectionHealthy;

    public interface StatusUpdateListener {
        void onStatusUpdate(OllamaRequest request);
        void onBatchComplete(List<OllamaRequest> completedRequests);
    }
    
    public static class RequestUpdate {
        public final OllamaRequest request;
        public final boolean isBatchComplete;
        
        public RequestUpdate(OllamaRequest request, boolean isBatchComplete) {
            this.request = request;
            this.isBatchComplete = isBatchComplete;
        }
    }
    
    public OllamaRequestManager(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
        this.requests = new ConcurrentHashMap<>();
        this.requestQueue = new PriorityBlockingQueue<>(100, 
            Comparator.comparing(OllamaRequest::getCreatedAt));
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        this.retryExecutor = Executors.newScheduledThreadPool(1);
        this.activeRequests = new AtomicInteger(0);
        this.listeners = new CopyOnWriteArrayList<>();
        this.lastRequestTime = Instant.now();
        this.connectionHealthy = true;
        
        startProcessingThread();
        startRetryMonitor();
        startHealthCheck();
    }
    
    private void startHealthCheck() {
        retryExecutor.scheduleAtFixedRate(() -> {
            try {
                Instant now = Instant.now();
                long idleSeconds = lastRequestTime.until(now, java.time.temporal.ChronoUnit.SECONDS);
                
                // If idle too long, verify connection
                if (idleSeconds > MAX_IDLE_TIME_SECONDS) {
                    boolean available = aiProvider.isAvailable();
                    if (!connectionHealthy && available) {
                        // Connection recovered
                        connectionHealthy = true;
                        System.out.println("Ollama connection health restored");
                    } else if (connectionHealthy && !available) {
                        // Connection lost
                        connectionHealthy = false;
                        System.out.println("Ollama connection lost");
                    }
                }
                
                // Update last request time if there are active requests
                if (activeRequests.get() > 0 || !requestQueue.isEmpty()) {
                    lastRequestTime = Instant.now();
                }
            } catch (Exception e) {
                System.err.println("Error in health check: " + e.getMessage());
            }
        }, HEALTH_CHECK_INTERVAL_SECONDS, HEALTH_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public OllamaRequest submitRequest(Finding finding, String prompt, int lineNumber) {
        OllamaRequest request = new OllamaRequest(finding, prompt, lineNumber);
        requests.put(request.getRequestId(), request);
        requestQueue.offer(request);
        
        // Notify listeners
        notifyStatusUpdate(request);
        
        return request;
    }
    
    public List<OllamaRequest> submitBatch(List<Finding> findings, String promptBase, Map<Finding, Integer> lineMap) {
        List<OllamaRequest> batchRequests = new ArrayList<>();
        
        for (Finding finding : findings) {
            String prompt = String.format(promptBase, 
                finding.getTitle(), finding.getSeverity(), 
                finding.getCategory(), finding.getFilePath(), 
                finding.getEvidence());
            
            Integer lineNumber = lineMap.get(finding);
            if (lineNumber == null) lineNumber = -1;
            
            OllamaRequest request = submitRequest(finding, prompt, lineNumber);
            batchRequests.add(request);
        }
        
        return batchRequests;
    }
    
    private void startProcessingThread() {
        statusWorker = new SwingWorker<Void, RequestUpdate>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (!isCancelled()) {
                    try {
                        // Check if we can process more requests
                        if (activeRequests.get() < MAX_CONCURRENT_REQUESTS) {
                            OllamaRequest request = requestQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (request != null) {
                                // Check if request is cancelled
                                if (request.isCancelled()) {
                                    // Clean up cancelled requests
                                    request.markRemovedFromQueue();
                                    publishUpdate(request, false);
                                } else {
                                    // Process valid requests
                                    processRequest(request);
                                }
                            }
                        } else {
                            Thread.sleep(50);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("Error in request processing thread: " + e.getMessage());
                    }
                }
                return null;
            }
            
            @Override
            protected void process(List<RequestUpdate> updates) {
                for (RequestUpdate update : updates) {
                    notifyStatusUpdate(update.request);
                    if (update.isBatchComplete) {
                        notifyBatchComplete();
                    }
                }
            }
        };
        
        statusWorker.execute();
    }
    
    private void handleRateLimit(OllamaRequest request, OllamaProvider.OllamaRateLimitException e) {
        synchronized(request) {
            if (request.isCancelled()) {
                return;
            }
            
            request.setError(e.getMessage());
            request.incrementRetryCount();
            
            if (request.shouldRetry(MAX_RETRIES)) {
                request.setStatus(AIStatus.PENDING);
                // Add delay before requeue for rate limits
                retryExecutor.schedule(() -> {
                    if (!request.isCancelled()) {
                        requestQueue.offer(request);
                    }
                }, 5000, TimeUnit.MILLISECONDS);
            } else {
                request.setStatus(AIStatus.RATE_LIMITED);
                publishUpdate(request, false);
            }
        }
    }

    private void handleServiceUnavailable(OllamaRequest request, OllamaProvider.OllamaServiceUnavailableException e) {
        synchronized(request) {
            if (request.isCancelled()) {
                return;
            }
            
            request.setError(e.getMessage());
            request.incrementRetryCount();
            
            if (request.shouldRetry(MAX_RETRIES)) {
                request.setStatus(AIStatus.PENDING);
                connectionHealthy = false;
                // Longer delay for service unavailable
                retryExecutor.schedule(() -> {
                    if (!request.isCancelled() && aiProvider.isAvailable()) {
                        connectionHealthy = true;
                        requestQueue.offer(request);
                    } else if (!request.isCancelled()) {
                        // Still unavailable, retry later
                        handleServiceUnavailable(request, e);
                    }
                }, 10000, TimeUnit.MILLISECONDS);
            } else {
                request.setStatus(AIStatus.FAILED);
                publishUpdate(request, false);
            }
        }
    }

    private void processRequest(OllamaRequest request) {
        // Early check for cancellation
        if (request.isCancelled()) {
            requestQueue.remove(request);
            request.markRemovedFromQueue();
            activeRequests.decrementAndGet();
            publishUpdate(request, true);
            return;
        }
        
        activeRequests.incrementAndGet();
        lastRequestTime = Instant.now();
        
        // Atomic status update
        synchronized(request) {
            if (request.isCancelled()) {
                activeRequests.decrementAndGet();
                return;
            }
            request.setStatus(AIStatus.IN_PROGRESS);
            request.setProcessingStartedAt(Instant.now());
        }
        
        publishUpdate(request, false);
        
        executorService.submit(() -> {
            try {
                // Check cancellation before processing
                if (request.isCancelled()) {
                    return;
                }
                
                // Simulate exponential backoff for retries
                if (request.getRetryCount() > 0) {
                    long delay = RETRY_DELAY_MS * (long) Math.pow(2, request.getRetryCount() - 1);
                    Thread.sleep(Math.min(delay, 10000)); // Max 10 seconds
                }
                
                // Check cancellation after delay
                if (request.isCancelled()) {
                    return;
                }
                
                // Execute the request with cancellation support
                try {
                    AIResponse aiResponse = aiProvider.generateText(request.getPrompt(), Map.of());
                    String response = aiResponse.getText();
                    
                    // Check cancellation before processing response
                    if (request.isCancelled()) {
                        return;
                    }
                    
                    if (response == null || response.trim().isEmpty()) {
                        handleRequestFailure(request, "Empty response from Ollama");
                    } else {
                        synchronized(request) {
                            if (!request.isCancelled()) {
                                request.setResponse(response);
                                // Cast to OllamaProvider to access estimateTokenCount
                                int promptTokens = 0;
                                int responseTokens = 0;
                                if (aiProvider instanceof OllamaProvider) {
                                    promptTokens = ((OllamaProvider) aiProvider).estimateTokenCount(request.getPrompt());
                                    responseTokens = ((OllamaProvider) aiProvider).estimateTokenCount(response);
                                }
                                request.setStatus(AIStatus.COMPLETED);
                            }
                        }
                        publishUpdate(request, false);
                    }
                } catch (InterruptedException e) {
                    // Request was cancelled during processing
                    synchronized(request) {
                        if (!request.isCancelled()) {
                            request.setStatus(AIStatus.FAILED);
                            request.setError("Request interrupted");
                        }
                    }
                    publishUpdate(request, false);
                } catch (OllamaProvider.OllamaTimeoutException e) {
                    if (!request.isCancelled()) {
                        handleRequestFailure(request, e.getMessage());
                    }
                } catch (OllamaProvider.OllamaRateLimitException e) {
                    handleRateLimit(request, e);
                } catch (OllamaProvider.OllamaServiceUnavailableException e) {
                    handleServiceUnavailable(request, e);
                } catch (Exception e) {
                    if (!request.isCancelled()) {
                        handleRequestFailure(request, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                if (!request.isCancelled()) {
                    handleRequestFailure(request, e.getMessage());
                }
            } finally {
                activeRequests.decrementAndGet();
                lastRequestTime = Instant.now();
                publishUpdate(request, true);
            }
        });
    }
    
    private void handleRequestFailure(OllamaRequest request, String error) {
        request.setError(error);
        request.incrementRetryCount();
        
        if (request.shouldRetry(MAX_RETRIES)) {
            // Schedule retry with exponential backoff
            request.setStatus(AIStatus.PENDING);
            long delay = RETRY_DELAY_MS * (long) Math.pow(2, request.getRetryCount() - 1);
            retryExecutor.schedule(() -> {
                if (!request.isCancelled()) {
                    requestQueue.offer(request);
                }
            }, Math.min(delay, 30000), TimeUnit.MILLISECONDS);
        } else {
            // Final failure - determine status from error type
            if (error != null) {
                // error is a String message, not an Exception instance
                if (error.contains("Read timed out") || 
                    error.contains("connect timed out") ||
                    error.contains("timeout")) {
                    request.setStatus(AIStatus.TIMEOUT);
                } else if (error.contains("rate limit")) {
                    request.setStatus(AIStatus.RATE_LIMITED);
                } else {
                    request.setStatus(AIStatus.FAILED);
                }
            } else {
                request.setStatus(AIStatus.FAILED);
            }
        }
        
        publishUpdate(request, false);
    }
    
    public boolean retryRequest(String requestId) {
        OllamaRequest request = requests.get(requestId);
        if (request != null && request.getStatus().isRetryable()) {
            synchronized(request) {
                // Create new request object for retry to avoid state confusion
                OllamaRequest newRequest = new OllamaRequest(
                    request.getFinding(), 
                    request.getPrompt(), 
                    request.getLineNumber()
                );
                
                // Replace old request in maps
                requests.put(newRequest.getRequestId(), newRequest);
                requests.remove(requestId);
                
                // Queue new request
                requestQueue.offer(newRequest);
                
                publishUpdate(newRequest, false);
            }
            return true;
        }
        return false;
    }
    
    public boolean cancelRequest(String requestId) {
        OllamaRequest request = requests.get(requestId);
        if (request != null && request.getStatus().isCancellable()) {
            // Cancel the request
            request.cancel();
            
            // Remove from queue if present
            if (requestQueue.remove(request)) {
                request.markRemovedFromQueue();
            }
            
            // If this is the active request, we need to interrupt it
            // The thread will check cancellation status and abort
            
            publishUpdate(request, false);
            
            // Check if queue is now empty after removal
            if (requestQueue.isEmpty() && activeRequests.get() == 0) {
                notifyBatchComplete();
            }
            
            return true;
        }
        return false;
    }
    
    public boolean retryAllFailed() {
        boolean anyRetried = false;
        for (OllamaRequest request : requests.values()) {
            if (request.getStatus().isRetryable()) {
                request.resetForRetry();
                requestQueue.offer(request);
                anyRetried = true;
            }
        }
        if (anyRetried) {
            notifyBatchComplete();
        }
        return anyRetried;
    }
    
    private void startRetryMonitor() {
        retryExecutor.scheduleAtFixedRate(() -> {
            try {
                // Check for stale IN_PROGRESS requests
                Instant cutoff = Instant.now().minusSeconds(REQUEST_TIMEOUT_SECONDS + 10);
                for (OllamaRequest request : requests.values()) {
                    if (request.getStatus() == AIStatus.IN_PROGRESS && 
                        request.getUpdatedAt().isBefore(cutoff)) {
                        handleRequestFailure(request, "Stuck request timeout");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in retry monitor: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void publishUpdate(OllamaRequest request, boolean isBatchComplete) {
        if (statusWorker != null && request != null) {
            SwingUtilities.invokeLater(() -> {
                notifyStatusUpdate(request);
                if (isBatchComplete) {
                    notifyBatchComplete();
                }
            });
        }
    }
    
    private void notifyStatusUpdate(OllamaRequest request) {
        for (StatusUpdateListener listener : listeners) {
            try {
                listener.onStatusUpdate(request);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyBatchComplete() {
        List<OllamaRequest> completed = new ArrayList<>();
        for (OllamaRequest request : requests.values()) {
            if (request.getStatus().isFinal()) {
                completed.add(request);
            }
        }
        
        for (StatusUpdateListener listener : listeners) {
            try {
                listener.onBatchComplete(completed);
            } catch (Exception e) {
                System.err.println("Error notifying batch complete: " + e.getMessage());
            }
        }
    }
    
    public void addStatusUpdateListener(StatusUpdateListener listener) {
        listeners.add(listener);
    }
    
    public void removeStatusUpdateListener(StatusUpdateListener listener) {
        listeners.remove(listener);
    }
    
    public OllamaRequest getRequest(String requestId) {
        return requests.get(requestId);
    }
    
    public List<OllamaRequest> getRequestsForFinding(String findingId) {
        List<OllamaRequest> result = new ArrayList<>();
        for (OllamaRequest request : requests.values()) {
            if (request.getFinding().getId().equals(findingId)) {
                result.add(request);
            }
        }
        return result;
    }
    
    public void shutdown() {
        statusWorker.cancel(true);
        executorService.shutdownNow();
        retryExecutor.shutdownNow();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}