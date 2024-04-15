/*
Complex Moving Average

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class ComplexMovingAverage extends ComplexMovingSum {
	public ComplexMovingAverage(int length) {
		super(length);
	}

	public Complex avg() {
		return sum().div(length);
	}

	public Complex avg(Complex input) {
		return sum(input).div(length);
	}
}
