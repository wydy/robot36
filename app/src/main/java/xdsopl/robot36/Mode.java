/*
Mode interface

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public interface Mode {
	String getName();

	int getScanLineSamples();

	boolean decodeScanLine(int[] pixelBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples);
}
