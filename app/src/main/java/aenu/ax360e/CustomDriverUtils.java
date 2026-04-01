package aenu.ax360e;

import android.content.Context;
import android.net.Uri;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CustomDriverUtils {

    private static final String TAG = "CustomDriverUtils";
    private static final String DRIVER_DIR_NAME = "custom_drivers";
    // Decompression bomb limits to prevent OOM from malicious zip files
    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 100 * 1024 * 1024; // 100MB total
    private static final long MAX_SINGLE_FILE_SIZE = 50 * 1024 * 1024; // 50MB per file

    public static File getDriverDirectory(Context context) {
        return context.getDir(DRIVER_DIR_NAME, Context.MODE_PRIVATE);
    }

    public static boolean installDriver(Context context, Uri zipUri) {
        File dir = getDriverDirectory(context);
        File stagingDir = new File(dir.getParentFile(), "staging");

        // Clean up any previous staging directory
        if (stagingDir.exists()) {
            deleteRecursive(stagingDir);
        }
        stagingDir.mkdirs();

        String driverSoName = null;
        try {
            InputStream is = context.getContentResolver().openInputStream(zipUri);
            if (is == null) {
                Log.e(TAG, "Failed to open input stream from URI");
                deleteRecursive(stagingDir);
                return false;
            }

            try (ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                String canonicalStagingPath = stagingDir.getCanonicalPath();
                long totalBytesExtracted = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        // Check for decompression bomb - individual file size
                        if (entry.getSize() > MAX_SINGLE_FILE_SIZE) {
                            Log.e(TAG, "Zip entry too large: " + entry.getName()
                                    + " (" + entry.getSize() + " bytes, max " + MAX_SINGLE_FILE_SIZE + ")");
                            deleteRecursive(stagingDir);
                            return false;
                        }

                        File file = new File(stagingDir, entry.getName());
                        String canonicalFilePath = file.getCanonicalPath();
                        if (!canonicalFilePath.startsWith(canonicalStagingPath + File.separator)) {
                            throw new SecurityException("Entry is outside of the target dir: " + entry.getName());
                        }

                        // Create parent directories if needed
                        File parentDir = file.getParentFile();
                        if (parentDir != null) parentDir.mkdirs();

                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int length;
                            long fileBytesWritten = 0;
                            while ((length = zis.read(buffer)) > 0) {
                                fileBytesWritten += length;
                                totalBytesExtracted += length;
                                // Check limits during extraction (handles unknown sizes)
                                if (fileBytesWritten > MAX_SINGLE_FILE_SIZE) {
                                    Log.e(TAG, "File exceeded max size during extraction: " + entry.getName());
                                    deleteRecursive(stagingDir);
                                    return false;
                                }
                                if (totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                                    Log.e(TAG, "Total extraction exceeded max size: " + totalBytesExtracted + " bytes");
                                    deleteRecursive(stagingDir);
                                    return false;
                                }
                                fos.write(buffer, 0, length);
                            }
                        }
                        if (entry.getName().endsWith(".so") && entry.getName().contains("vulkan")) {
                            driverSoName = entry.getName();
                        }
                    }
                    zis.closeEntry();
                }

                if (driverSoName != null) {
                    generateIcdManifest(stagingDir, driverSoName);

                    // Validation succeeded, atomically swap staging into place
                    if (dir.exists()) {
                        deleteRecursive(dir);
                    }
                    if (!stagingDir.renameTo(dir)) {
                        Log.e(TAG, "Failed to move staging directory to driver directory");
                        deleteRecursive(stagingDir);
                        return false;
                    }
                    return true;
                } else {
                    Log.e(TAG, "No vulkan .so file found in zip.");
                    deleteRecursive(stagingDir);
                    return false;
                }
            }
        } catch (IOException | JSONException | SecurityException | NullPointerException e) {
            Log.e(TAG, "Failed to install driver", e);
            deleteRecursive(stagingDir);
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
        }
    }

    public static void setupDriverEnv(Context context) {
        File dir = getDriverDirectory(context);
        File icdFile = new File(dir, "vk_icd.json");
        if (icdFile.exists()) {
            try {
                Os.setenv("VK_ICD_FILENAMES", icdFile.getAbsolutePath(), true);
                Os.setenv("VK_DRIVER_FILES", icdFile.getAbsolutePath(), true);
                Log.i(TAG, "Custom GPU driver environment variables set.");
            } catch (ErrnoException e) {
                Log.e(TAG, "Failed to set custom driver env variables", e);
            }
        }
    }

    public static void removeDriver(Context context) {
        File dir = getDriverDirectory(context);
        deleteRecursive(dir);
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