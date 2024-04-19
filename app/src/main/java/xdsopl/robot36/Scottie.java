/*
Scottie modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Scottie implements Mode {
	private final int scanLineSamples;
	private final String name;

	Scottie(String name, double channelSeconds, int sampleRate) {
		this.name = "Scottie " + name;
		double syncPulseSeconds = 0.009;
		double separatorSeconds = 0.0015;
		double scanLineSeconds = syncPulseSeconds + 3 * (channelSeconds + separatorSeconds);
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
