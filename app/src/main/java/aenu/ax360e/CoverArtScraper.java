package aenu.ax360e;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class CoverArtScraper {
    private static final String TAG = "CoverArtScraper";
    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB limit
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 15000; // 15 seconds

    // Using Xbox Marketplace images (these are publicly available)
    private static final String XBOX_MARKETPLACE_URL = "https://marketplace.xbox.com/en-US/Product/%s/66acd000-77fe-1000-9115-%s";

    // Alternative: Use a generic cover art API
    private static final String COVER_ART_SEARCH_URL = "https://images.igdb.com/igdb/image/upload/t_cover_big/%s.jpg";

    private final Context context;
    private final File coverArtCacheDir;

    public CoverArtScraper(Context context) {
        this.context = context;
        this.coverArtCacheDir = new File(Application.get_app_data_dir(), "cover_art");
        if (!coverArtCacheDir.exists()) {
            coverArtCacheDir.mkdirs();
        }
    }

    public interface CoverArtCallback {
        void onSuccess(File coverArtFile);
        void onFailure(String error);
    }

    /**
     * Downloads cover art for a game by its name
     */
    public void downloadCoverArt(String gameName, String gameUri, CoverArtCallback callback) {
        new Thread(() -> {
            try {
                // Create a safe filename from the game URI
                String safeFilename = getSafeFilename(gameUri);
                File coverArtFile = new File(coverArtCacheDir, safeFilename + ".jpg");

                // Check if we already have the cover art cached
                if (coverArtFile.exists()) {
                    Log.d(TAG, "Cover art already cached for: " + gameName);
                    if (callback != null) {
                        callback.onSuccess(coverArtFile);
                    }
                    return;
                }

                // Try to download from multiple sources
                boolean success = false;

                // First, try to extract title ID from game name or use a search
                // For now, we'll use a simple approach with game name search
                String searchQuery = cleanGameName(gameName);

                // Try downloading (this is a placeholder - real implementation would need actual API)
                success = downloadFromGenericSource(searchQuery, coverArtFile);

                if (success && coverArtFile.exists()) {
                    Log.d(TAG, "Successfully downloaded cover art for: " + gameName);
                    if (callback != null) {
                        callback.onSuccess(coverArtFile);
                    }
                } else {
                    String error = "Failed to download cover art for: " + gameName;
                    Log.w(TAG, error);
                    if (callback != null) {
                        callback.onFailure(error);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading cover art", e);
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Downloads cover art from a direct URL
     */
    public void downloadCoverArtFromUrl(String imageUrl, String gameUri, CoverArtCallback callback) {
        new Thread(() -> {
            try {
                String safeFilename = getSafeFilename(gameUri);
                File coverArtFile = new File(coverArtCacheDir, safeFilename + ".jpg");

                if (downloadImageFromUrl(imageUrl, coverArtFile)) {
                    if (callback != null) {
                        callback.onSuccess(coverArtFile);
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure("Failed to download image from URL");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading from URL", e);
                if (callback != null) {
                    callback.onFailure(e.getMessage());
                }
            }
        }).start();
    }

    private boolean downloadFromGenericSource(String searchQuery, File outputFile) {
        // This is a placeholder implementation
        // In a real implementation, you would:
        // 1. Use an API like IGDB, TheGamesDB, or SteamGridDB
        // 2. Search for the game by name
        // 3. Download the cover art

        // For now, we'll just return false to indicate no cover art was found
        // Users can manually provide cover art URLs
        return false;
    }

    private boolean downloadImageFromUrl(String imageUrl, File outputFile) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "aX360e-Android-App");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP request failed with code: " + responseCode);
                return false;
            }

            // Check content length
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_SIZE) {
                Log.w(TAG, "Image too large: " + contentLength + " bytes");
                return false;
            }

            inputStream = connection.getInputStream();

            // Read the image data with size limit
            byte[] buffer = new byte[8192];
            int totalRead = 0;
            int bytesRead;
            java.io.ByteArrayOutputStream imageData = new java.io.ByteArrayOutputStream();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > MAX_IMAGE_SIZE) {
                    Log.w(TAG, "Image exceeds size limit during download");
                    return false;
                }
                imageData.write(buffer, 0, bytesRead);
            }

            byte[] imageBytes = imageData.toByteArray();

            // Validate it's actually an image
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                Log.w(TAG, "Downloaded data is not a valid image");
                return false;
            }

            // Save to file
            outputStream = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            bitmap.recycle();

            Log.d(TAG, "Successfully downloaded image to: " + outputFile.getPath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to download image from URL: " + imageUrl, e);
            return false;
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error closing resources", e);
            }
        }
    }

    public File getCoverArtFile(String gameUri) {
        String safeFilename = getSafeFilename(gameUri);
        File coverArtFile = new File(coverArtCacheDir, safeFilename + ".jpg");
        return coverArtFile.exists() ? coverArtFile : null;
    }

    public void deleteCoverArt(String gameUri) {
        String safeFilename = getSafeFilename(gameUri);
        File coverArtFile = new File(coverArtCacheDir, safeFilename + ".jpg");
        if (coverArtFile.exists()) {
            if (coverArtFile.delete()) {
                Log.d(TAG, "Deleted cover art for: " + gameUri);
            }
        }
    }

    private String getSafeFilename(String uri) {
        // Create a safe filename from the URI
        return String.valueOf(uri.hashCode());
    }

    private String cleanGameName(String gameName) {
        if (gameName == null) return "";
        // Remove common file extensions and clean up the name
        return gameName
            .replaceAll("\\.(iso|xex|xbe)$", "")
            .replaceAll("[^a-zA-Z0-9\\s]", " ")
            .trim();
    }
}
