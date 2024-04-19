/*
Martin modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Martin implements Mode {
	private final int scanLineSamples;
	private final String name;

	Martin(String name, double channelSeconds, int sampleRate) {
		this.name = "Martin " + name;
		double syncPulseSeconds = 0.004862;
		double separatorSeconds = 0.000572;
		double scanLineSeconds = syncPulseSeconds + separatorSeconds + 3 * (channelSeconds + separatorSeconds);
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
