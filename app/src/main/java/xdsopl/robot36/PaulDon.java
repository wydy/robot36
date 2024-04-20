/*
PD modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class PaulDon implements Mode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int scanLineSamples;
	private final int channelSamples;
	private final int beginSamples;
	private final int yEvenBeginSamples;
	private final int vAvgBeginSamples;
	private final int uAvgBeginSamples;
	private final int yOddBeginSamples;
	private final int endSamples;
	private final String name;

	PaulDon(String name, double channelSeconds, int sampleRate) {
		this.name = "PD " + name;
		double syncPulseSeconds = 0.02;
		double syncPorchSeconds = 0.00208;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + 4 * (channelSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		channelSamples = (int) Math.round(channelSeconds * sampleRate);
		double yEvenBeginSeconds = syncPulseSeconds / 2 + syncPorchSeconds;
		yEvenBeginSamples = (int) Math.round(yEvenBeginSeconds * sampleRate);
		beginSamples = yEvenBeginSamples;
		double vAvgBeginSeconds = yEvenBeginSeconds + channelSeconds;
		vAvgBeginSamples = (int) Math.round(vAvgBeginSeconds * sampleRate);
		double uAvgBeginSeconds = vAvgBeginSeconds + channelSeconds;
		uAvgBeginSamples = (int) Math.round(uAvgBeginSeconds * sampleRate);
		double yOddBeginSeconds = uAvgBeginSeconds + channelSeconds;
		yOddBeginSamples = (int) Math.round(yOddBeginSeconds * sampleRate);
		double yOddEndSeconds = yOddBeginSeconds + channelSeconds;
		endSamples = (int) Math.round(yOddEndSeconds * sampleRate);
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
		lowPassFilter.reset(evenBuffer.length / (float) channelSamples);
		for (int i = prevPulseIndex + beginSamples; i < prevPulseIndex + endSamples; ++i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		lowPassFilter.reset(evenBuffer.length / (float) channelSamples);
		for (int i = prevPulseIndex + endSamples - 1; i >= prevPulseIndex + beginSamples; --i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		for (int i = 0; i < evenBuffer.length; ++i) {
			int position = (i * channelSamples) / evenBuffer.length + prevPulseIndex;
			int yEvenPos = position + yEvenBeginSamples;
			int vAvgPos = position + vAvgBeginSamples;
			int uAvgPos = position + uAvgBeginSamples;
			int yOddPos = position + yOddBeginSamples;
			evenBuffer[i] = ColorConverter.YUV2RGB(scanLineBuffer[yEvenPos], scanLineBuffer[uAvgPos], scanLineBuffer[vAvgPos]);
			oddBuffer[i] = ColorConverter.YUV2RGB(scanLineBuffer[yOddPos], scanLineBuffer[uAvgPos], scanLineBuffer[vAvgPos]);
		}
		return 2;
	}
}
