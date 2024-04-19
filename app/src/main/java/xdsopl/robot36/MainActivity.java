/*
Robot36

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
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
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

	private final int scopeWidth = 320, scopeHeight = 640;
	private Bitmap scopeBitmap;
	private int[] scopePixels;
	private ImageView scopeView;
	private float[] recordBuffer;
	private float[] scanLineBuffer;
	private AudioRecord audioRecord;
	private TextView status;
	private Demodulator demodulator;
	private int[] last5msSyncPulses;
	private int[] last9msSyncPulses;
	private int[] last20msSyncPulses;
	private int[] last5msScanLines;
	private int[] last9msScanLines;
	private int[] last20msScanLines;

	private int tint;
	private int curLine;
	private int curSample;
	private int scanLineToleranceSamples;

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
			if (visualizeSignal(demodulator.process(recordBuffer))) {
				scopeBitmap.setPixels(scopePixels, scopeWidth * curLine, scopeWidth, 0, 0, scopeWidth, scopeHeight);
				scopeView.invalidate();
			}
		}
	};

	private void adjustSyncPulses(int[] pulses, int shift) {
		for (int i = 0; i < pulses.length; ++i)
			pulses[i] -= shift;
	}

	private double scanLineMean(int[] lines) {
		double mean = 0;
		for (int diff : lines)
			mean += diff;
		mean /= lines.length;
		return mean;
	}

	private int scanLineStdDev(int[] lines) {
		double mean = scanLineMean(lines);
		double stdDev = 0;
		for (int diff : lines)
			stdDev += (diff - mean) * (diff - mean);
		stdDev = Math.sqrt(stdDev / lines.length);
		return (int) Math.round(stdDev);
	}

	private void processOneLine(int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex < 0 || prevPulseIndex + scanLineSamples >= scanLineBuffer.length)
			return;
		for (int i = 0; i < scopeWidth; ++i) {
			int position = (i * scanLineSamples) / scopeWidth + prevPulseIndex;
			int intensity = (int) Math.round(255 * Math.sqrt(scanLineBuffer[position]));
			int pixelColor = 0xff000000 | 0x00010101 * intensity;
			scopePixels[scopeWidth * curLine + i] = pixelColor;
		}
	}

	private boolean processSyncPulse(int[] pulses, int[] lines, int index) {
		for (int i = 1; i < lines.length; ++i)
			lines[i - 1] = lines[i];
		lines[lines.length - 1] = index - pulses[pulses.length - 1];
		for (int i = 1; i < pulses.length; ++i)
			pulses[i - 1] = pulses[i];
		pulses[pulses.length - 1] = index;
		if (lines[0] == 0)
			return false;
		if (scanLineStdDev(lines) > scanLineToleranceSamples)
			return false;
		if (pulses[0] >= lines[0]) {
			int lineSamples = lines[0];
			int endPulse = pulses[0];
			int extrapolate = endPulse / lineSamples;
			int firstPulse = endPulse - extrapolate * lineSamples;
			for (int pulseIndex = firstPulse; pulseIndex < endPulse; pulseIndex += lineSamples)
				processOneLine(pulseIndex, lineSamples);
		}
		for (int i = 0; i < lines.length; ++i)
			processOneLine(pulses[i], lines[i]);
		int shift = pulses[pulses.length - 1];
		adjustSyncPulses(last5msSyncPulses, shift);
		adjustSyncPulses(last9msSyncPulses, shift);
		adjustSyncPulses(last20msSyncPulses, shift);
		int endSample = curSample;
		curSample = 0;
		for (int i = shift; i < endSample; ++i)
			scanLineBuffer[curSample++] = scanLineBuffer[i];
		for (int i = 0; i < scopeWidth; ++i)
			scopePixels[scopeWidth * (curLine + scopeHeight) + i] = scopePixels[scopeWidth * curLine + i];
		curLine = (curLine + 1) % scopeHeight;
		return true;
	}

	private boolean visualizeSignal(boolean syncPulseDetected) {
		int syncPulseIndex = curSample + demodulator.syncPulseOffset;
		for (float v : recordBuffer) {
			scanLineBuffer[curSample++] = v;
			if (curSample >= scanLineBuffer.length) {
				int shift = scanLineBuffer.length / 2;
				syncPulseIndex -= shift;
				adjustSyncPulses(last5msSyncPulses, shift);
				adjustSyncPulses(last9msSyncPulses, shift);
				adjustSyncPulses(last20msSyncPulses, shift);
				curSample = 0;
				for (int i = shift; i < scanLineBuffer.length; ++i)
					scanLineBuffer[curSample++] = scanLineBuffer[i];
			}
		}
		if (syncPulseDetected) {
			switch (demodulator.syncPulseWidth) {
				case FiveMilliSeconds:
					return processSyncPulse(last5msSyncPulses, last5msScanLines, syncPulseIndex);
				case NineMilliSeconds:
					return processSyncPulse(last9msSyncPulses, last9msScanLines, syncPulseIndex);
				case TwentyMilliSeconds:
					return processSyncPulse(last20msSyncPulses, last20msScanLines, syncPulseIndex);
			}
		}
		return false;
	}

	void initTools(int sampleRate) {
		demodulator = new Demodulator(sampleRate);
		double scanLineMaxSeconds = 5;
		int scanLineMaxSamples = (int) Math.round(scanLineMaxSeconds * sampleRate);
		scanLineBuffer = new float[scanLineMaxSamples];
		int scanLineCount = 3;
		last5msScanLines = new int[scanLineCount];
		last9msScanLines = new int[scanLineCount];
		last20msScanLines = new int[scanLineCount];
		int syncPulseCount = scanLineCount + 1;
		last5msSyncPulses = new int[syncPulseCount];
		last9msSyncPulses = new int[syncPulseCount];
		last20msSyncPulses = new int[syncPulseCount];
		double scanLineToleranceSeconds = 0.001;
		scanLineToleranceSamples = (int) Math.round(scanLineToleranceSeconds * sampleRate);
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
