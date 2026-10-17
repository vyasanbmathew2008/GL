package com.example.spysim;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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

    // Points to the root of shared storage (/storage/emulated/0)
    private static final File TEST_DIRECTORY = Environment.getExternalStorageDirectory();

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
        heading.setText("Local device storage scanner");
        heading.setTextSize(22f);
        root.addView(heading);

        TextView explanation = new TextView(this);
        explanation.setText("On launch, the app checks the root storage directory and its subfolders. "
                + "It displays a local inventory and does not upload or transmit files. "
                + "Android may require All Files Access permission on newer versions.");
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST);
                }
            } else {
                String permission = Build.VERSION.SDK_INT >= 33
                        ? "android.permission.READ_MEDIA_IMAGES"
                        : Manifest.permission.READ_EXTERNAL_STORAGE;
                requestPermissions(new String[]{permission}, STORAGE_PERMISSION_REQUEST);
            }
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
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
            if (hasStoragePermission()) {
                scanTestDirectory();
            } else {
                statusView.setText("Storage permission was denied. No files were scanned.");
                filesView.setText("");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (hasStoragePermission()) {
                scanTestDirectory();
            } else {
                statusView.setText("Storage permission was denied. No files were scanned.");
                filesView.setText("");
            }
        }
    }

    private void scanTestDirectory() {
        if (!TEST_DIRECTORY.exists() || !TEST_DIRECTORY.isDirectory()) {
            statusView.setText("Directory not found: " + TEST_DIRECTORY.getAbsolutePath());
            filesView.setText("Ensure storage access is permitted.");
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
