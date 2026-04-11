cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '1,50p' > app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
cat << 'INNEREOF' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
    private PerformanceMonitor performanceMonitor;
    private android.widget.TextView perfOverlayText;
    private Handler perfHandler;
    private Runnable perfRunnable;
INNEREOF
cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '51,$p' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
mv app/src/main/java/aenu/ax360e/EmulatorActivity.java.new app/src/main/java/aenu/ax360e/EmulatorActivity.java
