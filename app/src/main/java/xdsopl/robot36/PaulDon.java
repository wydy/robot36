/*
PD modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class PaulDon implements Mode {
	private final int scanLineSamples;
	private final int channelSamples;
	private final int yEvenBeginSamples;
	private final int vAvgBeginSamples;
	private final int uAvgBeginSamples;
	private final int yOddBeginSamples;
	private final int yOddEndSamples;
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
		double vAvgBeginSeconds = yEvenBeginSeconds + channelSeconds;
		vAvgBeginSamples = (int) Math.round(vAvgBeginSeconds * sampleRate);
		double uAvgBeginSeconds = vAvgBeginSeconds + channelSeconds;
		uAvgBeginSamples = (int) Math.round(uAvgBeginSeconds * sampleRate);
		double yOddBeginSeconds = uAvgBeginSeconds + channelSeconds;
		yOddBeginSamples = (int) Math.round(yOddBeginSeconds * sampleRate);
		double yOddEndSeconds = yOddBeginSeconds + channelSeconds;
		yOddEndSamples = (int) Math.round(yOddEndSeconds * sampleRate);
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
		if (prevPulseIndex + yEvenBeginSamples < 0 || prevPulseIndex + yOddEndSamples > scanLineBuffer.length)
			return 0;
		for (int i = 0; i < evenBuffer.length; ++i) {
			int position = (i * channelSamples) / evenBuffer.length + prevPulseIndex;
			int yEvenPos = position + yEvenBeginSamples;
			int vAvgPos = position + vAvgBeginSamples;
			int uAvgPos = position + uAvgBeginSamples;
			int yOddPos = position + yOddBeginSamples;
			int yEven = Math.round(255 * scanLineBuffer[yEvenPos]);
			int vAvg = Math.round(255 * scanLineBuffer[vAvgPos]);
			int uAvg = Math.round(255 * scanLineBuffer[uAvgPos]);
			int yOdd = Math.round(255 * scanLineBuffer[yOddPos]);
			evenBuffer[i] = ColorConverter.YUV2RGB(yEven, uAvg, vAvg);
			oddBuffer[i] = ColorConverter.YUV2RGB(yOdd, uAvg, vAvg);
		}
		return 2;
	}
}
