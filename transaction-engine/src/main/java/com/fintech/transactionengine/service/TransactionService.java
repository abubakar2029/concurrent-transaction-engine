package com.fintech.transactionengine.service;

import com.fintech.transactionengine.dto.TransactionRequest;
import com.fintech.transactionengine.model.Account;
import com.fintech.transactionengine.model.Transaction;
import com.fintech.transactionengine.model.TransactionStatus;
import com.fintech.transactionengine.repository.AccountRepository;
import com.fintech.transactionengine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction executeTransfer(TransactionRequest request) {
        Account sender = accountRepository.findByAccountNumber(request.getSenderAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Sender account not found: " + request.getSenderAccountNumber()));

        Account receiver = accountRepository.findByAccountNumber(request.getReceiverAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Receiver account not found: " + request.getReceiverAccountNumber()));

        BigDecimal amount = request.getAmount();

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in sender's account");
        }

        // Subtract from sender, add to receiver
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        // Save transaction record with status SUCCESS
        Transaction transaction = Transaction.builder()
                .referenceNumber(UUID.randomUUID().toString())
                .senderAccountNumber(sender.getAccountNumber())
                .receiverAccountNumber(receiver.getAccountNumber())
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .build();

        return transactionRepository.save(transaction);
    }
}
