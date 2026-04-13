package aenu.ax360e;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Instrumented tests that verify the new [Vulkan] section keys added to
 * assets/config/default_config.toml in the libadrenotools integration PR:
 *
 *   • adrenotools_force_max_clocks  (default: false)
 *   • vulkan_lib_path               (default: "")
 *
 * These keys are consumed by the native Vulkan driver initialisation layer and
 * must be present so that the emulator can expose them in EmulatorSettings.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultConfigVulkanKeysTest {

    private static final String ASSET_PATH = "config/default_config.toml";

    private List<String> configLines;
    private int vulkanSectionStart = -1;

    @Before
    public void loadConfig() throws IOException {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        configLines = new ArrayList<>();

        try (InputStream is = ctx.getAssets().open(ASSET_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                configLines.add(line);
            }
        }

        // Locate the [Vulkan] section header
        for (int i = 0; i < configLines.size(); i++) {
            if (configLines.get(i).trim().equals("[Vulkan]")) {
                vulkanSectionStart = i;
                break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Config file structure
    // -----------------------------------------------------------------------

    @Test
    public void defaultConfig_exists() {
        assertFalse("default_config.toml must not be empty", configLines.isEmpty());
    }

    @Test
    public void vulkanSection_exists() {
        assertTrue(
                "default_config.toml must contain a [Vulkan] section",
                vulkanSectionStart >= 0);
    }

    // -----------------------------------------------------------------------
    // adrenotools_force_max_clocks
    // -----------------------------------------------------------------------

    @Test
    public void adrenotools_forceMaxClocks_keyExists() {
        assertTrue(
                "adrenotools_force_max_clocks key must be present in default_config.toml",
                findKeyInVulkanSection("adrenotools_force_max_clocks") >= 0);
    }

    @Test
    public void adrenotools_forceMaxClocks_isInVulkanSection() {
        int keyLine = findKeyInVulkanSection("adrenotools_force_max_clocks");
        assertTrue(
                "adrenotools_force_max_clocks must be inside the [Vulkan] section",
                keyLine > vulkanSectionStart);
    }

    @Test
    public void adrenotools_forceMaxClocks_defaultIsFalse() {
        int keyLine = findKeyInVulkanSection("adrenotools_force_max_clocks");
        assertTrue("adrenotools_force_max_clocks key must exist", keyLine >= 0);

        String line = configLines.get(keyLine).trim();
        assertTrue(
                "adrenotools_force_max_clocks must default to false, got: " + line,
                parseBoolValue(line, "adrenotools_force_max_clocks") == Boolean.FALSE);
    }

    @Test
    public void adrenotools_forceMaxClocks_hasComment() {
        int keyLine = findKeyInVulkanSection("adrenotools_force_max_clocks");
        assertTrue("adrenotools_force_max_clocks key must exist", keyLine >= 0);

        String line = configLines.get(keyLine);
        assertTrue(
                "adrenotools_force_max_clocks line should contain a comment (#), got: " + line,
                line.contains("#"));
    }

    // -----------------------------------------------------------------------
    // vulkan_lib_path
    // -----------------------------------------------------------------------

    @Test
    public void vulkanLibPath_keyExists() {
        assertTrue(
                "vulkan_lib_path key must be present in default_config.toml",
                findKeyInVulkanSection("vulkan_lib_path") >= 0);
    }

    @Test
    public void vulkanLibPath_isInVulkanSection() {
        int keyLine = findKeyInVulkanSection("vulkan_lib_path");
        assertTrue(
                "vulkan_lib_path must be inside the [Vulkan] section",
                keyLine > vulkanSectionStart);
    }

    @Test
    public void vulkanLibPath_defaultIsEmptyString() {
        int keyLine = findKeyInVulkanSection("vulkan_lib_path");
        assertTrue("vulkan_lib_path key must exist", keyLine >= 0);

        String line = configLines.get(keyLine).trim();
        // Expected token: vulkan_lib_path = ""
        String value = parseStringValue(line, "vulkan_lib_path");
        assertNotNull("vulkan_lib_path value must be parseable as a string", value);
        assertEquals(
                "vulkan_lib_path default must be an empty string, got: '" + value + "'",
                "",
                value);
    }

    @Test
    public void vulkanLibPath_hasComment() {
        int keyLine = findKeyInVulkanSection("vulkan_lib_path");
        assertTrue("vulkan_lib_path key must exist", keyLine >= 0);

        String line = configLines.get(keyLine);
        assertTrue(
                "vulkan_lib_path line should contain a comment (#), got: " + line,
                line.contains("#"));
    }

    // -----------------------------------------------------------------------
    // Ordering: adrenotools_force_max_clocks must come before vulkan_lib_path
    // within the [Vulkan] section (matches the PR diff ordering)
    // -----------------------------------------------------------------------

    @Test
    public void adrenotools_forceMaxClocks_appearsBeforeVulkanLibPath() {
        int clocksLine  = findKeyInVulkanSection("adrenotools_force_max_clocks");
        int libPathLine = findKeyInVulkanSection("vulkan_lib_path");
        assertTrue("adrenotools_force_max_clocks key must exist", clocksLine >= 0);
        assertTrue("vulkan_lib_path key must exist", libPathLine >= 0);
        assertTrue(
                "adrenotools_force_max_clocks (line " + clocksLine + ") must appear before"
                        + " vulkan_lib_path (line " + libPathLine + ") in the [Vulkan] section",
                clocksLine < libPathLine);
    }

    // -----------------------------------------------------------------------
    // Regression: pre-existing keys must still be present
    // -----------------------------------------------------------------------

    @Test
    public void existingKey_vulkanDevice_stillPresent() {
        assertTrue(
                "Existing key 'vulkan_device' must still be present in [Vulkan] section",
                findKeyInVulkanSection("vulkan_device") >= 0);
    }

    @Test
    public void existingKey_vulkanValidation_stillPresent() {
        assertTrue(
                "Existing key 'vulkan_validation' must still be present in [Vulkan] section",
                findKeyInVulkanSection("vulkan_validation") >= 0);
    }

    @Test
    public void existingKey_vulkanLogDebugMessages_stillPresent() {
        assertTrue(
                "Existing key 'vulkan_log_debug_messages' must still be present in [Vulkan] section",
                findKeyInVulkanSection("vulkan_log_debug_messages") >= 0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the index of the first line inside the [Vulkan] section that starts
     * with the given key. Returns -1 if not found or if the next section starts first.
     */
    private int findKeyInVulkanSection(String key) {
        if (vulkanSectionStart < 0) return -1;

        for (int i = vulkanSectionStart + 1; i < configLines.size(); i++) {
            String trimmed = configLines.get(i).trim();
            // Detect start of the next section
            if (trimmed.startsWith("[") && !trimmed.startsWith("[[")) {
                break;
            }
            if (trimmed.startsWith(key)) {
                // Ensure it's a key = value assignment (not a key_other prefix match)
                int len = key.length();
                if (trimmed.length() > len) {
                    char next = trimmed.charAt(len);
                    if (next == '=' || next == ' ' || next == '\t') {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Extracts and parses a boolean value from a TOML key = value line.
     * Returns null if the value cannot be determined.
     */
    private Boolean parseBoolValue(String line, String key) {
        int eq = line.indexOf('=');
        if (eq < 0) return null;
        // Strip trailing comments
        String valuePart = line.substring(eq + 1);
        int commentIdx = valuePart.indexOf('#');
        if (commentIdx >= 0) valuePart = valuePart.substring(0, commentIdx);
        valuePart = valuePart.trim();
        if ("true".equals(valuePart)) return Boolean.TRUE;
        if ("false".equals(valuePart)) return Boolean.FALSE;
        return null;
    }

    /**
     * Extracts a quoted string value from a TOML key = "value" line.
     * Returns null if no quoted value is found.
     */
    private String parseStringValue(String line, String key) {
        int eq = line.indexOf('=');
        if (eq < 0) return null;
        // Strip trailing comments
        String valuePart = line.substring(eq + 1);
        int commentIdx = valuePart.indexOf('#');
        // Be careful: the comment may be inside the string — but default_config uses
        // comments after the closing quote, so this is safe for the default cases.
        if (commentIdx >= 0) {
            // Only strip comment if it appears after a closing quote
            int closeQuote = valuePart.indexOf('"', valuePart.indexOf('"') + 1);
            if (closeQuote >= 0 && commentIdx > closeQuote) {
                valuePart = valuePart.substring(0, commentIdx);
            }
        }
        valuePart = valuePart.trim();
        if (valuePart.startsWith("\"") && valuePart.endsWith("\"")) {
            return valuePart.substring(1, valuePart.length() - 1);
        }
        return null;
    }
}