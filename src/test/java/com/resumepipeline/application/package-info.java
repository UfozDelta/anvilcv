/**
 * Service-layer unit tests run with {@code MockitoExtension}, NOT a Spring context.
 * Consequences to keep in mind when reading/writing tests here:
 *
 * <ul>
 *   <li>{@code @Async} methods (e.g. {@code ProjectService.fetchAndCacheRepoContext})
 *       run INLINE on the test thread — no proxy, no thread dispatch, and
 *       self-invocation bugs are invisible.</li>
 *   <li>No {@code @Valid} bean validation and no {@code @Transactional} semantics.</li>
 *   <li>{@code @InjectMocks} does NOT replace field-initialized collaborators:
 *       {@code ApplicationService}'s static {@code PARALLEL_EXECUTOR} (virtual threads)
 *       and its {@code new ObjectMapper()} stay real. The PDF-compile and cover-letter
 *       futures therefore execute on real threads — assert end-state/interactions only,
 *       never cross-future timing.</li>
 * </ul>
 *
 * Pure logic with no I/O lives in {@link com.resumepipeline.application.BulletSelector}
 * and is tested directly without mocks.
 */
package com.resumepipeline.application;
