/*
Wraase SC2-180

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Wraase_SC2_180 implements Mode {
	private final int scanLineSamples;
	private final int channelSamples;
	private final int redBeginSamples;
	private final int greenBeginSamples;
	private final int blueBeginSamples;
	private final int blueEndSamples;

	Wraase_SC2_180(int sampleRate) {
		double syncPulseSeconds = 0.0055225;
		double syncPorchSeconds = 0.0005;
		double channelSeconds = 0.235;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + 3 * channelSeconds;
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		channelSamples = (int) Math.round(channelSeconds * sampleRate);
		double redBeginSeconds = syncPulseSeconds / 2 + syncPorchSeconds;
		redBeginSamples = (int) Math.round(redBeginSeconds * sampleRate);
		double greenBeginSeconds = redBeginSeconds + channelSeconds;
		greenBeginSamples = (int) Math.round(greenBeginSeconds * sampleRate);
		double blueBeginSeconds = greenBeginSeconds + channelSeconds;
		blueBeginSamples = (int) Math.round(blueBeginSeconds * sampleRate);
		double blueEndSeconds = blueBeginSeconds + channelSeconds;
		blueEndSamples = (int) Math.round(blueEndSeconds * sampleRate);
	}

	@Override
	public String getName() {
		return "Wraase SC2-180";
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex + redBeginSamples < 0 || prevPulseIndex + blueEndSamples > scanLineBuffer.length)
			return 0;
		for (int i = 0; i < evenBuffer.length; ++i) {
			int position = (i * channelSamples) / evenBuffer.length + prevPulseIndex;
			int redPos = position + redBeginSamples;
			int greenPos = position + greenBeginSamples;
			int bluePos = position + blueBeginSamples;
			int red = Math.round(255 * scanLineBuffer[redPos]);
			int green = Math.round(255 * scanLineBuffer[greenPos]);
			int blue = Math.round(255 * scanLineBuffer[bluePos]);
			evenBuffer[i] = 0xff000000 | (red << 16) | (green << 8) | blue;
		}
		return 1;
	}
}
