/*
Raw decoder

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class RawDecoder implements Mode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int smallPictureMaxSamples;
	private final int mediumPictureMaxSamples;

	RawDecoder(int sampleRate) {
		smallPictureMaxSamples = (int) Math.round(0.125 * sampleRate);
		mediumPictureMaxSamples = (int) Math.round(0.175 * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	private float freqToLevel(float frequency, float offset) {
		return 0.5f * (frequency - offset + 1.f);
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
	public boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex < 0 || syncPulseIndex + scanLineSamples > scanLineBuffer.length)
			return false;
		int horizontalPixels = pixelBuffer.width;
		if (scanLineSamples < smallPictureMaxSamples)
			horizontalPixels /= 2;
		if (scanLineSamples < mediumPictureMaxSamples)
			horizontalPixels /= 2;
		lowPassFilter.cutoff(horizontalPixels, 2 * scanLineSamples, 2);
		lowPassFilter.reset();
		for (int i = 0; i < scanLineSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + i]);
		lowPassFilter.reset();
		for (int i = scanLineSamples - 1; i >= 0; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < horizontalPixels; ++i) {
			int position = (i * scanLineSamples) / horizontalPixels;
			pixelBuffer.pixels[i] = ColorConverter.GRAY(scratchBuffer[position]);
		}
		pixelBuffer.width = horizontalPixels;
		pixelBuffer.height = 1;
		return true;
	}
}
