package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * The globe's surface, unwrapped round the viewer's own spot (Aaron, 2026-11-04: the map stretched near the edges).
 * A map drawn on a ball the usual way (longitude and latitude) squeezes everything near the poles. Here the ball is a
 * grid of rings round the point straight below the viewer, and each point shows the map spot that far away in that
 * direction on the flat world (an azimuthal equidistant map). So the ground below and round you looks exactly as it is,
 * wherever you are, and the stretching goes to the far side of the ball, which you hardly see. The map loops (repeat
 * wrapping), like the world.
 *
 * <p>The shape never changes; only the texture coordinates move with you, and they are only rewritten when you have
 * moved more than a fraction of a map pixel, so a drifting viewer costs nothing most frames.
 */
final class GlobeSurface {
    private final int rings;
    private final int segments;
    /** Unit-ball position of each grid point (pole = +y). */
    private final float[] pos;
    /** How far each grid point is from the pole along the ground, as (east, south) in radians of arc. */
    private final float[] arc;
    private final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
    private double lastU = Double.NaN;
    private double lastV;
    private double lastRu;
    private double lastRv;

    GlobeSurface(int segments) {
        this.segments = segments;
        this.rings = segments / 2;
        int n = (rings + 1) * (segments + 1);
        pos = new float[n * 3];
        arc = new float[n * 2];
        for (int i = 0; i <= rings; i++) {
            // rings bunch up under the viewer, where the ground is seen closest
            double theta = Math.PI * Math.pow(i / (double) rings, 1.5);
            for (int j = 0; j <= segments; j++) {
                double phi = j / (double) segments * 2 * Math.PI;
                int k = i * (segments + 1) + j;
                pos[k * 3] = (float) (Math.sin(theta) * Math.sin(phi));
                pos[k * 3 + 1] = (float) Math.cos(theta);
                pos[k * 3 + 2] = (float) (Math.sin(theta) * Math.cos(phi));
                arc[k * 2] = (float) (theta * Math.sin(phi));
                arc[k * 2 + 1] = (float) (theta * Math.cos(phi));
            }
        }
    }

    /**
     * Draws the ball. {@code u}, {@code v}: the map coordinates (0..1, may run past) of the spot below; {@code ru},
     * {@code rv}: the ball's radius as a share of the map's width and height; {@code texel}: one map pixel in u.
     */
    void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, double u, double v, double ru,
            double rv, double texel) {
        double eps = texel * 0.25;
        if (Double.isNaN(lastU) || Math.abs(u - lastU) > eps || Math.abs(v - lastV) > eps || ru != lastRu
                || rv != lastRv) {
            rebuild(u, v, ru, rv);
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    private void rebuild(double u, double v, double ru, double rv) {
        // keep the numbers small so float texture coordinates stay sharp; the texture repeats anyway
        u -= Math.floor(u);
        v -= Math.floor(v);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                // counter-clockwise seen from outside
                vertex(buf, i, j, u, v, ru, rv);
                vertex(buf, i + 1, j, u, v, ru, rv);
                vertex(buf, i + 1, j + 1, u, v, ru, rv);
                vertex(buf, i, j + 1, u, v, ru, rv);
            }
        }
        buffer.bind();
        buffer.upload(buf.buildOrThrow());
        VertexBuffer.unbind();
        lastU = u;
        lastV = v;
        lastRu = ru;
        lastRv = rv;
    }

    private void vertex(BufferBuilder buf, int i, int j, double u, double v, double ru, double rv) {
        int k = i * (segments + 1) + j;
        buf.addVertex(pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2])
                .setUv((float) (u + arc[k * 2] * ru), (float) (v + arc[k * 2 + 1] * rv))
                .setColor(255, 255, 255, 255);
    }

    void close() {
        buffer.close();
    }
}
