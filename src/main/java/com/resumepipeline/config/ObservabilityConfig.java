package com.resumepipeline.config;

import com.resumepipeline.obs.MdcTaskDecorator;
import com.resumepipeline.obs.RequestLogFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

/**
 * Wires the two Layer 1 propagation pieces. {@code TaskDecorator} is picked up automatically
 * by Spring Boot's {@code TaskExecutionAutoConfiguration} for {@code applicationTaskExecutor}
 * (the pool behind {@code @Async}) — no executor bean of our own needed. The filter is a
 * {@code FilterRegistrationBean} rather than a {@code @Component}; see {@link RequestLogFilter}.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public TaskDecorator taskDecorator() {
        return new MdcTaskDecorator();
    }

    @Bean
    public FilterRegistrationBean<RequestLogFilter> requestLogFilter() {
        FilterRegistrationBean<RequestLogFilter> reg = new FilterRegistrationBean<>(new RequestLogFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
