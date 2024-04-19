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

	@Override
	public boolean decodeScanLine(int[] pixelBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex < 0 || prevPulseIndex + scanLineSamples >= scanLineBuffer.length)
			return false;
		for (int i = 0; i < pixelBuffer.length; ++i) {
			int position = (i * scanLineSamples) / pixelBuffer.length + prevPulseIndex;
			int intensity = (int) Math.round(255 * Math.sqrt(scanLineBuffer[position]));
			int pixelColor = 0xff000000 | 0x00010101 * intensity;
			pixelBuffer[i] = pixelColor;
		}
		return true;
	}
}
