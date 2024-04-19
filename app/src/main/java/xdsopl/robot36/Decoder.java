/*
SSTV Decoder

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Decoder {

	private final Demodulator demodulator;
	private final float[] scanLineBuffer;
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

	public int curLine;
	private int curSample;

	Decoder(int[] scopePixels, int scopeWidth, int scopeHeight, int sampleRate) {
		this.scopePixels = scopePixels;
		this.scopeWidth = scopeWidth;
		this.scopeHeight = scopeHeight;
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

	private int scanLineStdDev(int[] lines) {
		double mean = scanLineMean(lines);
		double stdDev = 0;
		for (int diff : lines)
			stdDev += (diff - mean) * (diff - mean);
		stdDev = Math.sqrt(stdDev / lines.length);
		return (int) Math.round(stdDev);
	}

	private void processOneLine(int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex < 0 || prevPulseIndex + scanLineSamples >= scanLineBuffer.length)
			return;
		for (int i = 0; i < scopeWidth; ++i) {
			int position = (i * scanLineSamples) / scopeWidth + prevPulseIndex;
			int intensity = (int) Math.round(255 * Math.sqrt(scanLineBuffer[position]));
			int pixelColor = 0xff000000 | 0x00010101 * intensity;
			scopePixels[scopeWidth * curLine + i] = pixelColor;
		}
	}

	private boolean processSyncPulse(int[] pulses, int[] lines, int index) {
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
		if (pulses[0] >= lines[0]) {
			int lineSamples = lines[0];
			int endPulse = pulses[0];
			int extrapolate = endPulse / lineSamples;
			int firstPulse = endPulse - extrapolate * lineSamples;
			for (int pulseIndex = firstPulse; pulseIndex < endPulse; pulseIndex += lineSamples)
				processOneLine(pulseIndex, lineSamples);
		}
		for (int i = 0; i < lines.length; ++i)
			processOneLine(pulses[i], lines[i]);
		int shift = pulses[pulses.length - 1];
		adjustSyncPulses(last5msSyncPulses, shift);
		adjustSyncPulses(last9msSyncPulses, shift);
		adjustSyncPulses(last20msSyncPulses, shift);
		int endSample = curSample;
		curSample = 0;
		for (int i = shift; i < endSample; ++i)
			scanLineBuffer[curSample++] = scanLineBuffer[i];
		for (int i = 0; i < scopeWidth; ++i)
			scopePixels[scopeWidth * (curLine + scopeHeight) + i] = scopePixels[scopeWidth * curLine + i];
		curLine = (curLine + 1) % scopeHeight;
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
					return processSyncPulse(last5msSyncPulses, last5msScanLines, syncPulseIndex);
				case NineMilliSeconds:
					return processSyncPulse(last9msSyncPulses, last9msScanLines, syncPulseIndex);
				case TwentyMilliSeconds:
					return processSyncPulse(last20msSyncPulses, last20msScanLines, syncPulseIndex);
			}
		}
		return false;
	}
}
