/*
Color converter

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public final class ColorConverter {

	private static int clamp(int value) {
		return Math.min(Math.max(value, 0), 255);
	}

	public static int YUV2RGB(int Y, int U, int V) {
		Y -= 16;
		U -= 128;
		V -= 128;
		int R = clamp((298 * Y + 409 * V + 128) >> 8);
		int G = clamp((298 * Y - 100 * U - 208 * V + 128) >> 8);
		int B = clamp((298 * Y + 516 * U + 128) >> 8);
		return 0xff000000 | (R << 16) | (G << 8) | B;
	}
}
