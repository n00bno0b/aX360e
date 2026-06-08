package aenu.ax360e;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

public class DriverTestActivity extends Activity {
    private static final String TAG = "DriverTest";
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resultText = new TextView(this);
        resultText.setPadding(32, 32, 32, 32);
        resultText.setTextSize(14f);
        resultText.setTextIsSelectable(true);
        setContentView(resultText);

        Emulator.load_library();

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Driver Test ===\n\n");

            // 1. Check if driver is installed
            File dir = CustomDriverUtils.getDriverDirectory(this);
            File icdFile = new File(dir, "vk_icd.json");
            File soFile = new File(dir, "vulkan.purple.so");
            sb.append("Driver dir: ").append(dir.getAbsolutePath()).append("\n");
            sb.append("vk_icd.json exists: ").append(icdFile.exists()).append("\n");
            sb.append("vulkan.purple.so exists: ").append(soFile.exists()).append("\n");

            boolean installed = CustomDriverUtils.isDriverInstalled(this);
            sb.append("isDriverInstalled: ").append(installed).append("\n\n");

            // 2. Test native driver load
            if (installed) {
                sb.append("Testing native driver load...\n");
                try {
                    String result = Emulator.nativeTestDriverLoad(dir.getAbsolutePath(), "vulkan.purple.so");
                    sb.append("Result: ").append(result).append("\n\n");
                } catch (Exception e) {
                    sb.append("Error: ").append(e.getMessage()).append("\n\n");
                }
            }

            // 3. Check env vars
            sb.append("Checking environment...\n");
            try {
                String dirEnv = System.getenv("CUSTOM_DRIVER_DIR");
                String pathEnv = System.getenv("CUSTOM_DRIVER_PATH");
                sb.append("CUSTOM_DRIVER_DIR: ").append(dirEnv != null ? dirEnv : "(null)").append("\n");
                sb.append("CUSTOM_DRIVER_PATH: ").append(pathEnv != null ? pathEnv : "(null)").append("\n");
            } catch (Exception e) {
                sb.append("Env check error: ").append(e.getMessage()).append("\n");
            }

            // 4. Check installed driver info
            try {
                String installedPath = Emulator.nativeGetInstalledDriverPath();
                String installedName = Emulator.nativeGetInstalledDriverName();
                sb.append("\nNative installed path: ").append(installedPath).append("\n");
                sb.append("Native installed name: ").append(installedName).append("\n");
                sb.append("Supports libadrenotools: ").append(Emulator.nativeSupportsLibadrenotoolsBuild()).append("\n");
            } catch (Exception e) {
                sb.append("Native info error: ").append(e.getMessage()).append("\n");
            }

            Log.i(TAG, sb.toString());
            final String result = sb.toString();
            new Handler(Looper.getMainLooper()).post(() -> resultText.setText(result));
        }).start();
    }
}
