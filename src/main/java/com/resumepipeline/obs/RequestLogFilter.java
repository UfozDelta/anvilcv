package com.resumepipeline.obs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *
 * Access log is scoped to {@code /api/**}: {@code SecurityConfig} permits every request and the
 * jar serves the SPA's static assets, so an unscoped log would mostly say nothing on every page
 * load. The user is resolved from {@code SecurityContextHolder} on the way out, after
 * {@code chain.doFilter} — resolving on the way in would see an empty context on every request,
 * and resolving only for requests the chain accepts would miss the 401s/403s this exists to show.
 */
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    // X-Request-Id is unvalidated input from an unauthenticated caller. Without this whitelist,
    // \n and ANSI escapes go straight into every subsequent log line for the request.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    // Both poll every 1500ms (EventStream.tsx) — at INFO they'd drown the pipeline they annotate.
    private static final Pattern PROGRESS_POLL =
            Pattern.compile("/api/(?:applications|projects)/jobs/[^/]+/progress");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put("req", resolveRequestId(request));
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/")) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    MDC.put("user", auth.getName());
                }
                long ms = System.currentTimeMillis() - start;
                String method = request.getMethod();
                int status = response.getStatus();
                if (PROGRESS_POLL.matcher(uri).matches()) {
                    log.debug("HTTP {} {} -> {} in {}ms", method, uri, status, ms);
                } else {
                    log.info("HTTP {} {} -> {} in {}ms", method, uri, status, ms);
                }
            }
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
