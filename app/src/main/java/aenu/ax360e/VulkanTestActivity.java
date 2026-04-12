package aenu.ax360e;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Button;
import androidx.annotation.NonNull;

public class VulkanTestActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "VulkanTestActivity";
    private SurfaceView surfaceView;
    private TextView statusText;
    private TextView driverInfo;
    private volatile boolean isRunning = false;
    private Thread testThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vulkan_test);

        surfaceView = findViewById(R.id.surface_view);
        statusText = findViewById(R.id.status_text);
        driverInfo = findViewById(R.id.driver_info);
        Button btnClose = findViewById(R.id.btn_close);

        surfaceView.getHolder().addCallback(this);
        btnClose.setOnClickListener(v -> finish());

        // Apply launch environment (drivers, etc.)
        LaunchEnvironmentResolver resolver = new LaunchEnvironmentResolver(this);
        // Use a dummy game URI to trigger global driver settings
        resolver.resolveAndApply("vulkan_test");

        // Ensure native library is loaded
        Emulator.load_library();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (testThread == null || !testThread.isAlive()) {
            startVulkanTest(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // Handle resize if needed
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopVulkanTest();
    }

    private void startVulkanTest(Surface surface) {
        if (isRunning) {
            return;
        }
        isRunning = true;
        testThread = new Thread(() -> {
            try {
                String result = nRunVulkanTest(surface);
                String deviceInfo = nGetVulkanDeviceInfo();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    statusText.setText("Result: " + result);
                    driverInfo.setText("GPU: " + deviceInfo);
                });
            } finally {
                isRunning = false;
                testThread = null;
            }
        });
        testThread.start();
    }

    private void stopVulkanTest() {
        isRunning = false;
        Thread thread = testThread;
        if (thread != null) {
            nCancelVulkanTest();
            try {
                thread.join(300);
            } catch (InterruptedException e) {
                Log.w(TAG, "join interrupted", e);
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopVulkanTest();
        super.onDestroy();
    }

    private native String nRunVulkanTest(Surface surface);
    private native void nCancelVulkanTest();
    private native String nGetVulkanDeviceInfo();
}
