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

	private final int scopeWidth = 1234, scopeHeight = 512;
	private Bitmap scopeBitmap;
	private int[] scopePixels;
	private ImageView scopeView;
	private float[] recordBuffer;
	private AudioRecord audioRecord;
	private TextView status;
	private Demodulator demodulator;

	private int tint;
	private int curLine;
	private int curColumn;

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
			visualizeSignal(demodulator.process(recordBuffer));
		}
	};

	private void slideUpOneLine() {
		for (int i = 0; i < scopeWidth; ++i)
			scopePixels[scopeWidth * (curLine + scopeHeight) + i] = scopePixels[scopeWidth * curLine + i];
		curLine = (curLine + 1) % scopeHeight;
		scopeBitmap.setPixels(scopePixels, scopeWidth * curLine, scopeWidth, 0, 0, scopeWidth, scopeHeight);
		scopeView.invalidate();
	}

	private void visualizeSignal(boolean syncPulseDetected) {
		int syncPulseOffset = curColumn + demodulator.syncPulseOffset;
		if (syncPulseDetected && syncPulseOffset >= 0 && syncPulseOffset < curColumn) {
			syncPulseDetected = false;
			int nextLine = (curLine + 1) % scopeHeight;
			for (int i = 0; i < curColumn - syncPulseOffset; ++i)
				scopePixels[scopeWidth * nextLine + i] = scopePixels[scopeWidth * curLine + syncPulseOffset + i];
			for (int i = syncPulseOffset; i < scopeWidth; ++i)
				scopePixels[scopeWidth * curLine + i] = 0xff00ff00;
			curColumn -= syncPulseOffset;
			slideUpOneLine();
		}
		for (float v : recordBuffer) {
			int intensity = (int) Math.round(255 * Math.sqrt(v));
			int pixelColor = 0xff000000 | 0x00010101 * intensity;
			scopePixels[scopeWidth * curLine + curColumn] = pixelColor;
			boolean syncNow = syncPulseDetected && syncPulseOffset == curColumn;
			if (syncNow || ++curColumn >= scopeWidth) {
				if (syncNow)
					while (curColumn < scopeWidth)
						scopePixels[scopeWidth * curLine + curColumn++] = 0xffff0000;
				syncPulseDetected = false;
				curColumn = 0;
				slideUpOneLine();
			}
		}
	}

	void initTools(int sampleRate) {
		demodulator = new Demodulator(sampleRate);
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
