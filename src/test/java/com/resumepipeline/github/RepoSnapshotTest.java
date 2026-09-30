package com.resumepipeline.github;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepoSnapshotTest {

    @Test
    void readsGithubTarballStrippingTopDirAndNoise() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("src/App.java", "class App {}".getBytes());
        files.put("node_modules/x/index.js", "junk".getBytes());
        files.put("package-lock.json", "{}".getBytes());
        files.put("logo.bin", new byte[]{1, 0, 2, 0});
        String deep = "src/" + "very/".repeat(25) + "Deep.java";
        files.put(deep, "class Deep {}".getBytes());

        RepoSnapshot s = RepoSnapshot.fromTarGz(new ByteArrayInputStream(TarGz.ofBytes(files)));

        assertEquals("class App {}", s.read("src/App.java"));
        assertEquals("class Deep {}", s.read(deep), "pax long path honored");
        assertNull(s.read("node_modules/x/index.js"));
        assertNull(s.read("package-lock.json"));
        assertNull(s.read("logo.bin"), "binary content skipped");
        assertEquals(2, s.files().size());
        assertFalse(s.truncated());
    }

    @Test
    void paxRecordParsing() {
        assertEquals("a/b c.txt", RepoSnapshot.paxValue("30 mtime=1\n21 path=a/b c.txt\n", "path"));
        assertNull(RepoSnapshot.paxValue("30 mtime=1\n", "path"));
    }
}
