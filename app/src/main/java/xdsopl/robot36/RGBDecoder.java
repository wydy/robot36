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
		if (prevPulseIndex + beginSamples < 0 || prevPulseIndex + endSamples > scanLineBuffer.length)
			return 0;
		lowPassFilter.reset(evenBuffer.length / (float) greenSamples);
		for (int i = prevPulseIndex + beginSamples; i < prevPulseIndex + endSamples; ++i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		lowPassFilter.reset(evenBuffer.length / (float) greenSamples);
		for (int i = prevPulseIndex + endSamples - 1; i >= prevPulseIndex + beginSamples; --i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		for (int i = 0; i < evenBuffer.length; ++i) {
			int redPos = redBeginSamples + (i * redSamples) / evenBuffer.length + prevPulseIndex;
			int greenPos = greenBeginSamples + (i * greenSamples) / evenBuffer.length + prevPulseIndex;
			int bluePos = blueBeginSamples + (i * blueSamples) / evenBuffer.length + prevPulseIndex;
			evenBuffer[i] = ColorConverter.RGB(scanLineBuffer[redPos], scanLineBuffer[greenPos], scanLineBuffer[bluePos]);
		}
		return 1;
	}
}
