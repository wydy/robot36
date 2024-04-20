/*
Raw decoder

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class RawDecoder implements Mode {
	private final ExponentialMovingAverage lowPassFilter;

	RawDecoder() {
		lowPassFilter = new ExponentialMovingAverage();
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
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex < 0 || prevPulseIndex + scanLineSamples > scanLineBuffer.length)
			return 0;
		lowPassFilter.reset(evenBuffer.length / (float) scanLineSamples);
		for (int i = prevPulseIndex; i < prevPulseIndex + scanLineSamples; ++i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		lowPassFilter.reset(evenBuffer.length / (float) scanLineSamples);
		for (int i = prevPulseIndex + scanLineSamples - 1; i >= prevPulseIndex; --i)
			scanLineBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		for (int i = 0; i < evenBuffer.length; ++i) {
			int position = (i * scanLineSamples) / evenBuffer.length + prevPulseIndex;
			evenBuffer[i] = ColorConverter.GRAY(scanLineBuffer[position]);
		}
		return 1;
	}
}
