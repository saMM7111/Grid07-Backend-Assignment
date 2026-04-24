package com.sam.assigment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sam.assigment.api.dto.AddCommentRequest;
import com.sam.assigment.api.dto.CreatePostRequest;
import com.sam.assigment.api.dto.PostResponse;
import com.sam.assigment.api.exception.BadRequestException;
import com.sam.assigment.api.exception.TooManyRequestsException;
import com.sam.assigment.domain.ActorType;
import com.sam.assigment.domain.Bot;
import com.sam.assigment.domain.User;
import com.sam.assigment.repository.BotRepository;
import com.sam.assigment.repository.CommentRepository;
import com.sam.assigment.repository.PostRepository;
import com.sam.assigment.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "phase4.it", matches = "true")
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false"
})
class Phase4CornerCasesIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BotRepository botRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanState() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
        botRepository.deleteAll();
        userRepository.deleteAll();
        clearRedis();
    }

    @Test
    void shouldStopAtExactlyHundredBotCommentsUnderConcurrency() throws InterruptedException {
        User owner = userRepository.save(new User("owner-" + UUID.randomUUID(), false));
        PostResponse post = postService.createPost(new CreatePostRequest(
                ActorType.USER,
                owner.getId(),
                "Race-condition target post"
        ));

        List<Bot> bots = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            bots.add(new Bot("load-bot-" + i, "Load-test bot #" + i));
        }
        List<Bot> savedBots = botRepository.saveAll(bots);

        ExecutorService executor = Executors.newFixedThreadPool(48);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(savedBots.size());

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (Bot bot : savedBots) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    postService.addComment(post.id(), new AddCommentRequest(
                            ActorType.BOT,
                            bot.getId(),
                            "Bot load-test comment from " + bot.getName(),
                            1
                    ));
                    accepted.incrementAndGet();
                } catch (TooManyRequestsException ex) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failed.incrementAndGet();
                } catch (RuntimeException ex) {
                    failed.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(doneGate.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        long persistedBotComments = commentRepository.countByPostIdAndAuthor_ActorType(post.id(), ActorType.BOT);
        assertThat(persistedBotComments).isEqualTo(100);
        assertThat(accepted.get()).isEqualTo(100);
        assertThat(rejected.get()).isEqualTo(100);
        assertThat(failed.get()).isZero();
    }

    @Test
    void shouldRollbackRedisReservationWhenBotCommentValidationFails() {
        User owner = userRepository.save(new User("owner-" + UUID.randomUUID(), false));
        Bot bot = botRepository.save(new Bot("validator-bot", "Validation test bot"));
        PostResponse post = postService.createPost(new CreatePostRequest(
                ActorType.USER,
                owner.getId(),
                "Validation target post"
        ));

        assertThatThrownBy(() -> postService.addComment(post.id(), new AddCommentRequest(
                ActorType.BOT,
                bot.getId(),
                "   ",
                1
        ))).isInstanceOf(BadRequestException.class);

        long persistedBotComments = commentRepository.countByPostIdAndAuthor_ActorType(post.id(), ActorType.BOT);
        assertThat(persistedBotComments).isZero();

        String botCountKey = "post:" + post.id() + ":bot_count";
        String cooldownKey = "cooldown:bot_" + bot.getId() + ":human_" + owner.getId();

        String botCountValue = redisTemplate.opsForValue().get(botCountKey);
        assertThat(botCountValue == null || "0".equals(botCountValue)).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))).isFalse();
    }

    private void clearRedis() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }
}
