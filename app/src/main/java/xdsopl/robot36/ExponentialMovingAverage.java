/*
Exponential Moving Average

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class ExponentialMovingAverage {
	private float alpha;
	private float prev;

	ExponentialMovingAverage() {
		this.alpha = 1;
	}

	public float avg(float input) {
		return prev = prev * (1 - alpha) + alpha * input;
	}

	public void alpha(float alpha) {
		this.alpha = alpha;
	}

	public void alpha(float alpha, int order) {
		alpha((float) Math.pow(alpha, 1.0 / order));
	}

	public void cutoff(float freq, float rate, int order) {
		double x = Math.cos(2 * Math.PI * freq / rate);
		alpha((float) (x-1+Math.sqrt(x*(x-4)+3)), order);
	}

	public void cutoff(float freq, float rate) {
		cutoff(freq, rate, 1);
	}

	public void reset() {
		prev = 0;
	}
}
