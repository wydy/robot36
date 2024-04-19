/*
Raw mode

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Raw implements Mode {

	Raw() {
	}

	@Override
	public String getName() {
		return "Raw";
	}

	@Override
	public int getScanLineSamples() {
		return -1;
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
