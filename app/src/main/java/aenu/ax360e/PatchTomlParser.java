// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple TOML parser for patch files
 * Only handles the subset of TOML needed for patch files
 */
public class PatchTomlParser {
    private static final String TAG = "PatchTomlParser";

    /**
     * Check if a line is a TOML key = value assignment for the given key.
     * Matches "key = ..." or "key=..." but not "key_other = ...".
     */
    private static boolean isKeyValue(String line, String key) {
        if (!line.startsWith(key)) return false;
        if (line.length() <= key.length()) return false;
        char next = line.charAt(key.length());
        return next == '=' || next == ' ' || next == '\t';
    }

    /**
     * Parse all patch files in a directory
     */
    public static List<PatchInfo.GamePatchFile> getAllPatches(File directory) {
        List<PatchInfo.GamePatchFile> allPatches = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            Log.e(TAG, "Patches directory does not exist: " + directory);
            return allPatches;
        }

        File[] files = directory.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".patch.toml"));

        if (files == null) {
            return allPatches;
        }

        for (File file : files) {
            try {
                PatchInfo.GamePatchFile gamePatch = parsePatchFile(file);
                if (gamePatch != null && !gamePatch.patches.isEmpty()) {
                    allPatches.add(gamePatch);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing patch file: " + file.getName(), e);
            }
        }

        return allPatches;
    }

    /**
     * Parse a single patch file
     */
    public static PatchInfo.GamePatchFile parsePatchFile(File file) {
        if (!file.exists() || !file.canRead()) {
            return null;
        }

        PatchInfo.GamePatchFile gamePatch = new PatchInfo.GamePatchFile();
        gamePatch.filePath = file.getAbsolutePath();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            PatchInfo currentPatch = null;
            String currentDataType = null;
            String pendingAddress = null;
            boolean inPatchSection = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse title_name
                if (isKeyValue(line, "title_name")) {
                    gamePatch.titleName = extractStringValue(line);
                }
                // Parse title_id
                else if (isKeyValue(line, "title_id")) {
                    gamePatch.titleId = extractStringValue(line);
                }
                // Parse hash
                else if (isKeyValue(line, "hash")) {
                    gamePatch.hash = extractStringValue(line);
                }
                // New patch section
                else if (line.equals("[[patch]]")) {
                    if (currentPatch != null) {
                        gamePatch.patches.add(currentPatch);
                    }
                    currentPatch = new PatchInfo();
                    currentPatch.titleId = gamePatch.titleId;
                    currentPatch.titleName = gamePatch.titleName;
                    currentPatch.filePath = file.getAbsolutePath();
                    inPatchSection = true;
                    currentDataType = null;
                    pendingAddress = null;
                }
                // Parse patch fields
                else if (inPatchSection && currentPatch != null) {
                    if (isKeyValue(line, "name")) {
                        currentPatch.patchName = extractStringValue(line);
                    } else if (isKeyValue(line, "desc")) {
                        currentPatch.description = extractStringValue(line);
                    } else if (isKeyValue(line, "author")) {
                        currentPatch.author = extractStringValue(line);
                    } else if (isKeyValue(line, "is_enabled")) {
                        currentPatch.isEnabled = extractBooleanValue(line);
                    }
                    // Data type sections
                    else if (line.matches("\\[\\[patch\\.(be8|be16|be32|be64|f32|f64|string|u16string|array)\\]\\]")) {
                        currentDataType = extractDataType(line);
                        pendingAddress = null;
                    }
                    // Data entries - state-based: collect address, then value
                    else if (currentDataType != null && isKeyValue(line, "address")) {
                        pendingAddress = extractValue(line);
                    }
                    else if (currentDataType != null && pendingAddress != null && isKeyValue(line, "value")) {
                        String value = extractValue(line);
                        currentPatch.dataEntries.add(
                            new PatchInfo.PatchDataEntry(currentDataType, pendingAddress, value));
                        pendingAddress = null;
                    }
                }
            }

            // Add last patch
            if (currentPatch != null) {
                gamePatch.patches.add(currentPatch);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading patch file: " + file.getName(), e);
            return null;
        }

        // Ensure titleName has a fallback
        if (gamePatch.titleName == null || gamePatch.titleName.isEmpty()) {
            gamePatch.titleName = file.getName().replace(".patch.toml", "");
        }
        if (gamePatch.titleId == null) {
            gamePatch.titleId = "";
        }

        return gamePatch;
    }

    /**
     * Update the is_enabled flag for a specific patch in a file
     */
    public static boolean updatePatchEnabled(File file, String patchName, boolean enabled) {
        if (!file.exists() || !file.canRead() || !file.canWrite()) {
            return false;
        }

        try {
            List<String> lines = new ArrayList<>();
            String currentPatchName = null;
            boolean modified = false;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();

                    // Detect patch section
                    if (trimmed.equals("[[patch]]")) {
                        currentPatchName = null;
                    }
                    // Track current patch name
                    else if (isKeyValue(trimmed, "name") && currentPatchName == null) {
                        currentPatchName = extractStringValue(trimmed);
                    }
                    // Update is_enabled if this is the target patch
                    else if (isKeyValue(trimmed, "is_enabled") &&
                             patchName.equals(currentPatchName)) {
                        line = "    is_enabled = " + enabled;
                        modified = true;
                    }

                    lines.add(line);
                }
            }

            if (modified) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                return true;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error updating patch file: " + file.getName(), e);
        }

        return false;
    }

    private static String extractStringValue(String line) {
        // Extract value from: key = "value"
        int start = line.indexOf('"');
        int end = line.lastIndexOf('"');
        if (start != -1 && end > start) {
            return line.substring(start + 1, end);
        }
        return "";
    }

    private static String extractValue(String line) {
        // Extract value from: key = value or key = "value"
        int equalSign = line.indexOf('=');
        if (equalSign != -1) {
            String value = line.substring(equalSign + 1).trim();
            // Remove quotes if present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return "";
    }

    private static boolean extractBooleanValue(String line) {
        String value = extractValue(line);
        return "true".equalsIgnoreCase(value);
    }

    private static String extractDataType(String line) {
        // Extract from: [[patch.be32]]
        Pattern pattern = Pattern.compile("\\[\\[patch\\.([^\\]]+)\\]\\]");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
