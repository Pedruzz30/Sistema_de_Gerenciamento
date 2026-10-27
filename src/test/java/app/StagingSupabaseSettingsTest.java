package app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StagingSupabaseSettingsTest {
    private static final String URL="https://stockos-test.supabase.co";
    private static final String ISSUER=URL+"/auth/v1";
    private static final String PUBLIC="sb_publishable_example_public_key_for_tests_only";

    @Test
    void jwtStagingRequiresSameProjectAndPublicKey() {
        assertDoesNotThrow(()->new StagingSupabaseSettings("supabase-jwt",
                ISSUER,URL,PUBLIC).validate());
        assertThrows(IllegalStateException.class,()->new StagingSupabaseSettings(
                "supabase-jwt",ISSUER,"https://another.supabase.co",PUBLIC).validate());
        assertThrows(IllegalStateException.class,()->new StagingSupabaseSettings(
                "supabase-jwt",ISSUER,"",PUBLIC).validate());
        assertThrows(IllegalStateException.class,()->new StagingSupabaseSettings(
                "supabase-jwt",ISSUER,"http://stockos-test.supabase.co",PUBLIC).validate());
    }

    @Test
    void privateKeysNeverPassThroughBrowserConfig() {
        assertThrows(IllegalStateException.class,()->new StagingSupabaseSettings(
                "supabase-jwt",ISSUER,URL,"sb_secret_example_do_not_expose").validate());
        assertThrows(IllegalStateException.class,()->new StagingSupabaseSettings(
                "legacy-dev","","","eyJservice_role_legacy_jwt").validate());
    }

    @Test
    void localLegacyWithoutSupabaseIsStillAllowed() {
        assertDoesNotThrow(()->new StagingSupabaseSettings(
                "legacy-dev","","","").validate());
    }
}
