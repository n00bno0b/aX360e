cat << 'INNEREOF' > app/src/main/res/layout/activity_emulator.xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <SurfaceView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:keepScreenOn="true"
        android:id="@+id/surface_view" />

    <aenu.ax360e.VirtualControl
        android:id="@+id/virtual_control"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <TextView
        android:id="@+id/perf_overlay_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|start"
        android:layout_margin="16dp"
        android:textColor="#00FF00"
        android:textSize="14sp"
        android:shadowColor="#000000"
        android:shadowDx="1.0"
        android:shadowDy="1.0"
        android:shadowRadius="2.0"
        android:visibility="gone"
        android:background="#80000000"
        android:padding="8dp"/>

</FrameLayout>
INNEREOF
