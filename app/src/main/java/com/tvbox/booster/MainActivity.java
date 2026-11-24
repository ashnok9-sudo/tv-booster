package com.tvbox.booster;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private TextView textStatus;
    private TextView textRamStats;
    private TextView textAppsStopped;
    private Button btnBoost;
    private Button btnBoostLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatus = findViewById(R.id.text_status);
        textRamStats = findViewById(R.id.text_ram_stats);
        textAppsStopped = findViewById(R.id.text_apps_stopped);
        btnBoost = findViewById(R.id.btn_boost);
        btnBoostLaunch = findViewById(R.id.btn_boost_launch);

        updateMemoryStats(false, 0, 0);

        // Pre-focus primary button for TV remote control D-Pad
        btnBoost.requestFocus();

        btnBoost.setOnClickListener(v -> performBoost(false));
        btnBoostLaunch.setOnClickListener(v -> performBoost(true));
    }

    private void updateMemoryStats(boolean boosted, long ramFreedMB, int appsKilled) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            long totalMB = mi.totalMem / (1024 * 1024);
            long availMB = mi.availMem / (1024 * 1024);

            textRamStats.setText("Free RAM: " + availMB + " MB / " + totalMB + " MB");

            if (boosted) {
                textStatus.setText("Device Cooled!\nCPU load reduced & memory freed.");
                textAppsStopped.setText("Apps Stopped: " + appsKilled + " (Freed +" + Math.max(0, ramFreedMB) + " MB)");
            } else {
                textStatus.setText(getString(R.string.ready_status));
                textAppsStopped.setText("System ready. Press Boost to clear background tasks.");
            }
        }
    }

    private void performBoost(boolean launchNuvio) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = getPackageManager();

        if (am == null || pm == null) return;

        ActivityManager.MemoryInfo beforeMi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(beforeMi);
        long beforeAvailMB = beforeMi.availMem / (1024 * 1024);

        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String myPackage = getPackageName();
        int killedCount = 0;

        for (ApplicationInfo info : packages) {
            // Do not kill self or critical system kernel
            if (info.packageName.equals(myPackage)) continue;
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            am.killBackgroundProcesses(info.packageName);
            killedCount++;
        }

        // Force JVM Garbage Collector and Finalizer to drop dead references
        System.runFinalization();
        System.gc();

        ActivityManager.MemoryInfo afterMi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(afterMi);
        long afterAvailMB = afterMi.availMem / (1024 * 1024);
        long freedMB = afterAvailMB - beforeAvailMB;

        updateMemoryStats(true, freedMB, killedCount);

        Toast.makeText(this, "Cleaned & Cooled! Stopped " + killedCount + " background apps.", Toast.LENGTH_SHORT).show();

        if (launchNuvio) {
            new Handler(Looper.getMainLooper()).postDelayed(this::findAndLaunchNuvio, 1200);
        }
    }

    private void findAndLaunchNuvio() {
        PackageManager pm = getPackageManager();
        // Common package names for Nuvio builds
        String[] candidatePackages = new String[] {
            "tv.nuvio",
            "com.nuvio",
            "com.nuviomedia.nuvio",
            "com.nuvio.app",
            "media.nuvio"
        };

        Intent launchIntent = null;

        for (String pkg : candidatePackages) {
            launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) break;
        }

        // Fallback: search all installed packages for "nuvio" in name
        if (launchIntent == null) {
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo app : apps) {
                if (app.packageName.toLowerCase().contains("nuvio")) {
                    launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) break;
                }
            }
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            finish(); // Close booster so Nuvio gets all RAM
        } else {
            Toast.makeText(this, "Nuvio not detected. Please install Nuvio APK.", Toast.LENGTH_LONG).show();
        }
    }
}
