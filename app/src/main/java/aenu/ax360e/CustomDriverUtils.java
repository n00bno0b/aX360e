package aenu.ax360e;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CustomDriverUtils {

    private static final String TAG = "CustomDriverUtils";
    private static final String DRIVER_DIR_NAME = "custom_drivers";
    // Decompression bomb protection: 100MB total extraction limit
    private static final long MAX_EXTRACTION_SIZE = 100 * 1024 * 1024;
    // Per-file size limit: 50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    public static File getDriverDirectory(Context context) {
        return context.getDir(DRIVER_DIR_NAME, Context.MODE_PRIVATE);
    }

    public static boolean installDriver(Context context, Uri zipUri) {
        File dir = getDriverDirectory(context);
        File stagingDir = new File(dir.getParentFile(), DRIVER_DIR_NAME + "_staging");
        if (stagingDir.exists()) {
            deleteRecursive(stagingDir);
        }
        stagingDir.mkdirs();

        String driverSoName = null;
        long totalExtractedSize = 0;
        try (InputStream is = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(is)) {
            if (is == null) {
                Log.e(TAG, "Failed to open selected ZIP URI");
                return false;
            }
            ZipEntry entry;
            String canonicalDirPath = stagingDir.getCanonicalPath();
            while ((entry = zis.getNextEntry()) != null) {
                // Reject symlinks for security
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = entry.getName();
                // Reject absolute paths and parent traversal attempts
                if (entryName.startsWith("/") || entryName.contains("..")) {
                    throw new SecurityException("Invalid entry path: " + entryName);
                }

                File file = new File(stagingDir, entryName);
                String canonicalFilePath = file.getCanonicalPath();
                if (!canonicalFilePath.startsWith(canonicalDirPath + File.separator)) {
                    throw new SecurityException("Entry is outside of the target dir: " + entryName);
                }

                // Check per-file size limit
                long entrySize = entry.getSize();
                if (entrySize > MAX_FILE_SIZE) {
                    throw new SecurityException("Entry exceeds max file size: " + entryName);
                }

                // Create parent directories if needed
                file.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    long extractedBytes = 0;
                    while ((length = zis.read(buffer)) > 0) {
                        extractedBytes += length;
                        totalExtractedSize += length;
                        // Check decompression bomb limits
                        if (totalExtractedSize > MAX_EXTRACTION_SIZE) {
                            throw new SecurityException("Extraction exceeds total size limit");
                        }
                        fos.write(buffer, 0, length);
                    }
                    // Sync file to disk
                    fos.getFD().sync();
                }
                if (entryName.endsWith(".so") && entryName.contains("vulkan")) {
                    driverSoName = entryName;
                }
                zis.closeEntry();
            }

            if (driverSoName != null) {
                generateIcdManifest(stagingDir, driverSoName);

                // Swap staging with actual driver dir using atomic move
                if (dir.exists()) {
                    deleteRecursive(dir);
                }

                // Use Files.move with ATOMIC_MOVE on API 26+, fallback to renameTo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        Files.move(stagingDir.toPath(), dir.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (UnsupportedOperationException e) {
                        // ATOMIC_MOVE not supported, fall back to renameTo
                        Log.w(TAG, "Atomic move not supported, using renameTo", e);
                        if (!stagingDir.renameTo(dir)) {
                            throw new IOException("Failed to rename staging directory to final driver directory");
                        }
                    }
                } else {
                    if (!stagingDir.renameTo(dir)) {
                        throw new IOException("Failed to rename staging directory to final driver directory");
                    }
                }

                return true;
            } else {
                Log.e(TAG, "No vulkan .so file found in zip.");
                return false;
            }
        } catch (IOException | JSONException | SecurityException e) {
            Log.e(TAG, "Failed to install driver", e);
            if (stagingDir.exists()) {
                deleteRecursive(stagingDir);
            }
            return false;
        }
    }

    private static void generateIcdManifest(File dir, String soName) throws IOException, JSONException {
        File soFile = new File(dir, soName);
        File jsonFile = new File(dir, "vk_icd.json");

        JSONObject icdObj = new JSONObject();
        icdObj.put("library_path", soFile.getAbsolutePath());
        icdObj.put("api_version", "1.3.0"); // Turnip usually supports 1.3.x

        JSONObject rootObj = new JSONObject();
        rootObj.put("file_format_version", "1.0.0");
        rootObj.put("ICD", icdObj);

        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(rootObj.toString(4));
            writer.flush();
        }
        // Sync JSON file to disk to ensure it's persisted before finalization
        try (FileOutputStream fos = new FileOutputStream(jsonFile, true)) {
            fos.getFD().sync();
        }
    }

    public static void setupDriverEnv(Context context) {
        File dir = getDriverDirectory(context);
        File icdFile = new File(dir, "vk_icd.json");
        if (icdFile.exists()) {
            try {
                String icdPath = icdFile.getAbsolutePath();
                Os.setenv("VK_ICD_FILENAMES", icdPath, true);
                Os.setenv("VK_DRIVER_FILES", icdPath, true);
                Log.i(TAG, "Custom GPU driver environment variables set:");
                Log.i(TAG, "  VK_ICD_FILENAMES=" + icdPath);
                Log.i(TAG, "  VK_DRIVER_FILES=" + icdPath);
            } catch (ErrnoException e) {
                Log.e(TAG, "Failed to set custom driver env variables (errno: " + e.errno + ")", e);
            }
        } else {
            Log.w(TAG, "Custom driver ICD file not found, skipping env setup");
        }
    }

    public static void removeDriver(Context context) {
        File dir = getDriverDirectory(context);
        deleteRecursive(dir);
    }

    public static void applyGpuPreset(Context context) {
        // Read the CustomDrivers|gpu_preset config via SharedPreferences or Emulator.Config
        // Here we modify the global Emulator config dynamically based on the preset.
        aenu.emulator.Emulator.Config config;
        try {
            config = aenu.emulator.Emulator.Config.open_config_file(Application.get_global_config_file().getAbsolutePath());
        } catch (aenu.emulator.Emulator.ConfigFileException e) {
            Log.e(TAG, "Failed to load global config file for GPU preset", e);
            return;
        }

        String preset = config.load_config_entry("CustomDrivers|gpu_preset");
        if (preset == null || preset.isEmpty()) {
            preset = "balanced"; // Default
        }

        Log.i(TAG, "Applying GPU preset: " + preset);
        if ("strict".equals(preset)) {
            config.save_config_entry("GPU|render_target_path_vulkan", "fsi");
            config.save_config_entry("GPU|depth_float24_convert_in_pixel_shader", "true");
            config.save_config_entry("GPU|mrt_edram_used_range_clamp_to_min", "true");
        } else if ("balanced".equals(preset)) {
            config.save_config_entry("GPU|render_target_path_vulkan", "fbo");
            config.save_config_entry("GPU|depth_float24_convert_in_pixel_shader", "false");
            config.save_config_entry("GPU|mrt_edram_used_range_clamp_to_min", "false");
        } else if ("fast".equals(preset)) {
            config.save_config_entry("GPU|render_target_path_vulkan", "any");
            config.save_config_entry("GPU|depth_float24_convert_in_pixel_shader", "false");
            config.save_config_entry("GPU|mrt_edram_used_range_clamp_to_min", "false");
            config.save_config_entry("GPU|execute_unclipped_draw_vs_on_cpu", "false");
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
