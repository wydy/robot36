/*
Robot 72 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_72_Color implements Mode {
	private final int scanLineSamples;

	Robot_72_Color(int sampleRate) {
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.138;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.069;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + 2 * (separatorSeconds + porchSeconds + chrominanceSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
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
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex < 0 || prevPulseIndex + scanLineSamples >= scanLineBuffer.length)
			return 0;
		for (int i = 0; i < evenBuffer.length; ++i) {
			int position = (i * scanLineSamples) / evenBuffer.length + prevPulseIndex;
			evenBuffer[i] = ColorConverter.GRAY(scanLineBuffer[position]);
		}
		return 1;
	}
}
