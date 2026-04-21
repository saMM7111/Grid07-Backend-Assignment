package com.sam.assigment.config;

import com.sam.assigment.domain.Bot;
import com.sam.assigment.domain.User;
import com.sam.assigment.repository.BotRepository;
import com.sam.assigment.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapDataConfig {

    @Bean
    CommandLineRunner seedActors(UserRepository userRepository, BotRepository botRepository) {
        return args -> {
            // Seed a minimal actor set so the Phase 1 endpoints can be tested immediately.
            if (userRepository.count() == 0) {
                userRepository.save(new User("alice", false));
                userRepository.save(new User("bob", true));
            }

            if (botRepository.count() == 0) {
                botRepository.save(new Bot("zen-bot", "Friendly assistant focused on concise replies"));
            }
        };
    }
}
