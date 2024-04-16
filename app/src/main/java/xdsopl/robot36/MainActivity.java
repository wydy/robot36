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

	private final int scopeWidth = 1200, scopeHeight = 512;
	private Bitmap scopeBitmap;
	private int[] scopePixels;
	private ImageView scopeView;
	private float[] recordBuffer;
	private AudioRecord audioRecord;
	private TextView status;
	private Delay syncPulseDelay;
	private Delay scanLineDelay;
	private SimpleMovingAverage powerAvg;
	private ComplexMovingAverage syncPulseFilter;
	private ComplexMovingAverage scanLineFilter;
	private ComplexMovingAverage baseBandLowPass;
	private FrequencyModulation scanLineDemod;
	private Phasor syncPulseOscillator;
	private Phasor scanLineOscillator;
	private Phasor baseBandOscillator;
	private Complex baseBand;
	private Complex syncPulse;
	private Complex scanLine;
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
			processSamples();
		}
	};

	private void processSamples() {
		for (float v : recordBuffer) {
			baseBand = baseBandLowPass.avg(baseBand.set(v).mul(baseBandOscillator.rotate()));
			syncPulse = syncPulseFilter.avg(syncPulse.set(baseBand).mul(syncPulseOscillator.rotate()));
			scanLine = scanLineFilter.avg(scanLine.set(baseBand).mul(scanLineOscillator.rotate()));
			float syncPulseValue = syncPulseDelay.push(syncPulse.norm()) / powerAvg.avg(baseBand.norm());
			float scanLineValue = scanLineDelay.push(scanLineDemod.demod(scanLine));
			float syncPulseLevel = Math.min(Math.max(syncPulseValue, 0), 1);
			float scanLineLevel = Math.min(Math.max(0.5f * (scanLineValue + 1), 0), 1);
			int syncPulseIntensity = (int) Math.round(255 * Math.sqrt(syncPulseLevel));
			int scanLineIntensity = (int) Math.round(255 * Math.sqrt(scanLineLevel));
			int syncPulseColor = 0x00000100 * syncPulseIntensity;
			int scanLineColor = 0x00010101 * scanLineIntensity;
			int pixelColor =  0xff000000;
			if (syncPulseLevel > 0.1)
				pixelColor |= syncPulseColor;
			else
				pixelColor |= scanLineColor;
			scopePixels[scopeWidth * curLine + curColumn] = pixelColor;
			if (++curColumn >= scopeWidth) {
				curColumn = 0;
				for (int i = 0; i < scopeWidth; ++i)
					scopePixels[scopeWidth * (curLine + scopeHeight) + i] = scopePixels[scopeWidth * curLine + i];
				curLine = (curLine + 1) % scopeHeight;
				scopeBitmap.setPixels(scopePixels, scopeWidth * curLine, scopeWidth, 0, 0, scopeWidth, scopeHeight);
				scopeView.invalidate();
			}
		}
	}

	private void initTools(int sampleRate) {
		double powerWindowSeconds = 0.5;
		int powerWindowSamples = (int) Math.round(powerWindowSeconds * sampleRate) | 1;
		powerAvg = new SimpleMovingAverage(powerWindowSamples);
		float blackFrequency = 1500;
		float whiteFrequency = 2300;
		float scanLineBandwidth = whiteFrequency - blackFrequency;
		scanLineDemod = new FrequencyModulation(scanLineBandwidth, sampleRate);
		float scanLineCutoff = scanLineBandwidth / 2;
		int scanLineFilterSamples = (int) Math.round(0.443 * sampleRate / scanLineCutoff) | 1;
		scanLineFilter = new ComplexMovingAverage(scanLineFilterSamples);
		double syncPulseSeconds = 0.009;
		int syncPulseFilterSamples = (int) Math.round(syncPulseSeconds * sampleRate) | 1;
		syncPulseFilter = new ComplexMovingAverage(syncPulseFilterSamples);
		float lowestFrequency = 1100;
		float highestFrequency = 2300;
		float cutoffFrequency = (highestFrequency - lowestFrequency) / 2;
		int lowPassSamples = (int) Math.round(0.443 * sampleRate / cutoffFrequency) | 1;
		baseBandLowPass = new ComplexMovingAverage(lowPassSamples);
		float centerFrequency = (lowestFrequency + highestFrequency) / 2;
		baseBandOscillator = new Phasor(-centerFrequency, sampleRate);
		float syncPulseFrequency = 1200;
		syncPulseOscillator = new Phasor(-(syncPulseFrequency - centerFrequency), sampleRate);
		float grayFrequency = (blackFrequency + whiteFrequency) / 2;
		scanLineOscillator = new Phasor(-(grayFrequency - centerFrequency), sampleRate);
		int syncPulseDelaySamples = (powerWindowSamples - 1) / 2;
		syncPulseDelay = new Delay(syncPulseDelaySamples);
		int scanLineDelaySamples = (powerWindowSamples - 1) / 2 + (syncPulseFilterSamples - 1) / 2 - (scanLineFilterSamples - 1) / 2;
		scanLineDelay = new Delay(scanLineDelaySamples);
		baseBand = new Complex();
		syncPulse = new Complex();
		scanLine = new Complex();
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
