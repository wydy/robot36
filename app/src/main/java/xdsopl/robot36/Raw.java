/*
Raw mode

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Raw implements Mode {

	Raw() {
	}

	@Override
	public String getName() {
		return "Raw";
	}

	@Override
	public int getScanLineSamples() {
		return -1;
	}
}
