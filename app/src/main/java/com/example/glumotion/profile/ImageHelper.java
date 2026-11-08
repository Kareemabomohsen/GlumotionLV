package com.example.glumotion.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * Helper for the profile photo: Bitmap ↔ Base64 text.
 * Realtime Database saves only text, so the photo is saved as a (small) Base64 String.
 */
public class ImageHelper {

    // Biggest side of the saved photo (small = fast to save and load)
    private static final int MAX_SIZE = 400;

    // Makes the photo smaller (keeps the shape)
    public static Bitmap resize(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= MAX_SIZE && height <= MAX_SIZE) {
            return bitmap;
        }
        float scale;
        if (width > height) {
            scale = (float) MAX_SIZE / width;
        } else {
            scale = (float) MAX_SIZE / height;
        }
        return Bitmap.createScaledBitmap(bitmap, (int) (width * scale), (int) (height * scale), true);
    }

    // Bitmap → Base64 text (JPEG 70% quality)
    public static String bitmapToBase64(Bitmap bitmap) {
        Bitmap small = resize(bitmap);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 70, stream);
        byte[] bytes = stream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    // Base64 text → Bitmap (null if empty / broken)
    public static Bitmap base64ToBitmap(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(text, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
