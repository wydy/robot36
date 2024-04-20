/*
Robot 72 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_72_Color implements Mode {
	private final int scanLineSamples;
	private final int luminanceSamples;
	private final int chrominanceSamples;
	private final int beginSamples;
	private final int yBeginSamples;
	private final int vBeginSamples;
	private final int uBeginSamples;
	private final int endSamples;

	Robot_72_Color(int sampleRate) {
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.138;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.069;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + 2 * (separatorSeconds + porchSeconds + chrominanceSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		luminanceSamples = (int) Math.round(luminanceSeconds * sampleRate);
		chrominanceSamples = (int) Math.round(chrominanceSeconds * sampleRate);
		double yBeginSeconds = syncPulseSeconds / 2 + syncPorchSeconds;
		yBeginSamples = (int) Math.round(yBeginSeconds * sampleRate);
		beginSamples = yBeginSamples;
		double yEndSeconds = yBeginSeconds + luminanceSeconds;
		double vBeginSeconds = yEndSeconds + separatorSeconds + porchSeconds;
		vBeginSamples = (int) Math.round(vBeginSeconds * sampleRate);
		double vEndSeconds = vBeginSeconds + chrominanceSeconds;
		double uBeginSeconds = vEndSeconds + separatorSeconds + porchSeconds;
		uBeginSamples = (int) Math.round(uBeginSeconds * sampleRate);
		double uEndSeconds = uBeginSeconds + chrominanceSeconds;
		endSamples = (int) Math.round(uEndSeconds * sampleRate);
	}

	@Override
	public String getName() {
		return "Robot 72 Color";
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public int decodeScanLine(int[] evenBuffer, int[] oddBuffer, float[] scanLineBuffer, int prevPulseIndex, int scanLineSamples) {
		if (prevPulseIndex + beginSamples < 0 || prevPulseIndex + endSamples > scanLineBuffer.length)
			return 0;
		for (int i = 0; i < evenBuffer.length; ++i) {
			int yPos = yBeginSamples + (i * luminanceSamples) / evenBuffer.length + prevPulseIndex;
			int uPos = uBeginSamples + (i * chrominanceSamples) / evenBuffer.length + prevPulseIndex;
			int vPos = vBeginSamples + (i * chrominanceSamples) / evenBuffer.length + prevPulseIndex;
			evenBuffer[i] = ColorConverter.YUV2RGB(scanLineBuffer[yPos], scanLineBuffer[uPos], scanLineBuffer[vPos]);
		}
		return 1;
	}
}
