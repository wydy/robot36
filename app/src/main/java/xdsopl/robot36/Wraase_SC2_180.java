/*
Wraase SC2-180

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Wraase_SC2_180 implements Mode {
	private final int scanLineSamples;

	Wraase_SC2_180(int sampleRate) {
		double syncPulseSeconds = 0.0055225;
		double syncPorchSeconds = 0.0005;
		double channelSeconds = 0.235;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + 3 * channelSeconds;
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
	}

	@Override
	public String getName() {
		return "Wraase SC2-180";
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}
}
