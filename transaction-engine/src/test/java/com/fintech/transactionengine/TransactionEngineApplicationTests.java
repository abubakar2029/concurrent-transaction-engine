package com.fintech.transactionengine;

import tools.jackson.databind.ObjectMapper;
import com.fintech.transactionengine.dto.TransactionRequest;
import com.fintech.transactionengine.model.Account;
import com.fintech.transactionengine.model.TransactionStatus;
import com.fintech.transactionengine.repository.AccountRepository;
import com.fintech.transactionengine.repository.TransactionRepository;
import com.fintech.transactionengine.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private String senderAccNum;
    private String receiverAccNum;

    @BeforeEach
    void setUp() {
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

        var transaction = transactionService.executeTransfer(request);

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

        assertThatThrownBy(() -> transactionService.executeTransfer(request))
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

        assertThatThrownBy(() -> transactionService.executeTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sender account not found");
    }

    @Test
    void testApiTransfer_Success() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .senderAccountNumber(senderAccNum)
                .receiverAccountNumber(receiverAccNum)
                .amount(new BigDecimal("30.00"))
                .build();

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.senderAccountNumber").value(senderAccNum))
                .andExpect(jsonPath("$.receiverAccountNumber").value(receiverAccNum))
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.referenceNumber").isNotEmpty());

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
}
