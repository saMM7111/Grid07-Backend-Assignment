package com.sam.assigment.service;

import com.sam.assigment.domain.Actor;
import com.sam.assigment.domain.ActorType;
import com.sam.assigment.domain.Bot;
import com.sam.assigment.domain.Post;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationEngineService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEngineService.class);
    private static final Duration NOTIFICATION_COOLDOWN = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public NotificationEngineService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void handleBotInteraction(Post post, Actor actor, String interactionAction) {
        if (post.getAuthor().getActorType() != ActorType.USER || actor.getActorType() != ActorType.BOT) {
            return;
        }

        Long userId = post.getAuthor().getId();
        String message = buildNotificationMessage(actor, interactionAction);
        String cooldownKey = cooldownKey(userId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            redisTemplate.opsForList().rightPush(pendingNotificationsKey(userId), message);
            return;
        }

        log.info("Push Notification Sent to User {}: {}", userId, message);
        redisTemplate.opsForValue().set(cooldownKey, "1", NOTIFICATION_COOLDOWN);
    }

    private String buildNotificationMessage(Actor botActor, String interactionAction) {
        String botName = botActor instanceof Bot bot ? bot.getName() : "Bot-" + botActor.getId();
        return "Bot " + botName + " " + interactionAction + " your post.";
    }

    private String cooldownKey(Long userId) {
        return "user:" + userId + ":notif_cooldown";
    }

    private String pendingNotificationsKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }
}
