/*
Martin modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Martin implements Mode {
	private final int scanLineSamples;
	private final int channelSamples;
	private final int greenBeginSamples;
	private final int blueBeginSamples;
	private final int redBeginSamples;
	private final int redEndSamples;
	private final String name;

	Martin(String name, double channelSeconds, int sampleRate) {
		this.name = "Martin " + name;
		double syncPulseSeconds = 0.004862;
		double separatorSeconds = 0.000572;
		double scanLineSeconds = syncPulseSeconds + separatorSeconds + 3 * (channelSeconds + separatorSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		channelSamples = (int) Math.round(channelSeconds * sampleRate);
		double greenBeginSeconds = syncPulseSeconds / 2 + separatorSeconds;
		greenBeginSamples = (int) Math.round(greenBeginSeconds * sampleRate);
		double greenEndSeconds = greenBeginSeconds + channelSeconds;
		double blueBeginSeconds = greenEndSeconds + separatorSeconds;
		blueBeginSamples = (int) Math.round(blueBeginSeconds * sampleRate);
		double blueEndSeconds = blueBeginSeconds + channelSeconds;
		double redBeginSeconds = blueEndSeconds + separatorSeconds;
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
