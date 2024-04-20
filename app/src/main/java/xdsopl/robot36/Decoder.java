/*
SSTV Decoder

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import java.util.ArrayList;

public class Decoder {

	private final Demodulator demodulator;
	private final float[] scanLineBuffer;
	private final int[] evenBuffer;
	private final int[] oddBuffer;
	private final int[] scopePixels;
	private final int[] last5msSyncPulses;
	private final int[] last9msSyncPulses;
	private final int[] last20msSyncPulses;
	private final int[] last5msScanLines;
	private final int[] last9msScanLines;
	private final int[] last20msScanLines;
	private final int scanLineToleranceSamples;
	private final int scopeWidth;
	private final int scopeHeight;
	private final Mode rawMode;
	private final ArrayList<Mode> syncPulse5msModes;
	private final ArrayList<Mode> syncPulse9msModes;
	private final ArrayList<Mode> syncPulse20msModes;

	public String curMode;
	public int curLine;
	private int curSample;

	Decoder(int[] scopePixels, int scopeWidth, int scopeHeight, int sampleRate) {
		this.scopePixels = scopePixels;
		this.scopeWidth = scopeWidth;
		this.scopeHeight = scopeHeight;
		evenBuffer = new int[scopeWidth];
		oddBuffer = new int[scopeWidth];
		demodulator = new Demodulator(sampleRate);
		double scanLineMaxSeconds = 5;
		int scanLineMaxSamples = (int) Math.round(scanLineMaxSeconds * sampleRate);
		scanLineBuffer = new float[scanLineMaxSamples];
		int scanLineCount = 3;
		last5msScanLines = new int[scanLineCount];
		last9msScanLines = new int[scanLineCount];
		last20msScanLines = new int[scanLineCount];
		int syncPulseCount = scanLineCount + 1;
		last5msSyncPulses = new int[syncPulseCount];
		last9msSyncPulses = new int[syncPulseCount];
		last20msSyncPulses = new int[syncPulseCount];
		double scanLineToleranceSeconds = 0.001;
		scanLineToleranceSamples = (int) Math.round(scanLineToleranceSeconds * sampleRate);
		rawMode = new RawDecoder();
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
		syncPulse20msModes.add(new PaulDon("50", 0.09152, sampleRate));
		syncPulse20msModes.add(new PaulDon("90", 0.17024, sampleRate));
		syncPulse20msModes.add(new PaulDon("120", 0.1216, sampleRate));
		syncPulse20msModes.add(new PaulDon("160", 0.195584, sampleRate));
		syncPulse20msModes.add(new PaulDon("180", 0.18304, sampleRate));
		syncPulse20msModes.add(new PaulDon("240", 0.24448, sampleRate));
		syncPulse20msModes.add(new PaulDon("290", 0.2288, sampleRate));
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

	private double scanLineStdDev(int[] lines) {
		double mean = scanLineMean(lines);
		double stdDev = 0;
		for (int diff : lines)
			stdDev += (diff - mean) * (diff - mean);
		stdDev = Math.sqrt(stdDev / lines.length);
		return stdDev;
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

	private void copyLines(int lines) {
		if (lines > 0) {
			System.arraycopy(evenBuffer, 0, scopePixels, scopeWidth * curLine, scopeWidth);
			System.arraycopy(evenBuffer, 0, scopePixels, scopeWidth * (curLine + scopeHeight), scopeWidth);
			curLine = (curLine + 1) % scopeHeight;
		}
		if (lines == 2) {
			System.arraycopy(oddBuffer, 0, scopePixels, scopeWidth * curLine, scopeWidth);
			System.arraycopy(oddBuffer, 0, scopePixels, scopeWidth * (curLine + scopeHeight), scopeWidth);
			curLine = (curLine + 1) % scopeHeight;
		}
	}

	private boolean processSyncPulse(ArrayList<Mode> modes, int[] pulses, int[] lines, int index) {
		for (int i = 1; i < lines.length; ++i)
			lines[i - 1] = lines[i];
		lines[lines.length - 1] = index - pulses[pulses.length - 1];
		for (int i = 1; i < pulses.length; ++i)
			pulses[i - 1] = pulses[i];
		pulses[pulses.length - 1] = index;
		if (lines[0] == 0)
			return false;
		if (scanLineStdDev(lines) > scanLineToleranceSamples)
			return false;
		int meanSamples = (int) Math.round(scanLineMean(lines));
		Mode mode = detectMode(modes, meanSamples);
		curMode = mode.getName();
		if (pulses[0] >= meanSamples) {
			int endPulse = pulses[0];
			int extrapolate = endPulse / meanSamples;
			int firstPulse = endPulse - extrapolate * meanSamples;
			for (int pulseIndex = firstPulse; pulseIndex < endPulse; pulseIndex += meanSamples)
				copyLines(mode.decodeScanLine(evenBuffer, oddBuffer, scanLineBuffer, pulseIndex, meanSamples));
		}
		for (int i = 0; i < lines.length; ++i)
			copyLines(mode.decodeScanLine(evenBuffer, oddBuffer, scanLineBuffer, pulses[i], lines[i]));
		int shift = pulses[pulses.length - 1] - (meanSamples * 3) / 4;
		adjustSyncPulses(last5msSyncPulses, shift);
		adjustSyncPulses(last9msSyncPulses, shift);
		adjustSyncPulses(last20msSyncPulses, shift);
		int endSample = curSample;
		curSample = 0;
		for (int i = shift; i < endSample; ++i)
			scanLineBuffer[curSample++] = scanLineBuffer[i];
		return true;
	}

	public boolean process(float[] recordBuffer) {
		boolean syncPulseDetected = demodulator.process(recordBuffer);
		int syncPulseIndex = curSample + demodulator.syncPulseOffset;
		for (float v : recordBuffer) {
			scanLineBuffer[curSample++] = v;
			if (curSample >= scanLineBuffer.length) {
				int shift = scanLineBuffer.length / 2;
				syncPulseIndex -= shift;
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
					return processSyncPulse(syncPulse5msModes, last5msSyncPulses, last5msScanLines, syncPulseIndex);
				case NineMilliSeconds:
					return processSyncPulse(syncPulse9msModes, last9msSyncPulses, last9msScanLines, syncPulseIndex);
				case TwentyMilliSeconds:
					return processSyncPulse(syncPulse20msModes, last20msSyncPulses, last20msScanLines, syncPulseIndex);
			}
		}
		return false;
	}
}
