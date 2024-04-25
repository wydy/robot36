/*
SSTV Decoder

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import java.util.ArrayList;
import java.util.Arrays;

public class Decoder {

	private final Demodulator demodulator;
	private final PixelBuffer pixelBuffer;
	private final PixelBuffer scopeBuffer;
	private final float[] scanLineBuffer;
	private final float[] scratchBuffer;
	private final int[] last5msSyncPulses;
	private final int[] last9msSyncPulses;
	private final int[] last20msSyncPulses;
	private final int[] last5msScanLines;
	private final int[] last9msScanLines;
	private final int[] last20msScanLines;
	private final float[] last5msFrequencyOffsets;
	private final float[] last9msFrequencyOffsets;
	private final float[] last20msFrequencyOffsets;
	private final int scanLineReserveSamples;
	private final int syncPulseToleranceSamples;
	private final int scanLineToleranceSamples;
	private final Mode rawMode;
	private final ArrayList<Mode> syncPulse5msModes;
	private final ArrayList<Mode> syncPulse9msModes;
	private final ArrayList<Mode> syncPulse20msModes;

	public Mode lastMode;
	public int curLine;
	private int curSample;
	private int lastSyncPulseIndex;
	private int lastScanLineSamples;
	private float lastFrequencyOffset;

	Decoder(PixelBuffer scopeBuffer, int sampleRate) {
		this.scopeBuffer = scopeBuffer;
		pixelBuffer = new PixelBuffer(scopeBuffer.width, 2);
		demodulator = new Demodulator(sampleRate);
		double scanLineMaxSeconds = 7;
		int scanLineMaxSamples = (int) Math.round(scanLineMaxSeconds * sampleRate);
		scanLineBuffer = new float[scanLineMaxSamples];
		double scratchBufferSeconds = 1.1;
		int scratchBufferSamples = (int) Math.round(scratchBufferSeconds * sampleRate);
		scratchBuffer = new float[scratchBufferSamples];
		int scanLineCount = 4;
		last5msScanLines = new int[scanLineCount];
		last9msScanLines = new int[scanLineCount];
		last20msScanLines = new int[scanLineCount];
		int syncPulseCount = scanLineCount + 1;
		last5msSyncPulses = new int[syncPulseCount];
		last9msSyncPulses = new int[syncPulseCount];
		last20msSyncPulses = new int[syncPulseCount];
		last5msFrequencyOffsets = new float[syncPulseCount];
		last9msFrequencyOffsets = new float[syncPulseCount];
		last20msFrequencyOffsets = new float[syncPulseCount];
		double syncPulseToleranceSeconds = 0.03;
		syncPulseToleranceSamples = (int) Math.round(syncPulseToleranceSeconds * sampleRate);
		double scanLineToleranceSeconds = 0.001;
		scanLineToleranceSamples = (int) Math.round(scanLineToleranceSeconds * sampleRate);
		scanLineReserveSamples = sampleRate;
		rawMode = new RawDecoder();
		lastMode = rawMode;
		lastScanLineSamples = (int) Math.round(0.150 * sampleRate);
		syncPulse5msModes = new ArrayList<>();
		syncPulse5msModes.add(RGBModes.Wraase_SC2_180(sampleRate));
		syncPulse5msModes.add(RGBModes.Martin("1", 0.146432, sampleRate));
		syncPulse5msModes.add(RGBModes.Martin("2", 0.073216, sampleRate));
		syncPulse9msModes = new ArrayList<>();
		syncPulse9msModes.add(new Robot_36_Color(sampleRate));
		syncPulse9msModes.add(new Robot_72_Color(sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("1", 0.138240, sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("2", 0.088064, sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("DX", 0.3456, sampleRate));
		syncPulse20msModes = new ArrayList<>();
		syncPulse20msModes.add(new PaulDon("50", 320, 0.09152, sampleRate));
		syncPulse20msModes.add(new PaulDon("90", 320, 0.17024, sampleRate));
		syncPulse20msModes.add(new PaulDon("120", 640, 0.1216, sampleRate));
		syncPulse20msModes.add(new PaulDon("160", 512, 0.195584, sampleRate));
		syncPulse20msModes.add(new PaulDon("180", 640, 0.18304, sampleRate));
		syncPulse20msModes.add(new PaulDon("240", 640, 0.24448, sampleRate));
		syncPulse20msModes.add(new PaulDon("290", 640, 0.2288, sampleRate));
	}

	private void adjustSyncPulses(int[] pulses, int shift) {
		for (int i = 0; i < pulses.length; ++i)
			pulses[i] -= shift;
	}

	private double scanLineMean(int[] lines) {
		double mean = 0;
		for (int diff : lines)
			mean += diff;
		mean /= lines.length;
		return mean;
	}

	private double scanLineStdDev(int[] lines, double mean) {
		double stdDev = 0;
		for (int diff : lines)
			stdDev += (diff - mean) * (diff - mean);
		stdDev = Math.sqrt(stdDev / lines.length);
		return stdDev;
	}

	private double frequencyOffsetMean(float[] offsets) {
		double mean = 0;
		for (float diff : offsets)
			mean += diff;
		mean /= offsets.length;
		return mean;
	}

	private Mode detectMode(ArrayList<Mode> modes, int line) {
		Mode bestMode = rawMode;
		int bestDist = Integer.MAX_VALUE;
		for (Mode mode : modes) {
			int dist = Math.abs(line - mode.getScanLineSamples());
			if (dist <= scanLineToleranceSamples && dist < bestDist) {
				bestDist = dist;
				bestMode = mode;
			}
		}
		return bestMode;
	}

	private void copyLines(boolean okay) {
		if (!okay)
			return;
		for (int row = 0; row < pixelBuffer.height; ++row) {
			System.arraycopy(pixelBuffer.pixels, row * pixelBuffer.width, scopeBuffer.pixels, scopeBuffer.width * curLine, pixelBuffer.width);
			Arrays.fill(scopeBuffer.pixels, scopeBuffer.width * curLine + pixelBuffer.width, scopeBuffer.width * curLine + scopeBuffer.width, 0);
			System.arraycopy(pixelBuffer.pixels, row * pixelBuffer.width, scopeBuffer.pixels, scopeBuffer.width * (curLine + scopeBuffer.height / 2), pixelBuffer.width);
			Arrays.fill(scopeBuffer.pixels, scopeBuffer.width * (curLine + scopeBuffer.height / 2) + pixelBuffer.width, scopeBuffer.width * (curLine + scopeBuffer.height / 2) + scopeBuffer.width, 0);
			curLine = (curLine + 1) % (scopeBuffer.height / 2);
		}
	}

	private boolean processSyncPulse(ArrayList<Mode> modes, float[] freqOffs, int[] pulses, int[] lines, int index) {
		for (int i = 1; i < lines.length; ++i)
			lines[i - 1] = lines[i];
		lines[lines.length - 1] = index - pulses[pulses.length - 1];
		for (int i = 1; i < pulses.length; ++i)
			pulses[i - 1] = pulses[i];
		pulses[pulses.length - 1] = index;
		for (int i = 1; i < freqOffs.length; ++i)
			freqOffs[i - 1] = freqOffs[i];
		freqOffs[pulses.length - 1] = demodulator.frequencyOffset;
		if (lines[0] == 0)
			return false;
		double mean = scanLineMean(lines);
		int scanLineSamples = (int) Math.round(mean);
		if (scanLineSamples > scratchBuffer.length)
			return false;
		if (scanLineStdDev(lines, mean) > scanLineToleranceSamples)
			return false;
		float frequencyOffset = (float) frequencyOffsetMean(freqOffs);
		Mode mode = detectMode(modes, scanLineSamples);
		boolean pictureChanged = lastMode != mode
			|| Math.abs(lastScanLineSamples - scanLineSamples) > scanLineToleranceSamples
			|| Math.abs(lastSyncPulseIndex + scanLineSamples - pulses[pulses.length - 1]) > syncPulseToleranceSamples;
		pixelBuffer.width = scopeBuffer.width;
		if (pulses[0] >= scanLineSamples && pictureChanged) {
			int endPulse = pulses[0];
			int extrapolate = endPulse / scanLineSamples;
			int firstPulse = endPulse - extrapolate * scanLineSamples;
			for (int pulseIndex = firstPulse; pulseIndex < endPulse; pulseIndex += scanLineSamples)
				copyLines(mode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, pulseIndex, scanLineSamples, frequencyOffset));
		}
		for (int i = pictureChanged ? 0 : lines.length - 1; i < lines.length; ++i)
			copyLines(mode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, pulses[i], lines[i], frequencyOffset));
		int shift = pulses[pulses.length - 1] - scanLineReserveSamples;
		if (shift > scanLineReserveSamples) {
			adjustSyncPulses(last5msSyncPulses, shift);
			adjustSyncPulses(last9msSyncPulses, shift);
			adjustSyncPulses(last20msSyncPulses, shift);
			int endSample = curSample;
			curSample = 0;
			for (int i = shift; i < endSample; ++i)
				scanLineBuffer[curSample++] = scanLineBuffer[i];
		}
		lastMode = mode;
		lastSyncPulseIndex = pulses[pulses.length - 1];
		lastScanLineSamples = scanLineSamples;
		lastFrequencyOffset = frequencyOffset;
		return true;
	}

	public boolean process(float[] recordBuffer) {
		boolean syncPulseDetected = demodulator.process(recordBuffer);
		int syncPulseIndex = curSample + demodulator.syncPulseOffset;
		for (float v : recordBuffer) {
			scanLineBuffer[curSample++] = v;
			if (curSample >= scanLineBuffer.length) {
				int shift = scanLineReserveSamples;
				syncPulseIndex -= shift;
				lastSyncPulseIndex -= shift;
				adjustSyncPulses(last5msSyncPulses, shift);
				adjustSyncPulses(last9msSyncPulses, shift);
				adjustSyncPulses(last20msSyncPulses, shift);
				curSample = 0;
				for (int i = shift; i < scanLineBuffer.length; ++i)
					scanLineBuffer[curSample++] = scanLineBuffer[i];
			}
		}
		if (syncPulseDetected) {
			switch (demodulator.syncPulseWidth) {
				case FiveMilliSeconds:
					return processSyncPulse(syncPulse5msModes, last5msFrequencyOffsets, last5msSyncPulses, last5msScanLines, syncPulseIndex);
				case NineMilliSeconds:
					return processSyncPulse(syncPulse9msModes, last9msFrequencyOffsets, last9msSyncPulses, last9msScanLines, syncPulseIndex);
				case TwentyMilliSeconds:
					return processSyncPulse(syncPulse20msModes, last20msFrequencyOffsets, last20msSyncPulses, last20msScanLines, syncPulseIndex);
			}
		} else if (lastSyncPulseIndex >= scanLineReserveSamples && curSample > lastSyncPulseIndex + (lastScanLineSamples * 5) / 4) {
			pixelBuffer.width = scopeBuffer.width;
			copyLines(lastMode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, lastSyncPulseIndex, lastScanLineSamples, lastFrequencyOffset));
			lastSyncPulseIndex += lastScanLineSamples;
			return true;
		}
		return false;
	}
}
