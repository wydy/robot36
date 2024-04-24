/*
Decoder for RGB modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class RGBDecoder implements Mode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int scanLineSamples;
	private final int beginSamples;
	private final int redBeginSamples;
	private final int redSamples;
	private final int greenBeginSamples;
	private final int greenSamples;
	private final int blueBeginSamples;
	private final int blueSamples;
	private final int endSamples;
	private final String name;

	RGBDecoder(String name, double scanLineSeconds, double beginSeconds, double redBeginSeconds, double redEndSeconds, double greenBeginSeconds, double greenEndSeconds, double blueBeginSeconds, double blueEndSeconds, double endSeconds, int sampleRate) {
		this.name = name;
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		beginSamples = (int) Math.round(beginSeconds * sampleRate);
		redBeginSamples = (int) Math.round(redBeginSeconds * sampleRate);
		redSamples = (int) Math.round((redEndSeconds - redBeginSeconds) * sampleRate);
		greenBeginSamples = (int) Math.round(greenBeginSeconds * sampleRate);
		greenSamples = (int) Math.round((greenEndSeconds - greenBeginSeconds) * sampleRate);
		blueBeginSamples = (int) Math.round(blueBeginSeconds * sampleRate);
		blueSamples = (int) Math.round((blueEndSeconds - blueBeginSeconds) * sampleRate);
		endSamples = (int) Math.round(endSeconds * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	private float freqToLevel(float frequency, float offset) {
		return 0.5f * (frequency - offset + 1.f);
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
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scratchBuffer, float[] scanLineBuffer, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex + beginSamples < 0 || syncPulseIndex + endSamples > scanLineBuffer.length)
			return 0;
		lowPassFilter.cutoff(evenBuffer.length, 2 * greenSamples, 2);
		lowPassFilter.reset();
		for (int i = beginSamples; i < endSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + i]);
		lowPassFilter.reset();
		for (int i = endSamples - 1; i >= beginSamples; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < evenBuffer.length; ++i) {
			int redPos = redBeginSamples + (i * redSamples) / evenBuffer.length;
			int greenPos = greenBeginSamples + (i * greenSamples) / evenBuffer.length;
			int bluePos = blueBeginSamples + (i * blueSamples) / evenBuffer.length;
			evenBuffer[i] = ColorConverter.RGB(scratchBuffer[redPos], scratchBuffer[greenPos], scratchBuffer[bluePos]);
		}
		return 1;
	}
}
