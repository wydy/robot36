/*
Robot 72 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_72_Color implements Mode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int scanLineSamples;
	private final int luminanceSamples;
	private final int chrominanceSamples;
	private final int beginSamples;
	private final int yBeginSamples;
	private final int vBeginSamples;
	private final int uBeginSamples;
	private final int endSamples;

	@SuppressWarnings("UnnecessaryLocalVariable")
	Robot_72_Color(int sampleRate) {
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.138;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.069;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + 2 * (separatorSeconds + porchSeconds + chrominanceSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		luminanceSamples = (int) Math.round(luminanceSeconds * sampleRate);
		chrominanceSamples = (int) Math.round(chrominanceSeconds * sampleRate);
		double yBeginSeconds = syncPorchSeconds;
		yBeginSamples = (int) Math.round(yBeginSeconds * sampleRate);
		beginSamples = yBeginSamples;
		double yEndSeconds = yBeginSeconds + luminanceSeconds;
		double vBeginSeconds = yEndSeconds + separatorSeconds + porchSeconds;
		vBeginSamples = (int) Math.round(vBeginSeconds * sampleRate);
		double vEndSeconds = vBeginSeconds + chrominanceSeconds;
		double uBeginSeconds = vEndSeconds + separatorSeconds + porchSeconds;
		uBeginSamples = (int) Math.round(uBeginSeconds * sampleRate);
		double uEndSeconds = uBeginSeconds + chrominanceSeconds;
		endSamples = (int) Math.round(uEndSeconds * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	private float freqToLevel(float frequency, float offset) {
		return 0.5f * (frequency - offset + 1.f);
	}

	@Override
	public String getName() {
		return "Robot 72 Color";
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scratchBuffer, float[] scanLineBuffer, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex + beginSamples < 0 || syncPulseIndex + endSamples > scanLineBuffer.length)
			return 0;
		lowPassFilter.cutoff(evenBuffer.length, 2 * luminanceSamples, 2);
		lowPassFilter.reset();
		for (int i = beginSamples; i < endSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + i]);
		lowPassFilter.reset();
		for (int i = endSamples - 1; i >= beginSamples; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < evenBuffer.length; ++i) {
			int yPos = yBeginSamples + (i * luminanceSamples) / evenBuffer.length;
			int uPos = uBeginSamples + (i * chrominanceSamples) / evenBuffer.length;
			int vPos = vBeginSamples + (i * chrominanceSamples) / evenBuffer.length;
			evenBuffer[i] = ColorConverter.YUV2RGB(scratchBuffer[yPos], scratchBuffer[uPos], scratchBuffer[vPos]);
		}
		return 1;
	}
}
