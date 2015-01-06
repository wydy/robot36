
/*
Copyright 2014 Ahmet Inan <xdsopl@googlemail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package xdsopl.robot36;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private Decoder decoder;
    private Bitmap bitmap;
    private NotificationManager manager;
    private int notifyID = 1;

    private void showNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.decoder_running))
            .setContentIntent(pending)
            .setOngoing(true);
        manager.notify(notifyID, builder.build());
    }

    void updateTitle(final String newTitle)
    {
        if (getTitle() != newTitle) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setTitle(newTitle);
                }
            });
        }
    }

    void storeBitmap(Bitmap image) {
        bitmap = image;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String name = new SimpleDateFormat("yyyyMMdd_HHmmss_").format(new Date());
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (!dir.exists())
                    dir.mkdirs();
                File file;
                FileOutputStream stream;
                try {
                    file = File.createTempFile(name, ".png", dir);
                    stream = new FileOutputStream(file);
                } catch (IOException ignore) {
                    return;
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                try {
                    stream.close();
                } catch (IOException ignore) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                sendBroadcast(intent);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        changeLayoutOrientation(getResources().getConfiguration());
        decoder = new Decoder((ImageView)findViewById(R.id.image), this);
        manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        showNotification();
    }

    @Override
    protected void onDestroy () {
        decoder.destroy();
        manager.cancel(notifyID);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        decoder.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        decoder.resume();
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        changeLayoutOrientation(config);
    }

    private void changeLayoutOrientation(Configuration config) {
        boolean horizontal = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        findViewById(R.id.spectrum).setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT, horizontal ? 1.0f : 10.0f));
        int orientation = horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        ((LinearLayout)findViewById(R.id.content)).setOrientation(orientation);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_softer_image:
                decoder.softer_image();
                return true;
            case R.id.action_sharper_image:
                decoder.sharper_image();
                return true;
            case R.id.action_toggle_scaling:
                decoder.toggle_scaling();
                return true;
            case R.id.action_toggle_debug:
                decoder.toggle_debug();
                return true;
            case R.id.action_toggle_auto:
                decoder.toggle_auto();
                return true;
            case R.id.action_raw_mode:
                decoder.raw_mode();
                return true;
            case R.id.action_robot36_mode:
                decoder.robot36_mode();
                return true;
            case R.id.action_robot72_mode:
                decoder.robot72_mode();
                return true;
            case R.id.action_martin1_mode:
                decoder.martin1_mode();
                return true;
            case R.id.action_martin2_mode:
                decoder.martin2_mode();
                return true;
            case R.id.action_scottie1_mode:
                decoder.scottie1_mode();
                return true;
            case R.id.action_scottie2_mode:
                decoder.scottie2_mode();
                return true;
            case R.id.action_scottieDX_mode:
                decoder.scottieDX_mode();
                return true;
            case R.id.action_wrasseSC2_180_mode:
                decoder.wrasseSC2_180_mode();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
