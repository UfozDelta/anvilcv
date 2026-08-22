package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;

import java.util.Arrays;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

/**
 * Scores a bullet against JD keywords by counting keyword occurrences in the bullet's
 * own text (word-boundary match), plus any exact tag hits as a secondary signal.
 *
 * <p>Bullet {@code tags} are coarse category labels (backend/frontend/ai-ml/devops/...),
 * while JD keywords are specific tech terms (React, Kubernetes, Postgres...) — those
 * vocabularies barely overlap, so tag-only matching was near-random. Matching keywords
 * against the bullet text itself is what actually reflects relevance.
 */
final class KeywordScorer {

    private KeywordScorer() {}

    static ToLongFunction<Bullet> score(Set<String> keywordsLower) {
        return b -> {
            String text = b.getText() == null ? "" : b.getText().toLowerCase();
            long textHits = keywordsLower.stream().filter(kw -> containsWord(text, kw)).count();
            long tagHits = Arrays.stream(b.getTags() == null ? new String[0] : b.getTags())
                    .filter(t -> keywordsLower.contains(t.toLowerCase()))
                    .count();
            return textHits * 2 + tagHits; // text match is the stronger signal
        };
    }

    private static boolean containsWord(String textLower, String keywordLower) {
        if (keywordLower.isBlank()) return false;
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keywordLower) + "(?![a-z0-9])")
                .matcher(textLower).find();
    }
}
