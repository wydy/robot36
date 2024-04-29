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
	private final float[] visCodeBitFrequencies;
	private final int scanLineReserveSamples;
	private final int syncPulseToleranceSamples;
	private final int scanLineToleranceSamples;
	private final int leaderToneSamples;
	private final int leaderBreakSamples;
	private final int transitionSamples;
	private final int visCodeBitSamples;
	private final int visCodeSamples;
	private final Mode rawMode;
	private final ArrayList<Mode> syncPulse5msModes;
	private final ArrayList<Mode> syncPulse9msModes;
	private final ArrayList<Mode> syncPulse20msModes;

	public Mode lastMode;
	private boolean checkHeader;
	private int visCode;
	private int curSample;
	private int lastSyncPulseIndex;
	private int lastScanLineSamples;
	private float lastFrequencyOffset;
	private float leaderFreqOffset;

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
		double leaderToneSeconds = 0.3;
		leaderToneSamples = (int) Math.round(leaderToneSeconds * sampleRate);
		double leaderBreakSeconds = 0.01;
		leaderBreakSamples = (int) Math.round(leaderBreakSeconds * sampleRate);
		double transitionSeconds = 0.0005;
		transitionSamples = (int) Math.round(transitionSeconds * sampleRate);
		double visCodeBitSeconds = 0.03;
		visCodeBitSamples = (int) Math.round(visCodeBitSeconds * sampleRate);
		double visCodeSeconds = 0.3;
		visCodeSamples = (int) Math.round(visCodeSeconds * sampleRate);
		visCodeBitFrequencies = new float[10];
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
		rawMode = new RawDecoder(sampleRate);
		Mode robot36 = new Robot_36_Color(sampleRate);
		lastMode = robot36;
		lastScanLineSamples = robot36.getScanLineSamples();
		curSample = scanLineReserveSamples;
		lastSyncPulseIndex = curSample;
		syncPulse5msModes = new ArrayList<>();
		syncPulse5msModes.add(RGBModes.Wraase_SC2_180(sampleRate));
		syncPulse5msModes.add(RGBModes.Martin("1", 44, 0.146432, sampleRate));
		syncPulse5msModes.add(RGBModes.Martin("2", 40, 0.073216, sampleRate));
		syncPulse9msModes = new ArrayList<>();
		syncPulse9msModes.add(robot36);
		syncPulse9msModes.add(new Robot_72_Color(sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("1", 60, 0.138240, sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("2", 56, 0.088064, sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("DX", 76, 0.3456, sampleRate));
		syncPulse20msModes = new ArrayList<>();
		syncPulse20msModes.add(new PaulDon("50", 93, 320, 0.09152, sampleRate));
		syncPulse20msModes.add(new PaulDon("90", 99, 320, 0.17024, sampleRate));
		syncPulse20msModes.add(new PaulDon("120", 95, 640, 0.1216, sampleRate));
		syncPulse20msModes.add(new PaulDon("160", 98, 512, 0.195584, sampleRate));
		syncPulse20msModes.add(new PaulDon("180", 96, 640, 0.18304, sampleRate));
		syncPulse20msModes.add(new PaulDon("240", 97, 640, 0.24448, sampleRate));
		syncPulse20msModes.add(new PaulDon("290", 94, 640, 0.2288, sampleRate));
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

	private Mode findMode(int code) {
		for (Mode mode : syncPulse5msModes)
			if (mode.getCode() == code)
				return mode;
		for (Mode mode : syncPulse9msModes)
			if (mode.getCode() == code)
				return mode;
		for (Mode mode : syncPulse20msModes)
			if (mode.getCode() == code)
				return mode;
		return rawMode;
	}

	private void copyUnscaled() {
		for (int row = 0; row < pixelBuffer.height; ++row) {
			int line = scopeBuffer.width * scopeBuffer.line;
			System.arraycopy(pixelBuffer.pixels, row * pixelBuffer.width, scopeBuffer.pixels, line, pixelBuffer.width);
			Arrays.fill(scopeBuffer.pixels, line + pixelBuffer.width, line + scopeBuffer.width, 0);
			System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * (scopeBuffer.line + scopeBuffer.height / 2), scopeBuffer.width);
			scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
		}
	}

	private void copyScaled(int scale) {
		for (int row = 0; row < pixelBuffer.height; ++row) {
			int line = scopeBuffer.width * scopeBuffer.line;
			for (int col = 0; col < pixelBuffer.width; ++col)
				for (int i = 0; i < scale; ++i)
					scopeBuffer.pixels[line + col * scale + i] = pixelBuffer.pixels[pixelBuffer.width * row + col];
			Arrays.fill(scopeBuffer.pixels, line + pixelBuffer.width * scale, line + scopeBuffer.width, 0);
			System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * (scopeBuffer.line + scopeBuffer.height / 2), scopeBuffer.width);
			scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
			for (int i = 1; i < scale; ++i) {
				System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * scopeBuffer.line, scopeBuffer.width);
				System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * (scopeBuffer.line + scopeBuffer.height / 2), scopeBuffer.width);
				scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
			}
		}
	}

	private void copyLines(boolean okay) {
		if (!okay)
			return;
		int scale = scopeBuffer.width / pixelBuffer.width;
		if (scale == 1)
			copyUnscaled();
		else
			copyScaled(scale);
	}

	private boolean detectHeader(int syncPulseIndex) {
		if (!checkHeader)
			return false;
		if (syncPulseIndex < 2 * leaderBreakSamples || curSample < syncPulseIndex + leaderToneSamples + visCodeSamples)
			return false;
		checkHeader = false;
		float preBreakFreq = 0;
		for (int i = 0; i < leaderBreakSamples; ++i)
			preBreakFreq += scanLineBuffer[syncPulseIndex - 2 * leaderBreakSamples + i];
		float centerFreq = 1900;
		float halfBandWidth = 400;
		preBreakFreq = preBreakFreq * halfBandWidth / leaderBreakSamples + centerFreq;
		if (preBreakFreq < 1850 || preBreakFreq > 1950)
			return false;
		float leaderFreq = 0;
		for (int i = transitionSamples; i < leaderToneSamples - transitionSamples; ++i)
			leaderFreq += scanLineBuffer[syncPulseIndex + i];
		leaderFreqOffset = leaderFreq / (leaderToneSamples - 2 * transitionSamples);
		leaderFreq = leaderFreq * halfBandWidth / (leaderToneSamples - 2 * transitionSamples) + centerFreq;
		if (leaderFreq < 1850 || leaderFreq > 1950)
			return false;
		Arrays.fill(visCodeBitFrequencies, 0);
		for (int j = 0; j < 10; ++j)
			for (int i = transitionSamples; i < visCodeBitSamples - transitionSamples; ++i)
				visCodeBitFrequencies[j] += scanLineBuffer[syncPulseIndex + leaderToneSamples + visCodeBitSamples * j + i] - leaderFreqOffset;
		for (int i = 0; i < 10; ++i)
			visCodeBitFrequencies[i] = visCodeBitFrequencies[i] * halfBandWidth / (visCodeBitSamples - 2 * transitionSamples) + centerFreq;
		if (visCodeBitFrequencies[0] < 1150 || visCodeBitFrequencies[0] > 1250 || visCodeBitFrequencies[9] < 1150 || visCodeBitFrequencies[9] > 1250)
			return false;
		for (int i = 1; i < 9; ++i)
			if (visCodeBitFrequencies[i] < 1050 || visCodeBitFrequencies[i] > 1150 && visCodeBitFrequencies[i] < 1250 || visCodeBitFrequencies[i] > 1350)
				return false;
		visCode = 0;
		for (int i = 0; i < 8; ++i)
			visCode |= (visCodeBitFrequencies[i + 1] < 1200 ? 1 : 0) << i;
		boolean check = true;
		for (int i = 0; i < 8; ++i)
			check ^= (visCode & 1 << i) != 0;
		visCode &= 127;
		return check;
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
		if (pulses[0] >= scanLineSamples && pictureChanged) {
			int endPulse = pulses[0];
			int extrapolate = endPulse / scanLineSamples;
			int firstPulse = endPulse - extrapolate * scanLineSamples;
			for (int pulseIndex = firstPulse; pulseIndex < endPulse; pulseIndex += scanLineSamples)
				copyLines(mode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, scopeBuffer.width, pulseIndex, scanLineSamples, frequencyOffset));
		}
		for (int i = pictureChanged ? 0 : lines.length - 1; i < lines.length; ++i)
			copyLines(mode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, scopeBuffer.width, pulses[i], lines[i], frequencyOffset));
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

	public boolean process(float[] recordBuffer, int channelSelect) {
		boolean syncPulseDetected = demodulator.process(recordBuffer, channelSelect);
		int syncPulseIndex = curSample + demodulator.syncPulseOffset;
		int channels = channelSelect > 0 ? 2 : 1;
		for (int j = 0; j < recordBuffer.length / channels; ++j) {
			scanLineBuffer[curSample++] = recordBuffer[j];
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
					checkHeader = true;
					return processSyncPulse(syncPulse9msModes, last9msFrequencyOffsets, last9msSyncPulses, last9msScanLines, syncPulseIndex);
				case TwentyMilliSeconds:
					return processSyncPulse(syncPulse20msModes, last20msFrequencyOffsets, last20msSyncPulses, last20msScanLines, syncPulseIndex);
				default:
					return false;
			}
		}
		if (detectHeader(last9msSyncPulses[last9msSyncPulses.length - 1])) {
			Mode visMode = findMode(visCode);
			if (visMode != rawMode) {
				lastMode = visMode;
				lastSyncPulseIndex = last9msSyncPulses[last9msSyncPulses.length - 1] + leaderToneSamples + visCodeSamples + visMode.getFirstSyncPulseIndex();
				lastScanLineSamples = visMode.getScanLineSamples();
				lastFrequencyOffset = leaderFreqOffset;
				int shift = lastSyncPulseIndex - scanLineReserveSamples;
				if (shift > scanLineReserveSamples) {
					lastSyncPulseIndex -= shift;
					adjustSyncPulses(last5msSyncPulses, shift);
					adjustSyncPulses(last9msSyncPulses, shift);
					adjustSyncPulses(last20msSyncPulses, shift);
					int endSample = curSample;
					curSample = 0;
					for (int i = shift; i < endSample; ++i)
						scanLineBuffer[curSample++] = scanLineBuffer[i];
				}
			}
		}
		if (lastSyncPulseIndex >= scanLineReserveSamples && curSample > lastSyncPulseIndex + (lastScanLineSamples * 5) / 4) {
			copyLines(lastMode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, scopeBuffer.width, lastSyncPulseIndex, lastScanLineSamples, lastFrequencyOffset));
			lastSyncPulseIndex += lastScanLineSamples;
			return true;
		}
		return false;
	}
}
