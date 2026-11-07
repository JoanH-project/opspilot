package com.opspilot.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {
    @Bean
    static BeanFactoryPostProcessor productionConfigurationGuard(Environment environment) {
        return beanFactory -> ProductionConfigurationValidator.validate(environment);
    }
}
