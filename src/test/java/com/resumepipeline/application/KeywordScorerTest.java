package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordScorerTest {

    private static Bullet withText(String text) {
        return new Bullet(UUID.randomUUID(), text, new String[0], "general");
    }

    private static Bullet withTags(String... tags) {
        return new Bullet(UUID.randomUUID(), "no keywords here", tags, "general");
    }

    private static long score(Bullet b, String... keywords) {
        return KeywordScorer.score(Set.of(keywords)).applyAsLong(b);
    }

    @Nested
    class PlainMatching {

        @Test
        void exactWordInTextScoresTwo() {
            assertEquals(2, score(withText("Built a React frontend."), "react"));
        }

        @Test
        void absentKeywordScoresZero() {
            assertEquals(0, score(withText("Built a React frontend."), "kubernetes"));
        }

        @Test
        void matchIsCaseInsensitiveOnBothSides() {
            assertEquals(2, score(withText("Built a REACT frontend."), "React"));
        }

        @Test
        void eachKeywordCountedOnce() {
            assertEquals(4, score(withText("React and Redis."), "react", "redis"));
        }

        @Test
        void tagHitScoresOneAndTextHitScoresTwo() {
            Bullet b = new Bullet(UUID.randomUUID(), "Built with Redis.", new String[]{"backend"}, "general");
            assertEquals(3, score(b, "redis", "backend"));
        }

        @Test
        void emptyAndNullTextAreSafe() {
            assertEquals(0, score(withText(""), "react"));
            assertEquals(0, score(withTags("misc"), "react"));
        }

        @Test
        void substringIsNotAMatch() {
            // "go" must not match inside "mongodb" — gluing tokens must never match mid-word.
            assertEquals(0, score(withText("Stored records in MongoDB."), "go"));
        }
    }

    @Nested
    class Aliases {

        @Test
        void k8sInTextMatchesKubernetesKeyword() {
            assertEquals(2, score(withText("Deployed on K8s."), "Kubernetes"));
        }

        @Test
        void kubernetesInTextMatchesK8sKeyword() {
            assertEquals(2, score(withText("Deployed on Kubernetes."), "k8s"));
        }

        @Test
        void postgresMatchesPostgresql() {
            assertEquals(2, score(withText("Migrated to Postgres."), "PostgreSQL"));
        }

        @Test
        void awsAbbreviationMatchesSpelledOutKeyword() {
            assertEquals(2, score(withText("Ran the fleet on AWS."), "Amazon Web Services"));
        }

        @Test
        void spelledOutTextMatchesAbbreviatedKeyword() {
            assertEquals(2, score(withText("Ran on Amazon Web Services."), "AWS"));
        }

        @Test
        void aliasAppliesToTagsToo() {
            assertEquals(1, score(withTags("k8s"), "Kubernetes"));
        }
    }

    @Nested
    class Punctuation {

        @Test
        void ciCdMatches() {
            // The old word-boundary regex could never match this — the slash guaranteed a miss.
            assertEquals(2, score(withText("Owned the CI/CD pipeline."), "CI/CD"));
        }

        @Test
        void nodeDotJsMatchesNodeJs() {
            assertEquals(2, score(withText("Built the Node.js service."), "NodeJS"));
        }

        @Test
        void cPlusPlusDoesNotCollapseToBareC() {
            // Stripping punctuation naively turns both "C++" and "C#" into "c".
            assertEquals(0, score(withText("Wrote it in C++."), "C#"));
            assertEquals(2, score(withText("Wrote it in C++."), "C++"));
        }

        @Test
        void bareCIsDistinctFromCPlusPlus() {
            assertEquals(0, score(withText("Wrote it in C."), "C++"));
            assertEquals(2, score(withText("Wrote it in C."), "C"));
        }

        @Test
        void aspDotNetMatchesAcrossThePunctuationSplit() {
            assertEquals(2, score(withText("Ported the ASP.NET app."), "ASP.NET"));
        }

        @Test
        void hyphenatedTextStillMatchesTheBareTerm() {
            assertEquals(2, score(withText("Used React-Leaflet for maps."), "React"));
        }
    }

    @Nested
    class MultiWord {

        @Test
        void twoWordKeywordMatchesAdjacentTokens() {
            assertEquals(2, score(withText("Applied machine learning to ranking."), "machine learning"));
        }

        @Test
        void mlAbbreviationMatchesMachineLearning() {
            assertEquals(2, score(withText("Applied ML to ranking."), "machine learning"));
        }

        @Test
        void nonAdjacentWordsDoNotMatchMultiWordKeyword() {
            assertTrue(score(withText("Machine failure and deep learning."), "machine learning") == 0);
        }
    }
}
