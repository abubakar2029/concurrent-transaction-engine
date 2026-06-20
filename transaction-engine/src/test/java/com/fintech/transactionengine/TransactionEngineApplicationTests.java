package com.fintech.transactionengine;

import tools.jackson.databind.ObjectMapper;
import com.fintech.transactionengine.dto.TransactionRequest;
import com.fintech.transactionengine.model.Account;
import com.fintech.transactionengine.model.TransactionStatus;
import com.fintech.transactionengine.repository.AccountRepository;
import com.fintech.transactionengine.repository.TransactionRepository;
import com.fintech.transactionengine.service.TransactionService;
import com.fintech.transactionengine.queue.TransactionQueue;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fintech.transactionengine.dto.TransferResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionEngineApplicationTests {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionQueue transactionQueue;

    @Autowired
    private EntityManager entityManager;

    private String senderAccNum;
    private String receiverAccNum;

    @BeforeEach
    void setUp() {
        accountRepository.findByAccountNumber("ACC100").ifPresent(acc -> {
            acc.setBalance(new BigDecimal("1000.00"));
            accountRepository.save(acc);
        });
        accountRepository.findByAccountNumber("ACC200").ifPresent(acc -> {
            acc.setBalance(new BigDecimal("500.00"));
            accountRepository.save(acc);
        });

        senderAccNum = "ACC-" + UUID.randomUUID().toString().substring(0, 8);
        receiverAccNum = "ACC-" + UUID.randomUUID().toString().substring(0, 8);

        Account sender = Account.builder()
                .accountNumber(senderAccNum)
                .balance(new BigDecimal("100.00"))
                .ownerName("Sender Name")
                .build();

        Account receiver = Account.builder()
                .accountNumber(receiverAccNum)
                .balance(new BigDecimal("50.00"))
                .ownerName("Receiver Name")
                .build();

        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    @AfterEach
    void tearDown() {
        if (senderAccNum != null) {
            accountRepository.findByAccountNumber(senderAccNum).ifPresent(acc -> {
                accountRepository.delete(acc);
            });
        }
        if (receiverAccNum != null) {
            accountRepository.findByAccountNumber(receiverAccNum).ifPresent(acc -> {
                accountRepository.delete(acc);
            });
        }
        if (senderAccNum != null) {
            transactionRepository.deleteAll(transactionRepository.findAll().stream()
                    .filter(t -> t.getSenderAccountNumber().equals(senderAccNum))
                    .toList());
        }
    }

    @Test
    void contextLoads() {
    }

    @Test
    void testExecuteTransfer_Success() {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber(senderAccNum)
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("40.00"))
                .build();

        var transaction = transactionService.transferMoney(request);

        assertThat(transaction).isNotNull();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getAmount()).isEqualByComparingTo("40.00");
        assertThat(transaction.getSenderAccountNumber()).isEqualTo(senderAccNum);
        assertThat(transaction.getReceiverAccountNumber()).isEqualTo(receiverAccNum);
        assertThat(transaction.getReferenceNumber()).isNotNull();

        Account updatedSender = accountRepository.findByAccountNumber(senderAccNum).orElseThrow();
        Account updatedReceiver = accountRepository.findByAccountNumber(receiverAccNum).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo("60.00");
        assertThat(updatedReceiver.getBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void testExecuteTransfer_InsufficientBalance() {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber(senderAccNum)
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("150.00"))
                .build();

        assertThatThrownBy(() -> transactionService.transferMoney(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        Account updatedSender = accountRepository.findByAccountNumber(senderAccNum).orElseThrow();
        Account updatedReceiver = accountRepository.findByAccountNumber(receiverAccNum).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo("100.00");
        assertThat(updatedReceiver.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void testExecuteTransfer_AccountNotFound() {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber("NON-EXISTENT-ACC")
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("10.00"))
                .build();

        assertThatThrownBy(() -> transactionService.transferMoney(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sender account not found");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testApiTransfer_Success() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber(senderAccNum)
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("30.00"))
                .build();

        String responseBody = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TransferResponse response = objectMapper.readValue(responseBody, TransferResponse.class);
        assertThat(response.getMessage()).startsWith("Transaction is being processed. Reference ID: ");
        assertThat(response.getReferenceId()).isNotEmpty();

        // Wait for background worker processing
        Thread.sleep(1500);

        Account updatedSender = accountRepository.findByAccountNumber(senderAccNum).orElseThrow();
        Account updatedReceiver = accountRepository.findByAccountNumber(receiverAccNum).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo("70.00");
        assertThat(updatedReceiver.getBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void testApiTransfer_ValidationFailure_BlankAccount() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber("")
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("30.00"))
                .build();

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testApiTransfer_ValidationFailure_NegativeAmount() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber(senderAccNum)
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("-10.00"))
                .build();

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDataInitializer_SeedsDefaultAccounts() {
        var acc100 = accountRepository.findByAccountNumber("ACC100");
        var acc200 = accountRepository.findByAccountNumber("ACC200");

        assertThat(acc100).isPresent();
        assertThat(acc100.get().getBalance()).isEqualByComparingTo("1000.00");

        assertThat(acc200).isPresent();
        assertThat(acc200.get().getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void testTransactionQueue_EnqueueDequeue() throws InterruptedException {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber("SENDER1")
                .receiverAccountNumber("RECEIVER1")
                .amount(new BigDecimal("100.00"))
                .build();

        transactionQueue.putRequest(request);
        TransactionRequest retrieved = transactionQueue.takeRequest();

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getSenderAccountNumber()).isEqualTo("SENDER1");
        assertThat(retrieved.getReceiverAccountNumber()).isEqualTo("RECEIVER1");
        assertThat(retrieved.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testAsynchronousTransactionProcessing() throws Exception {
        String asyncSenderAccNum = "ACC-ASYNC-SENDER-" + UUID.randomUUID().toString().substring(0, 8);
        String asyncReceiverAccNum = "ACC-ASYNC-RECEIVER-" + UUID.randomUUID().toString().substring(0, 8);

        Account sender = Account.builder()
                .accountNumber(asyncSenderAccNum)
                .balance(new BigDecimal("200.00"))
                .ownerName("Async Sender")
                .build();

        Account receiver = Account.builder()
                .accountNumber(asyncReceiverAccNum)
                .balance(new BigDecimal("50.00"))
                .ownerName("Async Receiver")
                .build();

        accountRepository.save(sender);
        accountRepository.save(receiver);

        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber(asyncSenderAccNum)
                .receiverAccountNumber(asyncReceiverAccNum)
                .amount(new BigDecimal("100.00"))
                .build();

        transactionQueue.putRequest(request);

        // Wait for background worker processing
        Thread.sleep(1500);

        Account updatedSender = accountRepository.findByAccountNumber(asyncSenderAccNum).orElseThrow();
        Account updatedReceiver = accountRepository.findByAccountNumber(asyncReceiverAccNum).orElseThrow();

        try {
            assertThat(updatedSender.getBalance()).isEqualByComparingTo("100.00");
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo("150.00");
        } finally {
            // Clean up resources from persistent DB
            accountRepository.delete(updatedSender);
            accountRepository.delete(updatedReceiver);
            transactionRepository.deleteAll(transactionRepository.findAll().stream()
                    .filter(t -> t.getSenderAccountNumber().equals(asyncSenderAccNum))
                    .toList());
        }
    }
}
