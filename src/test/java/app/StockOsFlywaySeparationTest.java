package app;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.Arrays;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Ensures legacy Flyway discovery never loads private StockOS migrations. */
class StockOsFlywaySeparationTest {
    @Test
    void legacyAndSaasMigrationDirectoriesCannotOverlap() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var legacy = Arrays.stream(resolver.getResources("classpath*:db/migration/V*.sql"))
                .map(resource -> resource.getFilename()).collect(Collectors.toSet());
        var saas = Arrays.stream(resolver.getResources("classpath*:db/saas-migration/V*.sql"))
                .map(resource -> resource.getFilename()).collect(Collectors.toSet());
        assertEquals(13, saas.size(), "Expected 13 staged SaaS migrations");
        assertEquals(11, legacy.size(), "10 legacy + auth compatibility migration");
        assertTrue(legacy.contains("V20261026_01__auth_user_identity.sql"));
        assertTrue(legacy.stream().noneMatch(n -> n.startsWith("V20261026_02")
                || n.startsWith("V20261026_03") || n.startsWith("V20261026_04")
                || n.startsWith("V20261027_")));
        assertTrue(saas.stream().noneMatch(legacy::contains), "Migration overlap");
    }
}
