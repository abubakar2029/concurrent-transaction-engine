package com.fintech.transactionengine.worker;

import com.fintech.transactionengine.dto.TransactionRequest;
import com.fintech.transactionengine.queue.TransactionQueue;
import com.fintech.transactionengine.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionWorker implements Runnable {

    private final TransactionQueue transactionQueue;
    private final TransactionService transactionService;

    @Override
    public void run() {
        log.info("Transaction worker started on thread: {}", Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TransactionRequest request = transactionQueue.dequeue();
                log.debug("Worker picked up transaction request from sender: {} to receiver: {}", 
                        request.getSenderAccountNumber(), request.getReceiverAccountNumber());
                try {
                    transactionService.transferMoney(request);
                    log.info("Successfully processed queued transfer from {} to {}", 
                            request.getSenderAccountNumber(), request.getReceiverAccountNumber());
                } catch (Exception e) {
                    log.error("Failed to process queued transfer from {} to {}: {}", 
                            request.getSenderAccountNumber(), request.getReceiverAccountNumber(), e.getMessage());
                }
            } catch (InterruptedException e) {
                log.info("Transaction worker on thread {} interrupted, shutting down", Thread.currentThread().getName());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
