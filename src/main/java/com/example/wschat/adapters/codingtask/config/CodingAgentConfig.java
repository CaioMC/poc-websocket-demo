package com.example.wschat.adapters.codingtask.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.wschat.core.codingtask.application.CodingTaskSettings;

/**
 * Liga a configuração ao core: o core recebe um {@link CodingTaskSettings} simples,
 * sem saber que ele veio de um application.yaml.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CodingAgentProperties.class)
public class CodingAgentConfig {

    @Bean
    CodingTaskSettings codingTaskSettings(CodingAgentProperties properties) {
        return new CodingTaskSettings(
            properties.repository(),
            properties.baseBranch(),
            properties.dispatchTimeout(),
            properties.contextMessages()
        );
    }
}
