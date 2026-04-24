package com.sam.assigment.service;

import com.sam.assigment.domain.Actor;
import com.sam.assigment.domain.ActorType;
import com.sam.assigment.domain.Bot;
import com.sam.assigment.domain.Post;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationEngineService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEngineService.class);
    private static final String PENDING_NOTIFICATIONS_KEY_PATTERN = "user:*:pending_notifs";

    private final StringRedisTemplate redisTemplate;
    private final Duration notificationCooldown;

    public NotificationEngineService(
            StringRedisTemplate redisTemplate,
            @Value("${app.notifications.cooldown:15m}") Duration notificationCooldown
    ) {
        this.redisTemplate = redisTemplate;
        this.notificationCooldown = notificationCooldown;
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
        redisTemplate.opsForValue().set(cooldownKey, "1", notificationCooldown);
    }

    @Scheduled(cron = "${app.notifications.sweep-cron:0 */5 * * * *}")
    public void sweepPendingNotifications() {
        Set<String> pendingKeys = redisTemplate.keys(PENDING_NOTIFICATIONS_KEY_PATTERN);
        if (pendingKeys == null || pendingKeys.isEmpty()) {
            return;
        }

        for (String pendingKey : pendingKeys) {
            List<String> pendingMessages = drainPendingMessages(pendingKey);
            if (pendingMessages.isEmpty()) {
                redisTemplate.delete(pendingKey);
                continue;
            }

            String leadBot = extractLeadBotLabel(pendingMessages.get(0));
            int othersCount = Math.max(0, pendingMessages.size() - 1);

            log.info("Summarized Push Notification: {} and [{}] others interacted with your posts.", leadBot, othersCount);
            redisTemplate.delete(pendingKey);
        }
    }

    private String buildNotificationMessage(Actor botActor, String interactionAction) {
        String botName = botActor instanceof Bot bot ? bot.getName() : "Bot-" + botActor.getId();
        return "Bot " + botName + " " + interactionAction + " your post.";
    }

    private List<String> drainPendingMessages(String listKey) {
        List<String> messages = new ArrayList<>();
        String message;

        while ((message = redisTemplate.opsForList().leftPop(listKey)) != null) {
            messages.add(message);
        }

        return messages;
    }

    private String extractLeadBotLabel(String message) {
        if (message == null || message.isBlank()) {
            return "Bot";
        }

        String trimmed = message.trim();
        if (!trimmed.startsWith("Bot ")) {
            return "Bot";
        }

        String withoutPrefix = trimmed.substring(4);
        String[] knownSuffixes = new String[]{
                " replied to your post.",
                " liked your post.",
                " interacted with your post."
        };

        for (String suffix : knownSuffixes) {
            int suffixIndex = withoutPrefix.indexOf(suffix);
            if (suffixIndex > 0) {
                return "Bot " + withoutPrefix.substring(0, suffixIndex).trim();
            }
        }

        int firstSpace = withoutPrefix.indexOf(' ');
        if (firstSpace > 0) {
            return "Bot " + withoutPrefix.substring(0, firstSpace);
        }

        return "Bot " + withoutPrefix;
    }

    private String cooldownKey(Long userId) {
        return "user:" + userId + ":notif_cooldown";
    }

    private String pendingNotificationsKey(Long userId) {
        return "user:" + userId + ":pending_notifs";
    }
}
