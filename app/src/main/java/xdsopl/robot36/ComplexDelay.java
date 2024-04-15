/*
Complex digital delay line

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class ComplexDelay {
	public final int length;
	private final Delay real;
	private final Delay imag;
	private final Complex temp;

	ComplexDelay(int length) {
		this.length = length;
		this.real = new Delay(length);
		this.imag = new Delay(length);
		this.temp = new Complex();
	}

	Complex push(Complex input) {
		temp.real = real.push(input.real);
		temp.imag = imag.push(input.imag);
		return temp;
	}
}
