/*
Robot36

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

	private Bitmap scopeBitmap;
	private PixelBuffer scopeBuffer;
	private ImageView scopeView;
	private float[] recordBuffer;
	private AudioRecord audioRecord;
	private Decoder decoder;
	private Menu menu;
	private int recordRate;
	private int recordChannel;
	private int audioSource;

	private void setStatus(int id) {
		setTitle(id);
	}

	private void setStatus(String str) {
		setTitle(str);
	}

	private final AudioRecord.OnRecordPositionUpdateListener recordListener = new AudioRecord.OnRecordPositionUpdateListener() {
		@Override
		public void onMarkerReached(AudioRecord ignore) {
		}

		@Override
		public void onPeriodicNotification(AudioRecord audioRecord) {
			audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
			if (decoder.process(recordBuffer, recordChannel)) {
				scopeBitmap.setPixels(scopeBuffer.pixels, scopeBuffer.width * scopeBuffer.line, scopeBuffer.width, 0, 0, scopeBuffer.width, scopeBuffer.height / 2);
				scopeView.invalidate();
				setStatus(decoder.lastMode.getName());
			}
		}
	};

	private void initAudioRecord() {
		boolean rateChanged = true;
		if (audioRecord != null) {
			rateChanged = audioRecord.getSampleRate() != recordRate;
			boolean channelChanged = audioRecord.getChannelCount() != (recordChannel == 0 ? 1 : 2);
			boolean sourceChanged = audioRecord.getAudioSource() != audioSource;
			if (!rateChanged && !channelChanged && !sourceChanged)
				return;
			stopListening();
			audioRecord.release();
			audioRecord = null;
		}
		int channelConfig = AudioFormat.CHANNEL_IN_MONO;
		int channelCount = 1;
		if (recordChannel != 0) {
			channelCount = 2;
			channelConfig = AudioFormat.CHANNEL_IN_STEREO;
		}
		int sampleSize = 4;
		int frameSize = sampleSize * channelCount;
		int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
		int readsPerSecond = 50;
		int bufferSize = Integer.highestOneBit(recordRate) * frameSize;
		int frameCount = recordRate / readsPerSecond;
		recordBuffer = new float[frameCount * channelCount];
		try {
			audioRecord = new AudioRecord(audioSource, recordRate, channelConfig, audioFormat, bufferSize);
			if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
				audioRecord.setRecordPositionUpdateListener(recordListener);
				audioRecord.setPositionNotificationPeriod(frameCount);
				if (rateChanged)
					decoder = new Decoder(scopeBuffer, recordRate);
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

	private void setRecordRate(int newSampleRate) {
		if (recordRate == newSampleRate)
			return;
		recordRate = newSampleRate;
		updateRecordRateMenu();
		initAudioRecord();
	}

	private void setRecordChannel(int newChannelSelect) {
		if (recordChannel == newChannelSelect)
			return;
		recordChannel = newChannelSelect;
		updateRecordChannelMenu();
		initAudioRecord();
	}

	private void setAudioSource(int newAudioSource) {
		if (audioSource == newAudioSource)
			return;
		audioSource = newAudioSource;
		updateAudioSourceMenu();
		initAudioRecord();
	}

	private void updateRecordRateMenu() {
		switch (recordRate) {
			case 8000:
				menu.findItem(R.id.action_set_record_rate_8000).setChecked(true);
				break;
			case 16000:
				menu.findItem(R.id.action_set_record_rate_16000).setChecked(true);
				break;
			case 32000:
				menu.findItem(R.id.action_set_record_rate_32000).setChecked(true);
				break;
			case 44100:
				menu.findItem(R.id.action_set_record_rate_44100).setChecked(true);
				break;
			case 48000:
				menu.findItem(R.id.action_set_record_rate_48000).setChecked(true);
				break;
		}
	}

	private void updateRecordChannelMenu() {
		switch (recordChannel) {
			case 0:
				menu.findItem(R.id.action_set_record_channel_default).setChecked(true);
				break;
			case 1:
				menu.findItem(R.id.action_set_record_channel_first).setChecked(true);
				break;
			case 2:
				menu.findItem(R.id.action_set_record_channel_second).setChecked(true);
				break;
			case 3:
				menu.findItem(R.id.action_set_record_channel_summation).setChecked(true);
				break;
			case 4:
				menu.findItem(R.id.action_set_record_channel_analytic).setChecked(true);
				break;
		}
	}

	private void updateAudioSourceMenu() {
		switch (audioSource) {
			case MediaRecorder.AudioSource.DEFAULT:
				menu.findItem(R.id.action_set_source_default).setChecked(true);
				break;
			case MediaRecorder.AudioSource.MIC:
				menu.findItem(R.id.action_set_source_microphone).setChecked(true);
				break;
			case MediaRecorder.AudioSource.CAMCORDER:
				menu.findItem(R.id.action_set_source_camcorder).setChecked(true);
				break;
			case MediaRecorder.AudioSource.VOICE_RECOGNITION:
				menu.findItem(R.id.action_set_source_voice_recognition).setChecked(true);
				break;
			case MediaRecorder.AudioSource.UNPROCESSED:
				menu.findItem(R.id.action_set_source_unprocessed).setChecked(true);
				break;
		}
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
	protected void onSaveInstanceState(@NonNull Bundle state) {
		state.putInt("nightMode", AppCompatDelegate.getDefaultNightMode());
		state.putInt("recordRate", recordRate);
		state.putInt("recordChannel", recordChannel);
		state.putInt("audioSource", audioSource);
		super.onSaveInstanceState(state);
	}

	private void storeSettings() {
		SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = pref.edit();
		edit.putInt("nightMode", AppCompatDelegate.getDefaultNightMode());
		edit.putInt("recordRate", recordRate);
		edit.putInt("recordChannel", recordChannel);
		edit.putInt("audioSource", audioSource);
		edit.apply();
	}

	@Override
	protected void onCreate(Bundle state) {
		final int defaultSampleRate = 8000;
		final int defaultChannelSelect = 0;
		final int defaultAudioSource = MediaRecorder.AudioSource.DEFAULT;
		if (state == null) {
			SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
			AppCompatDelegate.setDefaultNightMode(pref.getInt("nightMode", AppCompatDelegate.getDefaultNightMode()));
			recordRate = pref.getInt("recordRate", defaultSampleRate);
			recordChannel = pref.getInt("recordChannel", defaultChannelSelect);
			audioSource = pref.getInt("audioSource", defaultAudioSource);
		} else {
			AppCompatDelegate.setDefaultNightMode(state.getInt("nightMode", AppCompatDelegate.getDefaultNightMode()));
			recordRate = state.getInt("recordRate", defaultSampleRate);
			recordChannel = state.getInt("recordChannel", defaultChannelSelect);
			audioSource = state.getInt("audioSource", defaultAudioSource);
		}
		super.onCreate(state);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});
		scopeView = findViewById(R.id.scope);
		int scopeWidth = 640;
		int scopeHeight = 1280;
		scopeBitmap = Bitmap.createBitmap(scopeWidth, scopeHeight, Bitmap.Config.ARGB_8888);
		scopeView.setImageBitmap(scopeBitmap);
		scopeBuffer = new PixelBuffer(scopeWidth, 2 * scopeHeight);
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
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		this.menu = menu;
		updateRecordRateMenu();
		updateRecordChannelMenu();
		updateAudioSourceMenu();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_set_record_rate_8000) {
			setRecordRate(8000);
			return true;
		}
		if (id == R.id.action_set_record_rate_16000) {
			setRecordRate(16000);
			return true;
		}
		if (id == R.id.action_set_record_rate_32000) {
			setRecordRate(32000);
			return true;
		}
		if (id == R.id.action_set_record_rate_44100) {
			setRecordRate(44100);
			return true;
		}
		if (id == R.id.action_set_record_rate_48000) {
			setRecordRate(48000);
			return true;
		}
		if (id == R.id.action_set_record_channel_default) {
			setRecordChannel(0);
			return true;
		}
		if (id == R.id.action_set_record_channel_first) {
			setRecordChannel(1);
			return true;
		}
		if (id == R.id.action_set_record_channel_second) {
			setRecordChannel(2);
			return true;
		}
		if (id == R.id.action_set_record_channel_summation) {
			setRecordChannel(3);
			return true;
		}
		if (id == R.id.action_set_record_channel_analytic) {
			setRecordChannel(4);
			return true;
		}
		if (id == R.id.action_set_source_default) {
			setAudioSource(MediaRecorder.AudioSource.DEFAULT);
			return true;
		}
		if (id == R.id.action_set_source_microphone) {
			setAudioSource(MediaRecorder.AudioSource.MIC);
			return true;
		}
		if (id == R.id.action_set_source_camcorder) {
			setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
			return true;
		}
		if (id == R.id.action_set_source_voice_recognition) {
			setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
			return true;
		}
		if (id == R.id.action_set_source_unprocessed) {
			setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);
			return true;
		}
		if (id == R.id.action_enable_night_mode) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
			return true;
		}
		if (id == R.id.action_disable_night_mode) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		startListening();
		super.onResume();
	}

	@Override
	protected void onPause() {
		stopListening();
		storeSettings();
		super.onPause();
	}
}
