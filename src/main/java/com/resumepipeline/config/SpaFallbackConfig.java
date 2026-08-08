package com.resumepipeline.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * Serves the React app for deep links like /applications/42.
 *
 * <p>The frontend uses BrowserRouter, so those paths hit Spring first and match no
 * static file. Resolving on 404 (rather than registering catch-all view controllers)
 * means this can never shadow a real handler — it only runs once routing has already
 * failed. /api/** is excluded so unknown API paths still return JSON errors.
 */
@Configuration
public class SpaFallbackConfig {

    @Bean
    public ErrorViewResolver spaFallback() {
        return (request, status, model) -> {
            if (status != HttpStatus.NOT_FOUND) {
                return null;
            }
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/")) {
                return null;
            }
            // ponytail: a dot means they asked for a file (favicon.ico, some.js) that
            // genuinely isn't there — 404 is the honest answer, don't hand back HTML.
            if (uri.contains(".")) {
                return null;
            }
            return new ModelAndView("forward:/index.html", Map.of(), HttpStatus.OK);
        };
    }
}
