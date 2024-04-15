/*
Robot36

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.Manifest;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

	private final int scopeWidth = 256, scopeHeight = 256;
	private Bitmap scopeBitmap;
	private int[] scopePixels;
	private ImageView scopeView;
	private float[] recordBuffer;
	private AudioRecord audioRecord;
	private TextView status;
	private Delay powerDelay;
	private SimpleMovingAverage powerAvg;
	private ComplexMovingAverage syncAvg;
	private Phasor osc_1200;
	private Complex sad;
	private int tint;
	private int curLine;

	private void setStatus(int id) {
		status.setText(id);
	}

	private final AudioRecord.OnRecordPositionUpdateListener recordListener = new AudioRecord.OnRecordPositionUpdateListener() {
		@Override
		public void onMarkerReached(AudioRecord ignore) {
		}

		@Override
		public void onPeriodicNotification(AudioRecord audioRecord) {
			audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
			processSamples();
		}
	};

	private void processSamples() {
		for (float v : recordBuffer) {
			sad = syncAvg.avg(sad.set(powerDelay.push(v)).mul(osc_1200.rotate()));
			float level = sad.norm() / powerAvg.avg(v * v);
			int x = Math.min((int) (scopeWidth * level), scopeWidth);
			for (int i = 0; i < x; ++i)
				scopePixels[scopeWidth * curLine + i] = tint;
			for (int i = x; i < scopeWidth; ++i)
				scopePixels[scopeWidth * curLine + i] = 0;
			for (int i = 0; i < scopeWidth; ++i)
				scopePixels[scopeWidth * (curLine + scopeHeight) + i] = scopePixels[scopeWidth * curLine + i];
			curLine = (curLine + 1) % scopeHeight;
		}
		scopeBitmap.setPixels(scopePixels, scopeWidth * curLine, scopeWidth, 0, 0, scopeWidth, scopeHeight);
		scopeView.invalidate();
	}

	private void initTools(int sampleRate) {
		double powerWindowSeconds = 0.5;
		int powerWindowSamples = (int) Math.round(powerWindowSeconds * sampleRate) | 1;
		powerAvg = new SimpleMovingAverage(powerWindowSamples);
		powerDelay = new Delay((powerWindowSamples - 1) / 2);
		double syncPulseSeconds = 0.009;
		int syncPulseSamples = (int) Math.round(syncPulseSeconds * sampleRate);
		syncAvg = new ComplexMovingAverage(syncPulseSamples);
		osc_1200 = new Phasor(-1200, sampleRate);
		sad = new Complex();
	}

	private void initAudioRecord() {
		int audioSource = MediaRecorder.AudioSource.UNPROCESSED;
		int channelConfig = AudioFormat.CHANNEL_IN_MONO;
		int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
		int sampleRate = 8000;
		int sampleSize = 4;
		int channelCount = 1;
		int readsPerSecond = 50;
		double bufferSeconds = 0.5;
		int bufferSize = (int) (bufferSeconds * sampleRate * sampleSize);
		recordBuffer = new float[(sampleRate * channelCount) / readsPerSecond];
		try {
			audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize);
			if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
				audioRecord.setRecordPositionUpdateListener(recordListener);
				audioRecord.setPositionNotificationPeriod(recordBuffer.length);
				initTools(sampleRate);
				startListening();
			} else {
				setStatus(R.string.audio_init_failed);
			}
		} catch (IllegalArgumentException e) {
			setStatus(R.string.audio_setup_failed);
		} catch (SecurityException e) {
			setStatus(R.string.audio_permission_denied);
		}
	}

	private void startListening() {
		if (audioRecord != null) {
			audioRecord.startRecording();
			if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
				audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
				setStatus(R.string.listening);
			} else {
				setStatus(R.string.audio_recording_error);
			}
		}
	}

	private void stopListening() {
		if (audioRecord != null)
			audioRecord.stop();
	}

	private final int permissionID = 1;

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != permissionID)
			return;
		for (int i = 0; i < permissions.length; ++i)
			if (permissions[i].equals(Manifest.permission.RECORD_AUDIO) && grantResults[i] == PackageManager.PERMISSION_GRANTED)
				initAudioRecord();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});
		tint = getColor(R.color.tint);
		status = findViewById(R.id.status);
		scopeView = findViewById(R.id.scope);
		scopeBitmap = Bitmap.createBitmap(scopeWidth, scopeHeight, Bitmap.Config.ARGB_8888);
		scopeView.setImageBitmap(scopeBitmap);
		scopePixels = new int[2 * scopeWidth * scopeHeight];
		List<String> permissions = new ArrayList<>();
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO);
			setStatus(R.string.audio_permission_denied);
		} else {
			initAudioRecord();
		}
		if (!permissions.isEmpty())
			ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), permissionID);
	}

	@Override
	protected void onResume() {
		startListening();
		super.onResume();
	}

	@Override
	protected void onPause() {
		stopListening();
		super.onPause();
	}
}
