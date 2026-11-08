package com.example.glumotion.recipes;

import com.example.glumotion.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

/**
 * Loads a picture from the internet into an ImageView (the recipe photos of TheMealDB).
 * The download runs in a background Thread, the picture is shown with a Handler on the main thread,
 * and every picture is kept in a HashMap (cache) so it is downloaded only once.
 */
public class ImageLoader {

    private static final HashMap<String, Bitmap> cache = new HashMap<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Shows the picture of the url inside the ImageView
    public static void load(final String url, final ImageView target) {
        if (url == null || url.isEmpty()) {
            return;
        }
        // RecyclerView reuses rows → remember which url this ImageView waits for
        target.setTag(url);
        if (cache.containsKey(url)) {
            target.setImageBitmap(cache.get(url));
            return;
        }
        target.setImageResource(R.drawable.ic_food);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(20000);
                    InputStream stream = connection.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    stream.close();
                    connection.disconnect();
                    if (bitmap == null) {
                        return;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            cache.put(url, bitmap);
                            // still the same row? (the user may have scrolled)
                            if (url.equals(target.getTag())) {
                                target.setImageBitmap(bitmap);
                            }
                        }
                    });
                } catch (Exception e) {
                    // no internet - the food icon stays
                }
            }
        });
        thread.start();
    }
}
