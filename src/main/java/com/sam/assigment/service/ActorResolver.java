package com.sam.assigment.service;

import com.sam.assigment.api.exception.ResourceNotFoundException;
import com.sam.assigment.domain.Actor;
import com.sam.assigment.domain.ActorType;
import com.sam.assigment.repository.BotRepository;
import com.sam.assigment.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ActorResolver {

    private final UserRepository userRepository;
    private final BotRepository botRepository;

    public ActorResolver(UserRepository userRepository, BotRepository botRepository) {
        this.userRepository = userRepository;
        this.botRepository = botRepository;
    }

    public Actor resolve(ActorType actorType, Long actorId) {
        return switch (actorType) {
            case USER -> userRepository.findById(actorId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorId));
            case BOT -> botRepository.findById(actorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bot not found: " + actorId));
        };
    }
}
