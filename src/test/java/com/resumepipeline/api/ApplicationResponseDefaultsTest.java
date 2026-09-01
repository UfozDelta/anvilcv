package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.ApplicationResponse;
import com.resumepipeline.application.Application;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application scored before the recruiter critique fields existed carries empty arrays
 * and null scalars for them. The detail page reads those fields unconditionally, so the
 * mapping has to hand back empty collections rather than nulls or a thrown NPE.
 */
class ApplicationResponseDefaultsTest {

    @Test
    void bareApplicationMapsWithoutNullCollections() {
        ApplicationResponse r = ApplicationResponse.from(new Application());

        assertTrue(r.recruiterWeaknesses().isEmpty());
        assertTrue(r.recruiterBulletVerdicts().isEmpty());
        assertNull(r.recruiterThinnestRequirement());
        assertNull(r.recruiterWeakestBulletId());
        assertNull(r.recruiterScore());
        assertNull(r.pageCount());
        assertEquals(0, r.recruiterDimensions().size());
    }
}
