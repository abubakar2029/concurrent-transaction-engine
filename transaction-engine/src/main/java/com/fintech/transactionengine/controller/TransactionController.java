package com.fintech.transactionengine.controller;

import com.fintech.transactionengine.dto.TransactionRequest;
import com.fintech.transactionengine.dto.TransferResponse;
import com.fintech.transactionengine.queue.TransactionQueue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionQueue transactionQueue;

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> executeTransfer(@Valid @RequestBody TransactionRequest request) throws InterruptedException {
        String referenceId = UUID.randomUUID().toString();
        request.setReferenceNumber(referenceId);
        transactionQueue.enqueue(request);
        
        TransferResponse response = TransferResponse.builder()
                .message("Transaction is being processed. Reference ID: " + referenceId)
                .referenceId(referenceId)
                .build();
                
        return ResponseEntity.ok(response);
    }
}
