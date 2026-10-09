package com.orderflow.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton containers: started once per JVM and shared by every integration test, so the suite pays the
 * container start-up cost only once. Testcontainers' Ryuk sidecar removes them when the JVM exits.
 */
public final class SharedContainers {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    public static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        POSTGRES.start();
        REDIS.start();
        RABBIT.start();
    }

    private SharedContainers() {
    }
}
