/*
SSTV Demodulator

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Demodulator {
	private final SimpleMovingAverage powerAvg;
	private final ComplexMovingAverage syncPulse5msFilter;
	private final ComplexMovingAverage syncPulse9msFilter;
	private final ComplexMovingAverage syncPulse20msFilter;
	private final ComplexMovingAverage scanLineFilter;
	private final ComplexMovingAverage baseBandLowPass;
	private final FrequencyModulation scanLineDemod;
	private final SchmittTrigger syncPulseTrigger;
	private final Phasor syncPulseOscillator;
	private final Phasor scanLineOscillator;
	private final Phasor baseBandOscillator;
	private final Delay syncPulse5msDelay;
	private final Delay syncPulse9msDelay;
	private final Delay syncPulse20msDelay;
	private final int syncPulseLowMark;
	private final int syncPulseHighMark;
	private float syncPulse5msMaxValue;
	private float syncPulse9msMaxValue;
	private float syncPulse20msMaxValue;
	private int syncPulse5msMaxPosition;
	private int syncPulse9msMaxPosition;
	private int syncPulse20msMaxPosition;
	private int syncPulseCounter;
	private Complex baseBand;
	private Complex syncPulse;
	private Complex syncPulse5ms;
	private Complex syncPulse9ms;
	private Complex syncPulse20ms;
	private Complex scanLine;

	public enum SyncPulseWidth {
		FiveMilliSeconds,
		NineMilliSeconds,
		TwentyMilliSeconds
	}

	public SyncPulseWidth syncPulseWidth;
	public int syncPulseOffset;

	Demodulator(int sampleRate) {
		double powerWindowSeconds = 0.1;
		int powerWindowSamples = (int) Math.round(powerWindowSeconds * sampleRate) | 1;
		powerAvg = new SimpleMovingAverage(powerWindowSamples);
		float blackFrequency = 1500;
		float whiteFrequency = 2300;
		float scanLineBandwidth = whiteFrequency - blackFrequency;
		scanLineDemod = new FrequencyModulation(scanLineBandwidth, sampleRate);
		float scanLineCutoff = scanLineBandwidth / 2;
		int scanLineFilterSamples = (int) Math.round(0.443 * sampleRate / scanLineCutoff) | 1;
		scanLineFilter = new ComplexMovingAverage(scanLineFilterSamples);
		double syncPulse5msSeconds = 0.0055;
		double syncPulse9msSeconds = 0.009;
		double syncPulse20msSeconds = 0.020;
		int syncPulse5msSamples = (int) Math.round(syncPulse5msSeconds * sampleRate) | 1;
		int syncPulse9msSamples = (int) Math.round(syncPulse9msSeconds * sampleRate) | 1;
		int syncPulse20msSamples = (int) Math.round(syncPulse20msSeconds * sampleRate) | 1;
		syncPulseLowMark = syncPulse5msSamples / 2;
		syncPulseHighMark = syncPulse20msSamples * 2;
		syncPulse5msFilter = new ComplexMovingAverage(syncPulse5msSamples);
		syncPulse9msFilter = new ComplexMovingAverage(syncPulse9msSamples);
		syncPulse20msFilter = new ComplexMovingAverage(syncPulse20msSamples);
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
		int syncPulse5msDelaySamples = (powerWindowSamples - 1) / 2 - (syncPulse5msSamples - 1) / 2;
		int syncPulse9msDelaySamples = (powerWindowSamples - 1) / 2 - (syncPulse9msSamples - 1) / 2;
		int syncPulse20msDelaySamples = (powerWindowSamples - 1) / 2 - (syncPulse20msSamples - 1) / 2;
		syncPulse5msDelay = new Delay(syncPulse5msDelaySamples);
		syncPulse9msDelay = new Delay(syncPulse9msDelaySamples);
		syncPulse20msDelay = new Delay(syncPulse20msDelaySamples);
		syncPulseTrigger = new SchmittTrigger(0.17f, 0.19f);
		baseBand = new Complex();
		syncPulse = new Complex();
		syncPulse5ms = new Complex();
		syncPulse9ms = new Complex();
		syncPulse20ms = new Complex();
		scanLine = new Complex();
	}

	public boolean process(float[] buffer) {
		boolean syncPulseDetected = false;
		for (int i = 0; i < buffer.length; ++i) {
			baseBand = baseBandLowPass.avg(baseBand.set(buffer[i]).mul(baseBandOscillator.rotate()));
			syncPulse = syncPulse.set(baseBand).mul(syncPulseOscillator.rotate());
			syncPulse5ms = syncPulse5msFilter.avg(syncPulse5ms.set(syncPulse));
			syncPulse9ms = syncPulse9msFilter.avg(syncPulse9ms.set(syncPulse));
			syncPulse20ms = syncPulse20msFilter.avg(syncPulse20ms.set(syncPulse));
			scanLine = scanLineFilter.avg(scanLine.set(baseBand).mul(scanLineOscillator.rotate()));
			float averagePower = powerAvg.avg(baseBand.norm());
			float syncPulse5msValue = syncPulse5msDelay.push(syncPulse5ms.norm()) / averagePower;
			float syncPulse9msValue = syncPulse9msDelay.push(syncPulse9ms.norm()) / averagePower;
			float syncPulse20msValue = syncPulse20msDelay.push(syncPulse20ms.norm()) / averagePower;
			float scanLineValue = scanLineDemod.demod(scanLine);
			float scanLineLevel = 0.5f * (scanLineValue + 1);
			if (syncPulseTrigger.latch(syncPulse5msValue)) {
				if (syncPulse5msMaxValue < syncPulse5msValue) {
					syncPulse5msMaxValue = syncPulse5msValue;
					syncPulse5msMaxPosition = syncPulseCounter;
				}
				if (syncPulse9msMaxValue < syncPulse9msValue) {
					syncPulse9msMaxValue = syncPulse9msValue;
					syncPulse9msMaxPosition = syncPulseCounter;
				}
				if (syncPulse20msMaxValue < syncPulse20msValue) {
					syncPulse20msMaxValue = syncPulse20msValue;
					syncPulse20msMaxPosition = syncPulseCounter;
				}
				++syncPulseCounter;
			} else if (syncPulseCounter > syncPulseLowMark && syncPulseCounter < syncPulseHighMark) {
				int filterDelay = (powerAvg.length - 1) / 2 - (scanLineFilter.length - 1) / 2;
				syncPulseOffset = i - syncPulseCounter - filterDelay;
				float mid9ms20msSum = ((9.f / 20.f) + 1.f) / 2.f;
				float mid9ms20msPwr = mid9ms20msSum * mid9ms20msSum;
				float mid5ms9msSum = ((5.f / 9.f) + 1.f) / 2.f;
				float mid5ms9msPwr = mid5ms9msSum * mid5ms9msSum;
				if (syncPulse20msMaxValue > mid9ms20msPwr * syncPulse9msMaxValue) {
					syncPulseOffset += syncPulse20msMaxPosition;
					syncPulseWidth = SyncPulseWidth.TwentyMilliSeconds;
				} else if (syncPulse9msMaxValue > mid5ms9msPwr * syncPulse5msMaxValue) {
					syncPulseOffset += syncPulse9msMaxPosition;
					syncPulseWidth = SyncPulseWidth.NineMilliSeconds;
				} else {
					syncPulseOffset += syncPulse5msMaxPosition;
					syncPulseWidth = SyncPulseWidth.FiveMilliSeconds;
				}
				syncPulseDetected = true;
				syncPulseCounter = 0;
				syncPulse5msMaxValue = 0;
				syncPulse9msMaxValue = 0;
				syncPulse20msMaxValue = 0;
			} else {
				syncPulseCounter = 0;
				syncPulse5msMaxValue = 0;
				syncPulse9msMaxValue = 0;
				syncPulse20msMaxValue = 0;
			}
			buffer[i] = scanLineLevel;
		}
		return syncPulseDetected;
	}
}
