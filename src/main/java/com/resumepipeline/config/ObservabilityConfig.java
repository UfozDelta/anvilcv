package com.resumepipeline.config;

import ch.qos.logback.classic.LoggerContext;
import com.resumepipeline.obs.MdcTaskDecorator;
import com.resumepipeline.obs.RequestLogFilter;
import com.resumepipeline.obs.RingBufferLogAppender;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * Also attaches the Layer 6 ring-buffer appender to root, alongside Boot's console appender,
 * so the admin log viewer sees the same events without a logback-spring.xml (which would mean
 * reproducing Boot's default console configuration by hand to keep it).
 */
@Configuration
public class ObservabilityConfig {

    private final RingBufferLogAppender ringBufferLogAppender;

    public ObservabilityConfig(@Value("${logging.ring-buffer.size:2000}") int ringBufferSize) {
        this.ringBufferLogAppender = new RingBufferLogAppender(ringBufferSize);
    }

    @PostConstruct
    public void attachRingBufferAppender() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ringBufferLogAppender.setContext(ctx);
        ringBufferLogAppender.start();
        ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).addAppender(ringBufferLogAppender);
    }

    @Bean
    public RingBufferLogAppender ringBufferLogAppender() {
        return ringBufferLogAppender;
    }

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
