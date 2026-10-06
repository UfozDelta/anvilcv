package com.resumepipeline.eval;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletLineMeasurer;
import com.resumepipeline.bullet.BulletMeasureDiagnosticRepository;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.render.PdfCompiler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the REAL bullet generator ({@link BulletService#generateBank}) without it being able to
 * touch the bullet table. Instead of a dry-run flag threaded through BulletService, this builds a
 * private BulletService whose repositories are in-memory stand-ins:
 *
 * <ul>
 *   <li>{@code save}/{@code saveAll} hand the entity back unpersisted, so generateBank returns
 *       exactly what it would have stored;
 *   <li>reads return empty, so the generator starts from an empty bank — a from-scratch run,
 *       not one told to avoid the bullets already stored;
 *   <li>anything else (delete, update, native writes) throws, so a future write path added to
 *       generation fails this eval loudly instead of writing to production.
 * </ul>
 *
 * <p>The real line measurer is replaced too: shadow-mode diagnostics would queue a tectonic
 * compile on the semaphore user previews share, for rows the stand-in repo throws away.
 *
 * <p>Coupling to the generation code is only BulletService's constructor and generateBank's
 * signature; a change to either is a compile error here, not a silent behavior change.
 */
@Component
public class DryRunGenerator {

    private final BulletService generator;

    public DryRunGenerator(ProjectService projectService, LlmClient llm, LlmUsageService usage,
                           GenerationConfigService configService, ProjectRepository projectRepo,
                           ApplicationRenderer renderer, PdfCompiler compiler,
                           ApplicationRepository applicationRepo) {
        BulletLineMeasurer noMeasure = new BulletLineMeasurer(null, null, null) {
            @Override
            public Map<String, Measured> measure(Map<String, String> textsById) {
                return Map.of();
            }
        };
        this.generator = new BulletService(
                inMemory(BulletRepository.class), projectService, llm, usage, configService,
                projectRepo, renderer, compiler, noMeasure,
                inMemory(BulletMeasureDiagnosticRepository.class), applicationRepo);
    }

    /** Unsaved bullets the current generator writes for this project and these lenses. */
    public List<Bullet> generate(UUID ownerId, UUID projectId, List<String> categories, ProgressLog progress) {
        return generator.generateBank(ownerId, projectId, categories, progress);
    }

    @SuppressWarnings("unchecked")
    static <T> T inMemory(Class<T> repoType) {
        return (T) Proxy.newProxyInstance(repoType.getClassLoader(), new Class<?>[]{repoType}, (proxy, m, args) -> {
            String name = m.getName();
            switch (name) {
                case "toString": return "DryRun(" + repoType.getSimpleName() + ")";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "save": return args[0];
                case "saveAll": {
                    List<Object> out = new ArrayList<>();
                    ((Iterable<?>) args[0]).forEach(out::add);
                    return out;
                }
                default: break;
            }
            if (name.startsWith("find") || name.startsWith("count") || name.startsWith("exists")) {
                Class<?> rt = m.getReturnType();
                if (List.class.isAssignableFrom(rt)) return List.of();
                if (rt == Optional.class) return Optional.empty();
                if (rt == long.class) return 0L;
                if (rt == boolean.class) return false;
            }
            throw new UnsupportedOperationException(
                    "Dry run blocked " + repoType.getSimpleName() + "." + name + " — eval never writes bullets");
        });
    }
}
