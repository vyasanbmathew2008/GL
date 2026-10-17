package com.example.spysim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public final class SyntheticImageFactory {
    private SyntheticImageFactory() { }

    public static File create(Context context, int index) throws IOException {
        if (index < 1 || index > 9999) {
            throw new IllegalArgumentException("index must be between 1 and 9999");
        }

        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.RGB_565);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(225, 235, 245));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(25, 55, 90));
            paint.setTextSize(34f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("SYNTHETIC TEST IMAGE", 320f, 190f, paint);
            paint.setTextSize(27f);
            canvas.drawText(String.format(Locale.US, "TEST ID: %04d", index), 320f, 245f, paint);
            paint.setTextSize(20f);
            canvas.drawText("NO PERSONAL DATA", 320f, 295f, paint);

            File directory = new File(context.getCacheDir(), "synthetic-tests");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create synthetic test directory");
            }
            File file = new File(directory, String.format(Locale.US, "synthetic_%03d.jpg", index));
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw new IOException("JPEG generation failed");
                }
            }
            return file;
        } finally {
            bitmap.recycle();
        }
    }
}
