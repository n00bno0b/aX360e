cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '1,103p' > app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '108,170p' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
cat << 'INNEREOF' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
    void vibrator(){
        if(vibrator!=null) {
            vibrator.vibrate(vibrationEffect);
        }
    }

    void load_key_map_and_vibrator() {
        final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        keysMap.clear();
        for (int i = 0; i < KeyMapConfig.KEY_NAMEIDS.length; i++) {
            String keyName = Integer.toString(KeyMapConfig.KEY_NAMEIDS[i]);
            int keyCode = sPrefs.getInt(keyName, KeyMapConfig.DEFAULT_KEYMAPPERS[i]);
            keysMap.put(keyCode, KeyMapConfig.KEY_VALUES[i]);
        }
        if(sPrefs.getBoolean("enable_vibrator",false)){
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrationEffect = VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE);
        }
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!Application.should_delay_load()){
            on_create();
            return;
        }

        delay_dialog=ProgressTask.create_progress_dialog( this,getString(R.string.loading));
        delay_dialog.show();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    Emulator.load_library();
                    Thread.sleep(100);
                    delay_on_create.sendEmptyMessage(DELAY_ON_CREATE);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
        return;
    }
INNEREOF
cat app/src/main/java/aenu/ax360e/EmulatorActivity.java | sed -n '173,$p' >> app/src/main/java/aenu/ax360e/EmulatorActivity.java.new
mv app/src/main/java/aenu/ax360e/EmulatorActivity.java.new app/src/main/java/aenu/ax360e/EmulatorActivity.java
