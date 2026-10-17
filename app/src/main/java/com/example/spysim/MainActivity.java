package com.example.spysim;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Replace placeholders locally; never commit real Telegram credentials to a public repository.
    private static final String BOT_TOKEN = "PASTE_TOKEN_HERE";
    private static final String CHAT_ID = "PASTE_CHAT_ID_HERE";
    private static final String FOLDER_PATH = "TelegramTestQueue";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView statusView;
    private TextView folderView;
    private Button sendButton;

    private File getTestImageFolder() {
        return new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), FOLDER_PATH);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showConsentWarning();
    }

    private void showConsentWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Test image transfer — permission required")
                .setMessage("Press Start only if you have permission to share every image in this app's "
                        + "dedicated TelegramTestQueue folder. The app will send all supported image files "
                        + "in that folder to the Telegram chat ID you enter. It will not scan your gallery "
                        + "or other device folders. Images leave this device when sent. The bot token is "
                        + "entered at runtime and is not saved by this app or committed to GitHub.")
                .setCancelable(false)
                .setPositiveButton("Continue", (dialog, which) -> showDashboard())
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    private void showDashboard() {
        File folder = getTestImageFolder();
        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 20);

        TextView heading = new TextView(this);
        heading.setText("Send test-folder images to Telegram");
        heading.setTextSize(22f);
        root.addView(heading);

        TextView description = new TextView(this);
        description.setText("Place only images you intend to transmit in the folder below or its subfolders. "
                + "Start sends every supported image in this dedicated test folder tree.");
        description.setTextSize(14f);
        description.setPadding(0, 10, 0, 12);
        root.addView(description);

        folderView = new TextView(this);
        folderView.setText("Test folder:\n" + folder.getAbsolutePath());
        folderView.setTextSize(12f);
        folderView.setPadding(0, 0, 0, 12);
        root.addView(folderView);

        sendButton = makeButton("Start sending test-folder images", this::sendFolderImages);
        root.addView(sendButton);

        statusView = new TextView(this);
        statusView.setText("Status: READY");
        statusView.setTextSize(15f);
        statusView.setPadding(0, 16, 0, 8);
        root.addView(statusView);

        root.addView(makeButton("Refresh folder count", this::updateFolderCount));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        updateFolderCount();
    }

    private Button makeButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    // Recursive local inventory: counts supported images in nested test subfolders too.
    // Scans only the app-specific test folder and its nested subfolders.
    private List<File> listImages() {
        File folder = getTestImageFolder();
        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }
        final Set<String> extensions = new HashSet<>(Arrays.asList(
                "jpg", "jpeg", "png", "webp", "gif", "bmp"));
        List<File> images = new ArrayList<>();
        collectImages(folder, extensions, images);
        Collections.sort(images, (a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
        return images;
    }

    private void collectImages(File folder, Set<String> extensions, List<File> images) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectImages(file, extensions, images);
            } else if (file.isFile()
                    && extensions.contains(fileExtension(file).toLowerCase(Locale.ROOT))) {
                images.add(file);
            }
        }
    }

    private String fileExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }

    private void updateFolderCount() {
        int count = listImages().size();
        folderView.setText("Test folder:\n" + getTestImageFolder().getAbsolutePath()
                + "\nImages ready: " + count);
        if (statusView != null) {
            statusView.setText("Status: READY — " + count + " image(s) in test folder");
        }
    }

    private void sendFolderImages() {
        String token = BOT_TOKEN.trim();
        String chatId = CHAT_ID.trim();
        if (token.isEmpty() || chatId.isEmpty()
                || token.equals("PASTE_TOKEN_HERE") || chatId.equals("PASTE_CHAT_ID_HERE")) {
            statusView.setText("Status: Set BOT_TOKEN and CHAT_ID constants in MainActivity.java first");
            return;
        }

        List<File> images = listImages();
        if (images.isEmpty()) {
            statusView.setText("Status: No images found. Put authorized test images in the folder or its subfolders.");
            return;
        }

        sendButton.setEnabled(false);
        statusView.setText("Status: Sending " + images.size() + " test-folder image(s)…");
        executor.execute(() -> {
            int sent = 0;
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                File file = images.get(i);
                try {
                    sendPhoto(token, chatId, file);
                    sent++;
                } catch (Exception e) {
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    errors.add(file.getName() + ": " + message);
                }
            }
            final int sentCount = sent;
            final List<String> failed = errors;
            runOnUiThread(() -> {
                if (failed.isEmpty()) {
                    statusView.setText("Status: Sent " + sentCount + "/" + images.size() + " image(s) successfully");
                } else {
                    String errorText = failed.toString();
                    if (errorText.length() > 500) {
                        errorText = errorText.substring(0, 500);
                    }
                    statusView.setText("Status: Sent " + sentCount + "/" + images.size() + "; failed: " + errorText);
                }
                sendButton.setEnabled(true);
            });
        });
    }

    private void sendPhoto(String token, String chatId, File file) throws IOException {
        String boundary = "----AndroidTelegram" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = (HttpURLConnection)
                new URL("https://api.telegram.org/bot" + token + "/sendPhoto").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try {
            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                writeField(out, boundary, "chat_id", chatId);
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\""
                        + file.getName().replace("\"", "") + "\"\r\n");
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
                try (FileInputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                out.writeBytes("\r\n--" + boundary + "--\r\n");
            }

            int code = connection.getResponseCode();
            java.io.InputStream responseStream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = "";
            if (responseStream != null) {
                try (java.io.InputStream input = responseStream;
                     java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        bytes.write(buffer, 0, read);
                    }
                    response = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            if (code < 200 || code >= 300 || !response.contains("\"ok\":true")) {
                throw new IOException("Telegram HTTP " + code + ": "
                        + response.substring(0, Math.min(250, response.length())));
            }
        } finally {
            connection.disconnect();
        }
    }

    private void writeField(DataOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
