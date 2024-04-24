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

	public void reset() {
		prev = 0;
	}
}
