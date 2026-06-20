package com.fintech.transactionengine.controller;

import com.fintech.transactionengine.dto.TransactionRequest;
import com.fintech.transactionengine.model.Transaction;
import com.fintech.transactionengine.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<Transaction> executeTransfer(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionService.executeTransfer(request);
        return ResponseEntity.ok(transaction);
    }
}
