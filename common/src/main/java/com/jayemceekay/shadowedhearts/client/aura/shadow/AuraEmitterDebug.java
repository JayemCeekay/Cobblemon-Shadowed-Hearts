package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.AnchorSource;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.BoneAnchor;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.List;

/** Full-resolution diagnostic overlay for the sites used by the active aura emitter. */
final class AuraEmitterDebug {
    enum Kind {
        SURFACE_TOP(0x00EBFF), SURFACE_SIDE(0xFFB805), SURFACE_UNDERSIDE(0xFF62C9),
        EDGE(0xA4FF35), FALLBACK(0xBBC5D1),
        COLOSSEUM_SMOKE(0x9852F5), COLOSSEUM_INFILL(0x00B68A), MOTE(0xFFFFFF),
        XD_CELL(0x489AFF), XD_HAZE(0xD1ABFF), XD_FLAKE(0xFF548A);

        final int argb;
        Kind(int rgb) { argb = 0xFF000000 | rgb; }
    }

    static final int DIAMETER_PIXELS = 4;
    record Dot(Vec3 point, Kind kind) {}
    record WorldFrame(long token, Vec3 camera, Matrix4f transform, List<Dot> dots) {}

    // Volume and mote emitters sample a new position for each birth. Keep only their last
    // three simulation ticks, scoped to the source so a recall removes its markers immediately.
    static final class Births {
        private static final int MAX_BIRTHS = 256;
        private record Birth(long tick, Dot dot) {}
        private final ArrayDeque<Birth> recent = new ArrayDeque<>();

        void add(long tick, Vec3 position, Kind kind) {
            expire(tick);
            // The ParticleEngine can still create motes when a render hook skips the aura clock.
            if (recent.size() == MAX_BIRTHS) recent.removeFirst();
            recent.addLast(new Birth(tick, new Dot(position, kind)));
        }

        void append(long tick, List<Dot> dots) {
            expire(tick);
            for (Birth birth : recent) dots.add(birth.dot);
        }

        private void expire(long tick) {
            while (!recent.isEmpty() && tick - recent.getFirst().tick >= 3) recent.removeFirst();
        }
    }

    private AuraEmitterDebug() {}

    static Dot surface(BoneAnchor site) {
        return surface(site, site.pos(), site.normal().y);
    }

    static Dot surface(BoneAnchor site, Vec3 position, double normalY) {
        Kind kind;
        if (site.source() == AnchorSource.SHELL) kind = Kind.FALLBACK;
        else if (site.source() == AnchorSource.EDGE) kind = Kind.EDGE;
        else if (normalY >= .5) kind = Kind.SURFACE_TOP;
        else if (normalY <= -.5) kind = Kind.SURFACE_UNDERSIDE;
        else kind = Kind.SURFACE_SIDE;
        return new Dot(position, kind);
    }

    static Matrix4f currentTransform() {
        return new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
    }

    static void draw(List<Dot> dots, Matrix4f transform, Vec3 camera,
                     float minX, float minY, float maxX, float maxY) {
        if (dots.isEmpty() || GameRenderer.getPositionColorShader() == null) return;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (viewport[2] <= 0 || viewport[3] <= 0) return;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.colorMask(true, true, true, true);
            BufferBuilder vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Vector4f clip = new Vector4f();
            for (Dot dot : dots) {
                Vec3 p = dot.point;
                // Keep subtraction in double precision for models far from the world origin.
                transform.transform(clip.set((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z), 1));
                appendDot(vertices, clip, viewport[2], viewport[3], minX, minY, maxX, maxY, dot.kind);
            }
            MeshData mesh = vertices.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
    }

    static boolean appendDot(VertexConsumer vertices, Vector4f clip, int width, int height,
                             float minX, float minY, float maxX, float maxY, Kind kind) {
        if (width <= 0 || height <= 0 || !clip.isFinite() || clip.w <= 0 || clip.z < -clip.w || clip.z > clip.w) return false;
        float x = clip.x / clip.w, y = clip.y / clip.w;
        float radiusX = (float) DIAMETER_PIXELS / width, radiusY = (float) DIAMETER_PIXELS / height;
        float left = Math.max(minX, x - radiusX), right = Math.min(maxX, x + radiusX);
        float bottom = Math.max(minY, y - radiusY), top = Math.min(maxY, y + radiusY);
        if (right <= left || top <= bottom) return false;
        vertices.addVertex(left, bottom, 0).setColor(kind.argb);
        vertices.addVertex(right, bottom, 0).setColor(kind.argb);
        vertices.addVertex(right, top, 0).setColor(kind.argb);
        vertices.addVertex(left, top, 0).setColor(kind.argb);
        return true;
    }
}
