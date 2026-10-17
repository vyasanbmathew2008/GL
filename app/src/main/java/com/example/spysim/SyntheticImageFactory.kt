package com.example.spysim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object SyntheticImageFactory {
    fun create(context: Context, index: Int): File {
        require(index in 1..9999)
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(225, 235, 245))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 55, 90); textSize = 34f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SYNTHETIC TEST IMAGE", 320f, 190f, paint)
        paint.textSize = 27f
        canvas.drawText(String.format(Locale.US, "TEST ID: %04d", index), 320f, 245f, paint)
        paint.textSize = 20f
        canvas.drawText("NO PERSONAL DATA", 320f, 295f, paint)

        val directory = File(context.cacheDir, "synthetic-tests")
        check(directory.exists() || directory.mkdirs()) { "Cannot create synthetic test directory" }
        val file = File(directory, String.format(Locale.US, "synthetic_%03d.jpg", index))
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "JPEG generation failed" }
            }
        } finally { bitmap.recycle() }
        return file
    }
}
