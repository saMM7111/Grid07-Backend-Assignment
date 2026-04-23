package com.sam.assigment.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViralityScoreService {

    private final StringRedisTemplate redisTemplate;

    public ViralityScoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long applyInteraction(Long postId, ViralityInteraction interaction) {
        Long updatedScore = redisTemplate.opsForValue().increment(scoreKey(postId), interaction.points());
        if (updatedScore == null) {
            throw new IllegalStateException("Could not update virality score for post " + postId);
        }
        return updatedScore;
    }

    private String scoreKey(Long postId) {
        return "post:" + postId + ":virality_score";
    }
}
