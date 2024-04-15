/*
Complex Moving Sum

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class ComplexMovingSum {
	public final int length;
	private final SimpleMovingSum real;
	private final SimpleMovingSum imag;
	private final Complex temp;

	ComplexMovingSum(int length) {
		this.length = length;
		this.real = new SimpleMovingSum(length);
		this.imag = new SimpleMovingSum(length);
		this.temp = new Complex();
	}

	void add(Complex input) {
		real.add(input.real);
		imag.add(input.imag);
	}

	Complex sum() {
		temp.real = real.sum();
		temp.imag = imag.sum();
		return temp;
	}

	Complex sum(Complex input) {
		temp.real = real.sum(input.real);
		temp.imag = imag.sum(input.imag);
		return temp;
	}
}
