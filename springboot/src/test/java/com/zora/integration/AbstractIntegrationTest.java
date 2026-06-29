package com.zora.integration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类（Phase 5.4）
 * <p>启动真实 MySQL 8 + Redis 7 容器，验证组件协作边界。</p>
 * <p>所有 AI 模型 Bean 用 @MockitoBean mock 掉，避免调外部 API。</p>
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // Mock 所有 AI 模型 Bean，防止调外部 API
    @MockitoBean
    EmbeddingModel embeddingModel;
    @MockitoBean
    ChatModel chatLanguageModel;
    @MockitoBean
    StreamingChatModel streamingChatLanguageModel;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("rag.document.upload-dir", () -> "./target/test-uploads");
        // 提供占位配置值（AI Bean 已被 mock，值无实际作用）
        registry.add("rag.embedding.api-key", () -> "test-key");
        registry.add("rag.embedding.base-url", () -> "https://test.example.com");
        registry.add("rag.embedding.model-name", () -> "test-model");
        registry.add("ai.api-key", () -> "test-key");
        registry.add("ai.base-url", () -> "https://test.example.com");
        registry.add("ai.model-name", () -> "test-model");
        // 占位 JWT + Agent 配置
        registry.add("jwt.secret", () -> "test-jwt-secret-for-integration-tests-only");
        registry.add("agent.tavily.api-key", () -> "test-key");
        registry.add("agent.tools.web-search.enabled", () -> "false");
        registry.add("agent.tools.math.enabled", () -> "false");
        registry.add("agent.tools.code-execution.enabled", () -> "false");
        registry.add("agent.multi-agent.enabled", () -> "false");
        // 在 Testcontainers 空 DB 上自动执行建表 SQL
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations",
                () -> "classpath:schema-test.sql,"
                     + "classpath:db/migration/V2__chat_tables.sql,"
                     + "classpath:db/migration/V3__rag_tables.sql,"
                     + "classpath:db/migration/V4__agent_tables.sql,"
                     + "classpath:db/migration/V5__search_index.sql,"
                     + "classpath:db/migration/V6__user_action_log.sql,"
                     + "classpath:db/migration/V7__multi_model.sql");
    }
}
