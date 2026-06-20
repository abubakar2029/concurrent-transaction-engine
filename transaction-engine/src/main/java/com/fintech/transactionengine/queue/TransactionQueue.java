package com.fintech.transactionengine.queue;

import com.fintech.transactionengine.dto.TransactionRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class TransactionQueue {

    private final BlockingQueue<TransactionRequest> queue = new LinkedBlockingQueue<>();

    public void enqueue(TransactionRequest request) throws InterruptedException {
        queue.put(request);
    }

    public TransactionRequest dequeue() throws InterruptedException {
        return queue.take();
    }
}
