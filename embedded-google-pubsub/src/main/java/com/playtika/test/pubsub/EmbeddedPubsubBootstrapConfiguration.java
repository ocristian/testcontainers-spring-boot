/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Playtika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.playtika.test.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.io.IOException;
import java.util.LinkedHashMap;

import static com.playtika.test.common.utils.ContainerUtils.containerLogsConsumer;
import static java.lang.String.format;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Slf4j
@Configuration
@Order(HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "embedded.google.pubsub.enabled", matchIfMissing = true)
@EnableConfigurationProperties({PubsubProperties.class})
public class EmbeddedPubsubBootstrapConfiguration {

    @Bean(name = PubsubProperties.BEAN_NAME_EMBEDDED_GOOGLE_PUBSUB, destroyMethod = "stop")
    public GenericContainer pubsub(ConfigurableEnvironment environment,
                                   PubsubProperties properties) {
        log.info("Starting Google Cloud Pubsub emulator. Docker image: {}", properties.getDockerImage());

        GenericContainer container = new GenericContainer(properties.getDockerImage())
                .withLogConsumer(containerLogsConsumer(log))
                .withExposedPorts(properties.getPort())
                .withCommand(
                        "/bin/sh",
                        "-c",
                        format(
                                "gcloud beta emulators pubsub start --project %s --host-port=%s:%d",
                                properties.getProjectId(),
                                properties.getHost(),
                                properties.getPort()
                        )
                )
                .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*started.*$"))
                .withStartupTimeout(properties.getTimeoutDuration());

        container.start();
        registerPubsubEnvironment(container, environment, properties);
        return container;
    }

    private void registerPubsubEnvironment(GenericContainer container,
                                           ConfigurableEnvironment environment,
                                           PubsubProperties properties) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("embedded.google.pubsub.port", container.getMappedPort(properties.getPort()));
        map.put("embedded.google.pubsub.host", container.getContainerIpAddress());

        log.info("Started Google Cloud Pubsub emulator. Connection details: {}, ", map);
        log.info("Consult with the doc https://cloud.google.com/pubsub/docs/emulator for more details");

        MapPropertySource propertySource = new MapPropertySource("embeddedGooglePubsubInfo", map);
        environment.getPropertySources().addFirst(propertySource);
    }

    @Bean
    public PubSubResourcesGenerator pubSubResourcesGenerator(GenericContainer pubsub, PubsubProperties properties) throws IOException {
        return new PubSubResourcesGenerator(pubsub, properties, properties.getProjectId());
    }
}