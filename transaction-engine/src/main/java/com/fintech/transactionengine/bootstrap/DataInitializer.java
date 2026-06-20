package com.fintech.transactionengine.bootstrap;

import com.fintech.transactionengine.model.Account;
import com.fintech.transactionengine.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) throws Exception {
        if (accountRepository.count() == 0) {
            Account acc1 = Account.builder()
                    .accountNumber("ACC100")
                    .balance(new BigDecimal("1000.00"))
                    .ownerName("Test User 1")
                    .build();

            Account acc2 = Account.builder()
                    .accountNumber("ACC200")
                    .balance(new BigDecimal("500.00"))
                    .ownerName("Test User 2")
                    .build();

            accountRepository.save(acc1);
            accountRepository.save(acc2);
        }
    }
}
