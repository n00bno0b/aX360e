cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '1,103p' > app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
cat << 'INNEREOF' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
        Emulator.get.setup_launch_args(new String[]{
                "--storage_root="+Application.get_app_data_dir().getAbsolutePath(),
                "--config="+Application.get_global_config_file().getAbsolutePath(),
                // log_file cvar is not defined on AX360E - logging goes to logcat via log_to_logcat
        });

        GameMetadataManager metaManager = new GameMetadataManager(this);
        GameMetadata meta = metaManager.getMetadata(uri);
        try {
            Emulator.Config config = Emulator.Config.open_config_file(Application.get_global_config_file().getAbsolutePath());
            EngineProfileManager engineManager = new EngineProfileManager(this);
            String detectedEngine = engineManager.detectEngine("UNKNOWN");
            String engineToApply = meta.engineProfileOverride != null && !meta.engineProfileOverride.isEmpty() ? meta.engineProfileOverride : detectedEngine;
            engineManager.applyEngineProfile(engineToApply, config);
            config.close_config_file();
        } catch (Exception e) {}

        Emulator.get.setup_uri_info_list_file(Application.get_uri_info_list_file().getAbsolutePath());
        setContentView(R.layout.activity_emulator);
        sf = (SurfaceView) findViewById(R.id.surface_view);
        sf.getHolder().addCallback(EmulatorActivity.this);

        sf.setFocusable(true);
        sf.setFocusableInTouchMode(true);
        sf.requestFocus();
        sf.setOnGenericMotionListener(this);

        perfOverlayText = findViewById(R.id.perf_overlay_text);
        performanceMonitor = new PerformanceMonitor(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showOverlay = prefs.getBoolean("UI|show_fps_counter", true) ||
                              prefs.getBoolean("UI|show_cpu_usage", true) ||
                              prefs.getBoolean("UI|show_memory_usage", true);

        if (showOverlay) {
            perfOverlayText.setVisibility(View.VISIBLE);
            perfHandler = new Handler();
            perfRunnable = new Runnable() {
                @Override
                public void run() {
                    performanceMonitor.updateMetrics();
                    performanceMonitor.pushMetricsToNative();

                    StringBuilder sb = new StringBuilder();
                    if (prefs.getBoolean("UI|show_fps_counter", true)) {
                        sb.append(String.format("FPS: %.1f\n", performanceMonitor.getAverageFps()));
                    }
                    if (prefs.getBoolean("UI|show_cpu_usage", true)) {
                        sb.append(String.format("CPU: %.1f%%\n", performanceMonitor.getCpuUsage()));
                    }
                    if (prefs.getBoolean("UI|show_memory_usage", true)) {
                        sb.append(String.format("MEM: %s\n", performanceMonitor.getFormattedMemoryUsage()));
                    }
                    if (prefs.getBoolean("PerfMonitoring|show_thermal_status", true)) {
                        sb.append(String.format("THRM: %s\n", performanceMonitor.getFormattedThermalStatus()));
                    }

                    perfOverlayText.setText(sb.toString());
                    perfHandler.postDelayed(this, 1000);
                }
            };
            perfHandler.postDelayed(perfRunnable, 1000);
        }

        load_key_map_and_vibrator();
    }
    void vibrator(){
INNEREOF
cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '161,$p' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
mv app/src/main/java/aenu/ax360e/EmulatorActivity.java.new app/src/main/java/aenu/ax360e/EmulatorActivity.java
