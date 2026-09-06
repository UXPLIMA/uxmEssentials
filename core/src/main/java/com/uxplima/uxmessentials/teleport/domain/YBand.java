package com.uxplima.uxmessentials.teleport.domain;

/**
 * The vertical band a random-teleport candidate's landing Y must fall within. Operators clamp RTP to a
 * sensible altitude range (above bedrock caverns, below the build limit) so a candidate that resolves
 * to a deep cave or a sky island is rejected before it reaches the queue.
 *
 * <p>An {@link #unbounded()} band accepts every finite Y, which is the default when neither
 * {@code rtp.min-y} nor {@code rtp.max-y} is set.
 *
 * @param minY the lowest acceptable landing Y, inclusive
 * @param maxY the highest acceptable landing Y, inclusive
 */
public record YBand(int minY, int maxY) {

    public YBand {
        if (maxY < minY) {
            throw new IllegalArgumentException("maxY must be >= minY: [" + minY + ", " + maxY + "]");
        }
    }

    /** A band that accepts any finite Y. */
    public static YBand unbounded() {
        return new YBand(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** True when {@code y} falls within {@code [minY, maxY]}. */
    public boolean contains(double y) {
        return y >= minY && y <= maxY;
    }
}
