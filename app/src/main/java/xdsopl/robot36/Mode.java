/*
Mode interface

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public interface Mode {
	String getName();

	int getScanLineSamples();

	boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int syncPulseIndex, int scanLineSamples, float frequencyOffset);
}
