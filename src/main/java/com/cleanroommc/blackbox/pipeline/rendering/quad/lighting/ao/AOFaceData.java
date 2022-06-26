package com.cleanroommc.blackbox.pipeline.rendering.quad.lighting.ao;

import com.cleanroommc.blackbox.pipeline.rendering.quad.lighting.LightDataAccess;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class AOFaceData {

    /**
     * The light data has been retrieved from the cache.
     */
    public static final int HAS_LIGHT_DATA = 0b01;

    /**
     * The light data has been unpacked into normalized floating point values.
     */
    public static final int HAS_UNPACKED_LIGHT_DATA = 0b10;

    private static int minNonZero(int a, int b) {
        if (a == 0) {
            return b;
        } else if (b == 0) {
            return a;
        }
        return  Math.min(a, b);
    }

    public final int[] lm = new int[4];
    public final float[] ao = new float[4];
    public final float[] bl = new float[4];
    public final float[] sl = new float[4];

    private int flags;

    public void initLightData(LightDataAccess cache, BlockPos pos, EnumFacing direction, boolean offset) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        final int adjX;
        final int adjY;
        final int adjZ;

        if (offset) {
            adjX = x + direction.getXOffset();
            adjY = y + direction.getYOffset();
            adjZ = z + direction.getZOffset();
        } else {
            adjX = x;
            adjY = y;
            adjZ = z;
        }

        long adjWord = cache.get(adjX, adjY, adjZ);

        final int calm;

        // Use the origin block's light values if the adjacent one is opaque
        if (offset && LightDataAccess.unpackFO(adjWord)) {
            calm = LightDataAccess.unpackLM(cache.get(x, y, z));
        } else {
            calm = LightDataAccess.unpackLM(adjWord);
        }

        final float caao = LightDataAccess.unpackAO(adjWord);

        EnumFacing[] faces = AONeighbourInfo.get(direction).faces;

        final long e0 = cache.get(adjX, adjY, adjZ, faces[0]);
        final int e0lm = LightDataAccess.unpackLM(e0);
        final float e0ao = LightDataAccess.unpackAO(e0);
        final boolean e0op = LightDataAccess.unpackOP(e0);

        final long e1 = cache.get(adjX, adjY, adjZ, faces[1]);
        final int e1lm = LightDataAccess.unpackLM(e1);
        final float e1ao = LightDataAccess.unpackAO(e1);
        final boolean e1op = LightDataAccess.unpackOP(e1);

        final long e2 = cache.get(adjX, adjY, adjZ, faces[2]);
        final int e2lm = LightDataAccess.unpackLM(e2);
        final float e2ao = LightDataAccess.unpackAO(e2);
        final boolean e2op = LightDataAccess.unpackOP(e2);

        final long e3 = cache.get(adjX, adjY, adjZ, faces[3]);
        final int e3lm = LightDataAccess.unpackLM(e3);
        final float e3ao = LightDataAccess.unpackAO(e3);
        final boolean e3op = LightDataAccess.unpackOP(e3);

        // If neither edge of a corner is occluded, then use the light
        final int c0lm;
        final float c0ao;

        if (!e2op && !e0op) {
            c0lm = e0lm;
            c0ao = e0ao;
        } else {
            long d0 = cache.get(adjX, adjY, adjZ, faces[0], faces[2]);
            c0lm = LightDataAccess.unpackLM(d0);
            c0ao = LightDataAccess.unpackAO(d0);
        }

        final int c1lm;
        final float c1ao;

        if (!e3op && !e0op) {
            c1lm = e0lm;
            c1ao = e0ao;
        } else {
            long d1 = cache.get(adjX, adjY, adjZ, faces[0], faces[3]);
            c1lm = LightDataAccess.unpackLM(d1);
            c1ao = LightDataAccess.unpackAO(d1);
        }

        final int c2lm;
        final float c2ao;

        if (!e2op && !e1op) {
            // FIX: Use e1 instead of c0 to fix lighting errors in some directions
            c2lm = e1lm;
            c2ao = e1ao;
        } else {
            long d2 = cache.get(adjX, adjY, adjZ, faces[1], faces[2]);
            c2lm = LightDataAccess.unpackLM(d2);
            c2ao = LightDataAccess.unpackAO(d2);
        }

        final int c3lm;
        final float c3ao;

        if (!e3op && !e1op) {
            // FIX: Use e1 instead of c0 to fix lighting errors in some directions
            c3lm = e1lm;
            c3ao = e1ao;
        } else {
            long d3 = cache.get(adjX, adjY, adjZ, faces[1], faces[3]);
            c3lm = LightDataAccess.unpackLM(d3);
            c3ao = LightDataAccess.unpackAO(d3);
        }

        float[] ao = this.ao;
        ao[0] = (e3ao + e0ao + c1ao + caao) * 0.25f;
        ao[1] = (e2ao + e0ao + c0ao + caao) * 0.25f;
        ao[2] = (e2ao + e1ao + c2ao + caao) * 0.25f;
        ao[3] = (e3ao + e1ao + c3ao + caao) * 0.25f;

        int[] cb = this.lm;
        cb[0] = calculateCornerBrightness(e3lm, e0lm, c1lm, calm);
        cb[1] = calculateCornerBrightness(e2lm, e0lm, c0lm, calm);
        cb[2] = calculateCornerBrightness(e2lm, e1lm, c2lm, calm);
        cb[3] = calculateCornerBrightness(e3lm, e1lm, c3lm, calm);

        this.flags |= HAS_LIGHT_DATA;
    }

    public void unpackLightData() {
        int[] lm = this.lm;

        float[] bl = this.bl;
        float[] sl = this.sl;

        bl[0] = unpackBlockLight(lm[0]);
        bl[1] = unpackBlockLight(lm[1]);
        bl[2] = unpackBlockLight(lm[2]);
        bl[3] = unpackBlockLight(lm[3]);

        sl[0] = unpackSkyLight(lm[0]);
        sl[1] = unpackSkyLight(lm[1]);
        sl[2] = unpackSkyLight(lm[2]);
        sl[3] = unpackSkyLight(lm[3]);

        this.flags |= HAS_UNPACKED_LIGHT_DATA;
    }

    public float getBlendedSkyLight(float[] w) {
        return weightedSum(this.sl, w);
    }

    public float getBlendedBlockLight(float[] w) {
        return weightedSum(this.bl, w);
    }

    public float getBlendedShade(float[] w) {
        return weightedSum(this.ao, w);
    }

    private static float weightedSum(float[] v, float[] w) {
        float t0 = v[0] * w[0];
        float t1 = v[1] * w[1];
        float t2 = v[2] * w[2];
        float t3 = v[3] * w[3];

        return t0 + t1 + t2 + t3;
    }

    private static float unpackSkyLight(int i) {
        return (i >> 16) & 0xFF;
    }

    private static float unpackBlockLight(int i) {
        return i & 0xFF;
    }

    private static int calculateCornerBrightness(int a, int b, int c, int d) {
        // FIX: Normalize corner vectors correctly to the minimum non-zero value between each one to prevent
        // strange issues
        if ((a == 0) || (b == 0) || (c == 0) || (d == 0)) {
            // Find the minimum value between all corners
            final int min = minNonZero(minNonZero(a, b), minNonZero(c, d));

            // Normalize the corner values
            a = Math.max(a, min);
            b = Math.max(b, min);
            c = Math.max(c, min);
            d = Math.max(d, min);
        }

        return ((a + b + c + d) >> 2) & 0xFF00FF;
    }

    public boolean hasLightData() {
        return (this.flags & HAS_LIGHT_DATA) != 0;
    }

    public boolean hasUnpackedLightData() {
        return (this.flags & HAS_UNPACKED_LIGHT_DATA) != 0;
    }

    public void reset() {
        this.flags = 0;
    }

}
