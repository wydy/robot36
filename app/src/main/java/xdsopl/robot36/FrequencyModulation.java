/*
Frequency Modulation

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class FrequencyModulation {
	private float prev;
	private final float scale;
	FrequencyModulation(float bandwidth, float sampleRate) {
		scale = sampleRate / (bandwidth * (float) Math.PI);
	}
	float demod(Complex input) {
		float phase = input.arg();
		float delta = (phase - prev) % (float) Math.PI;
		prev = phase;
		return scale * delta;
	}
}
