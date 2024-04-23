/*
SSTV Demodulator

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Demodulator {
	private final SimpleMovingAverage syncPulseFilter;
	private final ComplexConvolution baseBandLowPass;
	private final FrequencyModulation frequencyModulation;
	private final SchmittTrigger syncPulseTrigger;
	private final Phasor baseBandOscillator;
	private final Delay syncPulseValueDelay;
	private final float syncPulseFrequencyValue;
	private final float syncPulseFrequencyTolerance;
	private final int syncPulse5msMinSamples;
	private final int syncPulse5msMaxSamples;
	private final int syncPulse9msMaxSamples;
	private final int syncPulse20msMaxSamples;
	private final int syncPulseFilterDelay;
	private int syncPulseCounter;
	private Complex baseBand;

	public enum SyncPulseWidth {
		FiveMilliSeconds,
		NineMilliSeconds,
		TwentyMilliSeconds
	}

	public SyncPulseWidth syncPulseWidth;
	public int syncPulseOffset;
	public float frequencyOffset;

	Demodulator(int sampleRate) {
		float blackFrequency = 1500;
		float whiteFrequency = 2300;
		float scanLineBandwidth = whiteFrequency - blackFrequency;
		frequencyModulation = new FrequencyModulation(scanLineBandwidth, sampleRate);
		double syncPulse5msSeconds = 0.005;
		double syncPulse9msSeconds = 0.009;
		double syncPulse20msSeconds = 0.020;
		double syncPulse5msMinSeconds = syncPulse5msSeconds / 2;
		double syncPulse5msMaxSeconds = (syncPulse5msSeconds + syncPulse9msSeconds) / 2;
		double syncPulse9msMaxSeconds = (syncPulse9msSeconds + syncPulse20msSeconds) / 2;
		double syncPulse20msMaxSeconds = syncPulse20msSeconds + syncPulse5msSeconds;
		syncPulse5msMinSamples = (int) Math.round(syncPulse5msMinSeconds * sampleRate);
		syncPulse5msMaxSamples = (int) Math.round(syncPulse5msMaxSeconds * sampleRate);
		syncPulse9msMaxSamples = (int) Math.round(syncPulse9msMaxSeconds * sampleRate);
		syncPulse20msMaxSamples = (int) Math.round(syncPulse20msMaxSeconds * sampleRate);
		double syncPulseFilterSeconds = syncPulse5msSeconds / 2;
		int syncPulseFilterSamples = (int) Math.round(syncPulseFilterSeconds * sampleRate) | 1;
		syncPulseFilterDelay = (syncPulseFilterSamples - 1) / 2;
		syncPulseFilter = new SimpleMovingAverage(syncPulseFilterSamples);
		syncPulseValueDelay = new Delay(syncPulseFilterSamples);
		float lowestFrequency = 1000;
		float highestFrequency = 2800;
		float cutoffFrequency = (highestFrequency - lowestFrequency) / 2;
		double baseBandLowPassSeconds = 0.002;
		int baseBandLowPassSamples = (int) Math.round(baseBandLowPassSeconds * sampleRate) | 1;
		baseBandLowPass = new ComplexConvolution(baseBandLowPassSamples);
		Kaiser kaiser = new Kaiser();
		for (int i = 0; i < baseBandLowPass.length; ++i)
			baseBandLowPass.taps[i] = (float) (kaiser.window(2.0, i, baseBandLowPass.length) * Filter.lowPass(cutoffFrequency, sampleRate, i, baseBandLowPass.length));
		float centerFrequency = (lowestFrequency + highestFrequency) / 2;
		baseBandOscillator = new Phasor(-centerFrequency, sampleRate);
		float syncPulseFrequency = 1200;
		syncPulseFrequencyValue = (syncPulseFrequency - centerFrequency) * 2 / scanLineBandwidth;
		syncPulseFrequencyTolerance = 50 * 2 / scanLineBandwidth;
		float syncPorchFrequency = 1500;
		float syncHighFrequency = (syncPulseFrequency + syncPorchFrequency) / 2;
		float syncLowFrequency = (syncPulseFrequency + syncHighFrequency) / 2;
		float syncLowValue = (syncLowFrequency - centerFrequency) * 2 / scanLineBandwidth;
		float syncHighValue = (syncHighFrequency - centerFrequency) * 2 / scanLineBandwidth;
		syncPulseTrigger = new SchmittTrigger(syncLowValue, syncHighValue);
		baseBand = new Complex();
	}

	public boolean process(float[] buffer) {
		boolean syncPulseDetected = false;
		for (int i = 0; i < buffer.length; ++i) {
			baseBand = baseBandLowPass.push(baseBand.set(buffer[i]).mul(baseBandOscillator.rotate()));
			float frequencyValue = frequencyModulation.demod(baseBand);
			float syncPulseValue = syncPulseFilter.avg(frequencyValue);
			float syncPulseDelayedValue = syncPulseValueDelay.push(syncPulseValue);
			buffer[i] = frequencyValue;
			if (!syncPulseTrigger.latch(syncPulseValue)) {
				++syncPulseCounter;
			} else if (syncPulseCounter < syncPulse5msMinSamples || syncPulseCounter > syncPulse20msMaxSamples || Math.abs(syncPulseDelayedValue - syncPulseFrequencyValue) > syncPulseFrequencyTolerance) {
				syncPulseCounter = 0;
			} else {
				if (syncPulseCounter < syncPulse5msMaxSamples)
					syncPulseWidth = SyncPulseWidth.FiveMilliSeconds;
				else if (syncPulseCounter < syncPulse9msMaxSamples)
					syncPulseWidth = SyncPulseWidth.NineMilliSeconds;
				else
					syncPulseWidth = SyncPulseWidth.TwentyMilliSeconds;
				syncPulseOffset = i - syncPulseFilterDelay;
				frequencyOffset = syncPulseDelayedValue - syncPulseFrequencyValue;
				syncPulseDetected = true;
				syncPulseCounter = 0;
			}
		}
		return syncPulseDetected;
	}
}
