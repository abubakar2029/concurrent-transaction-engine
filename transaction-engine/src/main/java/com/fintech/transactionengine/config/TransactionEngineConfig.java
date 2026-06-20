package com.fintech.transactionengine.config;

import com.fintech.transactionengine.worker.TransactionWorker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class TransactionEngineConfig {

    @Bean
    public CommandLineRunner startWorkers(TransactionWorker worker) {
        return args -> {
            ExecutorService executorService = Executors.newFixedThreadPool(3);
            for (int i = 0; i < 3; i++) {
                executorService.submit(worker);
            }
        };
    }
}
