/*
SSTV Demodulator

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Demodulator {
	private final SimpleMovingAverage powerAvg;
	private final ComplexMovingAverage syncPulseFilter;
	private final ComplexMovingAverage scanLineFilter;
	private final ComplexMovingAverage baseBandLowPass;
	private final FrequencyModulation scanLineDemod;
	private final SchmittTrigger syncPulseTrigger;
	private final Phasor syncPulseOscillator;
	private final Phasor scanLineOscillator;
	private final Phasor baseBandOscillator;
	private final Delay syncPulseDelay;
	private final Delay scanLineDelay;
	private final int syncPulseSamples;
	private float syncPulseMaxValue;
	private int syncPulseMaxPosition;
	private int syncPulseCounter;
	private Complex baseBand;
	private Complex syncPulse;
	private Complex scanLine;

	public int syncPulseOffset;

	Demodulator(int sampleRate) {
		double powerWindowSeconds = 0.5;
		int powerWindowSamples = (int) Math.round(powerWindowSeconds * sampleRate) | 1;
		powerAvg = new SimpleMovingAverage(powerWindowSamples);
		float blackFrequency = 1500;
		float whiteFrequency = 2300;
		float scanLineBandwidth = whiteFrequency - blackFrequency;
		scanLineDemod = new FrequencyModulation(scanLineBandwidth, sampleRate);
		float scanLineCutoff = scanLineBandwidth / 2;
		int scanLineFilterSamples = (int) Math.round(0.443 * sampleRate / scanLineCutoff) | 1;
		scanLineFilter = new ComplexMovingAverage(scanLineFilterSamples);
		double syncPulseSeconds = 0.009;
		syncPulseSamples = (int) Math.round(syncPulseSeconds * sampleRate) | 1;
		syncPulseFilter = new ComplexMovingAverage(syncPulseSamples);
		float lowestFrequency = 1100;
		float highestFrequency = 2300;
		float cutoffFrequency = (highestFrequency - lowestFrequency) / 2;
		int lowPassSamples = (int) Math.round(0.443 * sampleRate / cutoffFrequency) | 1;
		baseBandLowPass = new ComplexMovingAverage(lowPassSamples);
		float centerFrequency = (lowestFrequency + highestFrequency) / 2;
		baseBandOscillator = new Phasor(-centerFrequency, sampleRate);
		float syncPulseFrequency = 1200;
		syncPulseOscillator = new Phasor(-(syncPulseFrequency - centerFrequency), sampleRate);
		float grayFrequency = (blackFrequency + whiteFrequency) / 2;
		scanLineOscillator = new Phasor(-(grayFrequency - centerFrequency), sampleRate);
		int syncPulseDelaySamples = (powerWindowSamples - 1) / 2;
		syncPulseDelay = new Delay(syncPulseDelaySamples);
		int scanLineDelaySamples = (powerWindowSamples - 1) / 2 + (syncPulseSamples - 1) / 2 - (scanLineFilterSamples - 1) / 2;
		scanLineDelay = new Delay(scanLineDelaySamples);
		syncPulseTrigger = new SchmittTrigger(0.17f, 0.19f);
		baseBand = new Complex();
		syncPulse = new Complex();
		scanLine = new Complex();
	}

	public boolean process(float[] buffer) {
		boolean syncPulseDetected = false;
		for (int i = 0; i < buffer.length; ++i) {
			baseBand = baseBandLowPass.avg(baseBand.set(buffer[i]).mul(baseBandOscillator.rotate()));
			syncPulse = syncPulseFilter.avg(syncPulse.set(baseBand).mul(syncPulseOscillator.rotate()));
			scanLine = scanLineFilter.avg(scanLine.set(baseBand).mul(scanLineOscillator.rotate()));
			float syncPulseValue = syncPulseDelay.push(syncPulse.norm()) / powerAvg.avg(baseBand.norm());
			float scanLineValue = scanLineDelay.push(scanLineDemod.demod(scanLine));
			float scanLineLevel = Math.min(Math.max(0.5f * (scanLineValue + 1), 0), 1);
			if (syncPulseTrigger.latch(syncPulseValue)) {
				if (syncPulseMaxValue < syncPulseValue) {
					syncPulseMaxValue = syncPulseValue;
					syncPulseMaxPosition = syncPulseCounter;
				}
				++syncPulseCounter;
			} else if (syncPulseCounter > 0 && syncPulseCounter < syncPulseSamples) {
				syncPulseOffset = i + syncPulseMaxPosition - syncPulseCounter;
				syncPulseDetected = true;
				syncPulseCounter = 0;
				syncPulseMaxValue = 0;
			} else {
				syncPulseCounter = 0;
				syncPulseMaxValue = 0;
			}
			buffer[i] = scanLineLevel;
		}
		return syncPulseDetected;
	}
}
