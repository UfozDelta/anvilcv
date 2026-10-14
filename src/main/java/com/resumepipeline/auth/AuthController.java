package com.resumepipeline.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegisterRateLimiter rateLimiter;
    private final LoginRateLimiter loginLimiter;
    private final SecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authManager,
                          AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          RegisterRateLimiter rateLimiter,
                          LoginRateLimiter loginLimiter) {
        this.authManager = authManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.loginLimiter = loginLimiter;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password
    ) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody @Valid LoginRequest req,
                                     HttpServletRequest httpReq, HttpServletResponse httpResp) {
        String ip = httpReq.getRemoteAddr();
        if (loginLimiter.isBlocked(ip)) {
            log.warn("AUTH_RATELIMIT ip={} route=login", ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed logins");
        }
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
            // Rotate session ID to prevent session fixation.
            httpReq.getSession(true); // ensure session exists before rotating
            httpReq.changeSessionId();
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            contextRepo.saveContext(ctx, httpReq, httpResp);
            loginLimiter.recordSuccess(ip);
            log.info("AUTH_LOGIN user={} ok=true", req.username());
            return identity(auth);
        } catch (AuthenticationServiceException e) {
            // Infrastructure failure (e.g. DB down), not bad credentials: surface as an error, not a 401.
            throw e;
        } catch (AuthenticationException e) {
            loginLimiter.recordFailure(ip);
            log.warn("AUTH_LOGIN user={} ok=false", req.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody @Valid RegisterRequest req,
                                        HttpServletRequest httpReq, HttpServletResponse httpResp) {
        String ip = httpReq.getRemoteAddr();
        if (!rateLimiter.tryConsume(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many registration attempts");
        }
        if (userRepository.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        AppUser user = new AppUser(req.username(), req.email(), passwordEncoder.encode(req.password()));
        userRepository.save(user);

        // Log the new user in immediately.
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        httpReq.getSession(true);
        httpReq.changeSessionId();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, httpReq, httpResp);

        log.info("AUTH_REGISTER user={}", req.username());
        return identity(auth);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return identity(auth);
    }

    /**
     * Shape returned by every endpoint the frontend uses to learn who it is talking as.
     * {@code isAdmin} gates the admin nav link and route client-side; the server still
     * enforces the role on {@code /api/admin/**} independently.
     */
    private static Map<String, Object> identity(Authentication auth) {
        boolean admin = auth.getPrincipal() instanceof AppUserPrincipal p && p.isAdmin();
        return Map.of("username", auth.getName(), "isAdmin", admin);
    }
}
