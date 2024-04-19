/*
Robot 36 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_36_Color implements Mode {
	private final int scanLineSamples;

	Robot_36_Color(int sampleRate) {
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.088;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.044;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + separatorSeconds + porchSeconds + chrominanceSeconds;
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
	}

	@Override
	public String getName() {
		return "Robot 36 Color";
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}
}
