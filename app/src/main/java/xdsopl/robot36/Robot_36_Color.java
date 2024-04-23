/*
Robot 36 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_36_Color implements Mode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int scanLineSamples;
	private final int luminanceSamples;
	private final int separatorSamples;
	private final int chrominanceSamples;
	private final int beginSamples;
	private final int luminanceBeginSamples;
	private final int separatorBeginSamples;
	private final int chrominanceBeginSamples;
	private final int endSamples;

	@SuppressWarnings("UnnecessaryLocalVariable")
	Robot_36_Color(int sampleRate) {
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.088;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.044;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + separatorSeconds + porchSeconds + chrominanceSeconds;
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		luminanceSamples = (int) Math.round(luminanceSeconds * sampleRate);
		separatorSamples = (int) Math.round(separatorSeconds * sampleRate);
		chrominanceSamples = (int) Math.round(chrominanceSeconds * sampleRate);
		double luminanceBeginSeconds = syncPorchSeconds;
		luminanceBeginSamples = (int) Math.round(luminanceBeginSeconds * sampleRate);
		beginSamples = luminanceBeginSamples;
		double separatorBeginSeconds = luminanceBeginSeconds + luminanceSeconds;
		separatorBeginSamples = (int) Math.round(separatorBeginSeconds * sampleRate);
		double separatorEndSeconds = separatorBeginSeconds + separatorSeconds;
		double chrominanceBeginSeconds = separatorEndSeconds + porchSeconds;
		chrominanceBeginSamples = (int) Math.round(chrominanceBeginSeconds * sampleRate);
		double chrominanceEndSeconds = chrominanceBeginSeconds + chrominanceSeconds;
		endSamples = (int) Math.round(chrominanceEndSeconds * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	@Override
	public String getName() {
		return "Robot 36 Color";
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex + beginSamples < 0 || prevPulseIndex + endSamples > scanLineBuffer.length)
			return 0;
		float separator = 0;
		for (int i = 0; i < separatorSamples; ++i)
			separator += scanLineBuffer[prevPulseIndex + separatorBeginSamples + i];
		separator /= separatorSamples;
		boolean even = separator < 0.5f;
		lowPassFilter.reset(evenBuffer.length / (float) luminanceSamples);
		for (int i = prevPulseIndex + beginSamples; i < prevPulseIndex + endSamples; ++i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		lowPassFilter.reset(evenBuffer.length / (float) luminanceSamples);
		for (int i = prevPulseIndex + endSamples - 1; i >= prevPulseIndex + beginSamples; --i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		for (int i = 0; i < evenBuffer.length; ++i) {
			int luminancePos = luminanceBeginSamples + (i * luminanceSamples) / evenBuffer.length + prevPulseIndex;
			int chrominancePos = chrominanceBeginSamples + (i * chrominanceSamples) / evenBuffer.length + prevPulseIndex;
			if (even) {
				evenBuffer[i] = ColorConverter.RGB(scanLineBuffer[luminancePos], 0, scanLineBuffer[chrominancePos]);
			} else {
				int evenYUV = evenBuffer[i];
				int oddYUV =  ColorConverter.RGB(scanLineBuffer[luminancePos], scanLineBuffer[chrominancePos], 0);
				evenBuffer[i] = ColorConverter.YUV2RGB((evenYUV & 0x00ff00ff) | (oddYUV & 0x0000ff00));
				oddBuffer[i] = ColorConverter.YUV2RGB((oddYUV & 0x00ffff00) | (evenYUV & 0x000000ff));
			}
		}
		return even ? 0 : 2;
	}
}
