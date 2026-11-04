package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * The globe's meshes, built once and kept on the graphics card: a textured unit sphere (u = longitude, v = latitude,
 * north up) at a few levels of detail, and a shading sphere whose dark half is baked around its -y pole (it is turned
 * so +y points at the sun, so day and night move without rebuilding anything).
 */
final class GlobeMesh {
    /** Longitude steps per level of detail; latitude has half as many. */
    static final int[] DETAIL = {16, 32, 64, 128, 256};
    private static final Map<Integer, VertexBuffer> SURFACE = new HashMap<>();
    private static VertexBuffer shade;

    private GlobeMesh() {}

    static VertexBuffer surface(int detail) {
        return SURFACE.computeIfAbsent(detail, GlobeMesh::buildSurface);
    }

    static VertexBuffer shade() {
        if (shade == null) {
            shade = buildShade(64);
        }
        return shade;
    }

    static void clear() {
        SURFACE.values().forEach(VertexBuffer::close);
        SURFACE.clear();
        if (shade != null) {
            shade.close();
            shade = null;
        }
    }

    private static VertexBuffer buildSurface(int lonSteps) {
        int latSteps = lonSteps / 2;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < lonSteps; i++) {
            for (int j = 0; j < latSteps; j++) {
                // counter-clockwise seen from outside
                surfaceVertex(buf, i, j, lonSteps, latSteps);
                surfaceVertex(buf, i, j + 1, lonSteps, latSteps);
                surfaceVertex(buf, i + 1, j + 1, lonSteps, latSteps);
                surfaceVertex(buf, i + 1, j, lonSteps, latSteps);
            }
        }
        return upload(buf);
    }

    private static void surfaceVertex(BufferBuilder buf, int i, int j, int lonSteps, int latSteps) {
        float u = i / (float) lonSteps;
        float v = j / (float) latSteps;
        double lon = u * 2 * Math.PI - Math.PI;
        double lat = Math.PI / 2 - v * Math.PI;
        float x = (float) (Math.cos(lat) * Math.sin(lon));
        float y = (float) Math.sin(lat);
        float z = (float) (Math.cos(lat) * Math.cos(lon));
        buf.addVertex(x, y, z).setUv(u, v).setColor(255, 255, 255, 255);
    }

    private static VertexBuffer buildShade(int lonSteps) {
        int latSteps = lonSteps / 2;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < lonSteps; i++) {
            for (int j = 0; j < latSteps; j++) {
                shadeVertex(buf, i, j, lonSteps, latSteps);
                shadeVertex(buf, i, j + 1, lonSteps, latSteps);
                shadeVertex(buf, i + 1, j + 1, lonSteps, latSteps);
                shadeVertex(buf, i + 1, j, lonSteps, latSteps);
            }
        }
        return upload(buf);
    }

    private static void shadeVertex(BufferBuilder buf, int i, int j, int lonSteps, int latSteps) {
        double lon = i / (double) lonSteps * 2 * Math.PI - Math.PI;
        double lat = Math.PI / 2 - j / (double) latSteps * Math.PI;
        float x = (float) (Math.cos(lat) * Math.sin(lon));
        float y = (float) Math.sin(lat);
        float z = (float) (Math.cos(lat) * Math.cos(lon));
        // y = how much this point faces the sun: lit above ~0.1, a soft dusk band, dark night side
        double t = Math.max(0, Math.min(1, (0.12 - y) / 0.3));
        int alpha = (int) Math.round(205 * t);
        buf.addVertex(x * 1.0005f, y * 1.0005f, z * 1.0005f).setColor(4, 6, 18, alpha);
    }

    private static VertexBuffer upload(BufferBuilder buf) {
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(buf.buildOrThrow());
        VertexBuffer.unbind();
        return vb;
    }
}
