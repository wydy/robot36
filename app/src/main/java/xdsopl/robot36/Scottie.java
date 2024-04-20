/*
Scottie modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Scottie implements Mode {
	private final int scanLineSamples;
	private final int channelSamples;
	private final int greenBeginSamples;
	private final int blueBeginSamples;
	private final int redBeginSamples;
	private final int redEndSamples;
	private final String name;

	Scottie(String name, double channelSeconds, int sampleRate) {
		this.name = "Scottie " + name;
		double syncPulseSeconds = 0.009;
		double separatorSeconds = 0.0015;
		double scanLineSeconds = syncPulseSeconds + 3 * (channelSeconds + separatorSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		channelSamples = (int) Math.round(channelSeconds * sampleRate);
		double blueBeginSeconds = -(syncPulseSeconds / 2 + channelSeconds);
		blueBeginSamples = (int) Math.round(blueBeginSeconds * sampleRate);
		double greenBeginSeconds = blueBeginSeconds - (separatorSeconds + channelSeconds);
		greenBeginSamples = (int) Math.round(greenBeginSeconds * sampleRate);
		double redBeginSeconds = syncPulseSeconds / 2 + separatorSeconds;
		redBeginSamples = (int) Math.round(redBeginSeconds * sampleRate);
		double redEndSeconds = redBeginSeconds + channelSeconds;
		redEndSamples = (int) Math.round(redEndSeconds * sampleRate);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex + greenBeginSamples < 0 || prevPulseIndex + redEndSamples > scanLineBuffer.length)
			return 0;
		for (int i = 0; i < evenBuffer.length; ++i) {
			int position = (i * channelSamples) / evenBuffer.length + prevPulseIndex;
			int redPos = position + redBeginSamples;
			int greenPos = position + greenBeginSamples;
			int bluePos = position + blueBeginSamples;
			evenBuffer[i] = ColorConverter.RGB(scanLineBuffer[redPos], scanLineBuffer[greenPos], scanLineBuffer[bluePos]);
		}
		return 1;
	}
}
