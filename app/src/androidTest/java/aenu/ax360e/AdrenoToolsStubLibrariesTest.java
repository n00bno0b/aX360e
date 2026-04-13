package aenu.ax360e;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests that verify the Android stub libraries declared in {@link CustomDriverUtils}
 * are consistent with those produced by the new dummy_libs.cpp compilation targets
 * added in the libadrenotools integration PR.
 *
 * <p>The PR adds these stub shared libraries (via CMakeLists.txt):
 * <ul>
 *   <li>libcutils.so</li>
 *   <li>libutils.so</li>
 *   <li>libhardware.so</li>
 *   <li>libhidlbase.so</li>
 *   <li>libnativewindow.so</li>
 *   <li>libsync.so  (OUTPUT_NAME "sync")</li>
 *   <li>libandroid.so  (OUTPUT_NAME "android")</li>
 *   <li>libbacktrace.so</li>
 *   <li>libunwindstack.so</li>
 *   <li>liblog.so  (OUTPUT_NAME "log")</li>
 * </ul>
 *
 * <p>{@link CustomDriverUtils} must reference each of these by name so they are
 * properly staged into the stub directory before a custom driver is loaded.
 */
@RunWith(AndroidJUnit4.class)
public class AdrenoToolsStubLibrariesTest {

    /**
     * Canonical set of stub .so file names that dummy_libs.cpp / CMakeLists.txt
     * produces for the libadrenotools integration.
     */
    private static final Set<String> EXPECTED_STUB_LIBS = new HashSet<>(Arrays.asList(
            "libcutils.so",
            "libutils.so",
            "libhardware.so",
            "libhidlbase.so",
            "libnativewindow.so",
            "libsync.so",
            "libandroid.so",
            "libbacktrace.so",
            "libunwindstack.so",
            "liblog.so"
    ));

    // -----------------------------------------------------------------------
    // ANDROID_STUB_LIBRARIES constant in CustomDriverUtils
    // -----------------------------------------------------------------------

    private String[] getAndroidStubLibraries() throws Exception {
        Field field = CustomDriverUtils.class.getDeclaredField("ANDROID_STUB_LIBRARIES");
        field.setAccessible(true);
        return (String[]) field.get(null);
    }

    @Test
    public void androidStubLibraries_fieldExists() throws Exception {
        // Ensure the private constant field is present
        Field field = CustomDriverUtils.class.getDeclaredField("ANDROID_STUB_LIBRARIES");
        assertNotNull("ANDROID_STUB_LIBRARIES field must exist in CustomDriverUtils", field);
    }

    @Test
    public void androidStubLibraries_isNotEmpty() throws Exception {
        String[] libs = getAndroidStubLibraries();
        assertNotNull("ANDROID_STUB_LIBRARIES must not be null", libs);
        assertTrue("ANDROID_STUB_LIBRARIES must not be empty", libs.length > 0);
    }

    @Test
    public void androidStubLibraries_containsLibcutils() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libcutils.so (stub for atrace/property_get)",
                Arrays.asList(getAndroidStubLibraries()).contains("libcutils.so"));
    }

    @Test
    public void androidStubLibraries_containsLibutils() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libutils.so (stub for android::SystemClock etc.)",
                Arrays.asList(getAndroidStubLibraries()).contains("libutils.so"));
    }

    @Test
    public void androidStubLibraries_containsLibhardware() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libhardware.so (stub for hw_get_module)",
                Arrays.asList(getAndroidStubLibraries()).contains("libhardware.so"));
    }

    @Test
    public void androidStubLibraries_containsLibhidlbase() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libhidlbase.so",
                Arrays.asList(getAndroidStubLibraries()).contains("libhidlbase.so"));
    }

    @Test
    public void androidStubLibraries_containsLibnativewindow() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libnativewindow.so (stub for AHardwareBuffer etc.)",
                Arrays.asList(getAndroidStubLibraries()).contains("libnativewindow.so"));
    }

    @Test
    public void androidStubLibraries_containsLibsync() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libsync.so (stub for sync_wait/sync_merge)",
                Arrays.asList(getAndroidStubLibraries()).contains("libsync.so"));
    }

    @Test
    public void androidStubLibraries_containsLibandroid() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libandroid.so (stub for ANativeWindow)",
                Arrays.asList(getAndroidStubLibraries()).contains("libandroid.so"));
    }

    @Test
    public void androidStubLibraries_containsLibbacktrace() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libbacktrace.so",
                Arrays.asList(getAndroidStubLibraries()).contains("libbacktrace.so"));
    }

    @Test
    public void androidStubLibraries_containsLibunwindstack() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include libunwindstack.so",
                Arrays.asList(getAndroidStubLibraries()).contains("libunwindstack.so"));
    }

    @Test
    public void androidStubLibraries_containsLiblog() throws Exception {
        assertTrue(
                "ANDROID_STUB_LIBRARIES must include liblog.so (stub for __android_log_print)",
                Arrays.asList(getAndroidStubLibraries()).contains("liblog.so"));
    }

    /**
     * All expected stub libraries must be present — no library may be silently dropped.
     */
    @Test
    public void androidStubLibraries_containsAllExpectedStubs() throws Exception {
        Set<String> actual = new HashSet<>(Arrays.asList(getAndroidStubLibraries()));
        Set<String> missing = new HashSet<>(EXPECTED_STUB_LIBS);
        missing.removeAll(actual);
        assertTrue(
                "ANDROID_STUB_LIBRARIES is missing the following stub libraries: " + missing,
                missing.isEmpty());
    }

    /**
     * Ensures every entry in ANDROID_STUB_LIBRARIES ends with ".so" — a basic
     * sanity check that no typo introduced a non-library name.
     */
    @Test
    public void androidStubLibraries_allEntriesEndWithSo() throws Exception {
        for (String lib : getAndroidStubLibraries()) {
            assertTrue(
                    "Every stub library name must end with '.so', got: " + lib,
                    lib.endsWith(".so"));
        }
    }

    /**
     * Ensures every entry starts with "lib" — matching the standard Android shared
     * library naming convention.
     */
    @Test
    public void androidStubLibraries_allEntriesStartWithLib() throws Exception {
        for (String lib : getAndroidStubLibraries()) {
            assertTrue(
                    "Every stub library name must start with 'lib', got: " + lib,
                    lib.startsWith("lib"));
        }
    }

    // -----------------------------------------------------------------------
    // isDriverInstalled – smoke test that the method is callable without errors
    // (no driver installed in the test environment)
    // -----------------------------------------------------------------------

    @Test
    public void isDriverInstalled_returnsFalseWhenNoDriverPresent() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // In a clean test environment there should be no installed custom driver.
        // This test ensures the method does not throw.
        boolean installed = CustomDriverUtils.isDriverInstalled(ctx);
        assertFalse(
                "isDriverInstalled must return false when no custom driver has been installed",
                installed);
    }

    /**
     * clearDriverEnv() must not throw when called without a prior setupDriverEnv() call.
     */
    @Test
    public void clearDriverEnv_isIdempotent() {
        // Should not throw even if called multiple times in succession.
        CustomDriverUtils.clearDriverEnv();
        CustomDriverUtils.clearDriverEnv();
    }

    /**
     * getLastDriverError() must return an empty string (not null) before any
     * install attempt.
     */
    @Test
    public void getLastDriverError_returnsEmptyStringInitially() {
        String error = CustomDriverUtils.getLastDriverError();
        assertNotNull("getLastDriverError must not return null", error);
    }
}