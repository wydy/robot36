/*
PD modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class PaulDon implements Mode {
	private final int scanLineSamples;
	private final String name;

	PaulDon(String name, double channelSeconds, int sampleRate) {
		this.name = "PD " + name;
		double syncPulseSeconds = 0.02;
		double syncPorchSeconds = 0.00208;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + 4 * (channelSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}
}
