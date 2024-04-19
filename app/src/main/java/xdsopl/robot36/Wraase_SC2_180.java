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
