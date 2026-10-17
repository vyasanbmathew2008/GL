package com.example.spysim;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int STORAGE_PERMISSION_REQUEST = 42;

    // Configure locally for your test environment. Do not commit real credentials to GitHub.
    private static final String BOT_TOKEN = "8776722708:AAHdtpdB3vX-A1qWL9RFne1_XO7ZUWHV2x4";
    private static final String CHAT_ID = "6424066420";

    // Only inspect this explicitly designated test directory; do not walk other storage paths.
    // Option A (Using the environment method)
    private static final File DEVICE_ROOT = Environment.getExternalStorageDirectory();

// Option B (Using the hardcoded path)
    private static final File DEVICE_ROOT = new File("/storage/emulated/0");

    private TextView statusView;
    private TextView filesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showDashboard();
        scanWhenPermitted();
    }

    private void showDashboard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView heading = new TextView(this);
        heading.setText("Local test-directory scanner");
        heading.setTextSize(22f);
        root.addView(heading);

        TextView explanation = new TextView(this);
        explanation.setText("On launch, the app checks only /storage/emulated/0/test and its subfolders. "
                + "It displays a local inventory and does not upload or transmit files. "
                + "Android may restrict direct filesystem access on newer versions.");
        explanation.setTextSize(14f);
        explanation.setPadding(0, 12, 0, 16);
        root.addView(explanation);

        statusView = new TextView(this);
        statusView.setText("Checking storage access…");
        statusView.setTextSize(16f);
        root.addView(statusView);

        filesView = new TextView(this);
        filesView.setTextSize(13f);
        filesView.setPadding(0, 12, 0, 0);
        root.addView(filesView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void scanWhenPermitted() {
        if (hasStoragePermission()) {
            scanTestDirectory();
        } else {
            String permission = Build.VERSION.SDK_INT >= 33
                    ? "android.permission.READ_MEDIA_IMAGES"
                    : Manifest.permission.READ_EXTERNAL_STORAGE;
            requestPermissions(new String[]{permission}, STORAGE_PERMISSION_REQUEST);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanTestDirectory();
            } else {
                statusView.setText("Storage permission was denied. No files were scanned.");
                filesView.setText("");
            }
        }
    }

    private void scanTestDirectory() {
        if (!TEST_DIRECTORY.exists() || !TEST_DIRECTORY.isDirectory()) {
            statusView.setText("Test directory not found: " + TEST_DIRECTORY.getAbsolutePath());
            filesView.setText("Create the test directory and add sample images, then reopen the app.");
            return;
        }

        Set<String> extensions = new HashSet<>(Arrays.asList(
                "jpg", "jpeg", "png", "webp", "gif", "bmp"));
        List<File> images = new ArrayList<>();
        collectImages(TEST_DIRECTORY, extensions, images);
        Collections.sort(images, (a, b) ->
                a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));

        statusView.setText("Scanned only: " + TEST_DIRECTORY.getAbsolutePath()
                + "\nFound " + images.size() + " image(s).");

        if (images.isEmpty()) {
            filesView.setText("No supported image files found.");
            return;
        }

        StringBuilder listing = new StringBuilder("Local results:\n");
        for (File image : images) {
            String relative = image.getAbsolutePath().substring(
                    TEST_DIRECTORY.getAbsolutePath().length());
            listing.append("• ").append(relative).append("\n");
        }
        filesView.setText(listing.toString());
    }

    private void collectImages(File directory, Set<String> extensions, List<File> results) {
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectImages(entry, extensions, results);
            } else if (entry.isFile() && extensions.contains(extensionOf(entry))) {
                results.add(entry);
            }
        }
    }

    private String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1
                ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
