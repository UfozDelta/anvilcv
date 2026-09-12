package com.resumepipeline.obs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Registered outermost via a {@code FilterRegistrationBean} in {@code config.ObservabilityConfig},
 * not as a bare {@code @Component} — a {@code @Component} filter is picked up by the servlet
 * container and, if also wired into the security chain, would run (and log) twice per request.
 * Kept dependency-free: {@code @WebMvcTest} slices instantiate every {@code Filter} bean, so an
 * unmocked constructor dependency here fails those contexts.
 */
public class RequestLogFilter extends OncePerRequestFilter {

    // X-Request-Id is unvalidated input from an unauthenticated caller. Without this whitelist,
    // \n and ANSI escapes go straight into every subsequent log line for the request.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put("req", resolveRequestId(request));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && SAFE_REQUEST_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
