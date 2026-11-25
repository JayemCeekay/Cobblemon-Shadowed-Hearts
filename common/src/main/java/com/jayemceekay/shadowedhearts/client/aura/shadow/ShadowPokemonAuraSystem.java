package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfiles;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleResolver;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.classic.ClassicAuraMorphology;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.classic.ClassicAuraWisp;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum.ColosseumAuraVolume;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum.ColosseumSmokeSpawnSequence;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum.ColosseumSmokeAppearance;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraFade;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraSpawnVolume;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraSurfaceSampler;

import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisHandler;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.client.ClientEffects;
import com.jayemceekay.shadowedhearts.client.aura.shadow.AuraEmitterDebug.Kind;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ClassicAuraRegions.ClassicPartGeometry;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ClassicAuraRegions.ClassicRegion;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.BoneAnchor;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.AnchorSource;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.RenderedPartSample;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.AnchorRole;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPixelationScale;
import com.jayemceekay.shadowedhearts.config.AuraPixelationStyle;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

import static com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPerformanceBudget.MAX_PUFFS;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPerformanceBudget.adaptiveEmitterScale;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPerformanceBudget.boneEmissionInterval;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPerformanceBudget.puffRenderQuality;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPixelationScale.maximumWorldPixelLod;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.AuraPixelationScale.stabilizedWorldPixelLod;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ClassicAuraRegions.describeClassicPart;

import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.collectCubeAnchors;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.collectRenderedBoneChainAnchors;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.downsampleBoneAnchors;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.fallbackBoneAnchors;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.renderedPartSample;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.shiftedBoneAnchors;

import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.MAX_BONE_ANCHORS;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.boneAnchorBudget;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.cubeTemplate;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.estimateModelGeometry;

/**
 * Penumbra-derived FBO aura for shadow Pokemon.
 *
 * <p>The base aura is driven by large density puffs emitted from Cobblemon's
 * posed model surfaces. Before a pose is available it samples a hitbox shell.
 */
public final class ShadowPokemonAuraSystem {

    private static final ResourceLocation DENSITY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/particle/shadow_pokemon_aura.png"
    );

    private static final int FULLBRIGHT = 0x00F000F0;
    private static final int BONE_ANCHOR_CANDIDATE_MULTIPLIER = 4;
    private static final float BODY_CUBE_ANCHOR_BUDGET_FRACTION = 0.58f;
    private static final float MIN_MASK_RENDER_QUALITY = 0.30f;
    private static final float MIN_PIXEL_MODEL_SIZE = 0.05f;
    private static final float SIGNATURE_SURFACE_RELEASE = 0.55f;
    private static final float SIGNATURE_WAKE_RELEASE = 0.36f;
    // Must match the outer smoothstep thresholds in the XD filament shader.
    // These let model-space profile widths choose a billboard large enough for
    // both the bright core and its violet halo.
    private static final float XD_FILAMENT_CORE_UV_HALF_WIDTH = 0.028f;
    private static final float XD_FILAMENT_HALO_UV_HALF_WIDTH = 0.105f;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final MultiBufferSource.BufferSource MASK_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private static final List<Puff> ACTIVE = new ArrayList<>();
    private static final Map<Integer, SourceState> SOURCES = new ConcurrentHashMap<>();
    private static final ParticleRuntime WORLD_PARTICLES = new ParticleRuntime(
            RANDOM,
            ACTIVE,
            ShadowPokemonAuraSystem::adaptivePuffLimit
    );

    private static long lastTickGameTime = Long.MIN_VALUE;
    private static long lastRenderFrameToken = Long.MIN_VALUE;
    private static long lastMaskFrameToken = Long.MIN_VALUE;
    private static final Map<ShadowAuraStyle, Float> WORLD_PIXEL_LODS_THIS_FRAME =
            initialWorldPixelLods();
    private static boolean worldCompositePending;
    private static ColosseumMoteFrame pendingColosseumMoteFrame;
    private static AuraEmitterDebug.WorldFrame pendingEmitterMarkers;
    private static final Set<ShadowAuraRenderBatch> WORLD_PENDING_BATCHES = new LinkedHashSet<>();
    private record WorldPixelGrid(AuraPixelationStyle style, double normalizedSize) { }
    private static final Map<ShadowAuraStyle, WorldPixelGrid> WORLD_PIXEL_GRIDS =
            new EnumMap<>(ShadowAuraStyle.class);
    private static final Set<ShadowAuraRenderBatch> MASK_CAPTURED_BATCHES = new LinkedHashSet<>();
    private static RenderedAnchorCapture activeRenderedAnchorCapture;
    private static boolean maskCapturedThisFrame;
    private static boolean renderingModelMask;

    private ShadowPokemonAuraSystem() {}

    public static void observe(PokemonEntity entity,
                               double x, double y, double z,
                               float radius, float height,
                               float fade, float corruption,
                               float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || fade <= 0.001f || corruption <= 0.01f) {
            return;
        }

        long gameTime = mc.level.getGameTime();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float quality = auraQualityFor(entity, cameraPos, x, y, z, gameTime);
        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        updateSourceStyle(
                state,
                ShadowAuraStyleResolver.resolve(entity));
        state.species = entity.getPokemon().getSpecies().getResourceIdentifier().toString();
        state.modeTint = ShadowAuraRenderBatch.hasModeTint(entity.getAspects());
        if (state.style == ShadowAuraStyle.COLOSSEUM) LuminousMoteEmitters.observe(entity);
        state.lastSeenTick = gameTime;
        state.fade = fade;
        state.corruption = corruption;
        state.quality = quality;
        state.performanceBudget = AuraPerformanceBudget.forMode(ClientEffects.auraPerformanceMode());
        state.pixelCenterX = x;
        state.pixelCenterY = y + height * 0.5;
        state.pixelCenterZ = z;
        state.pixelModelSize = Math.max(entity.getBbWidth(), height);
        Vec3 entityBase = new Vec3(x, y, z);
        if (state.style == ShadowAuraStyle.SIGNATURE) state.updateSignatureLocation(entityBase, gameTime);

        int emissionInterval = boneEmissionInterval(quality);
        if (state.lastEmitTick != Long.MIN_VALUE && gameTime - state.lastEmitTick < emissionInterval) {
            return;
        }
        state.lastEmitTick = gameTime;

        long poseGraceTicks = Math.max(2, emissionInterval + 1);
        if (state.lastBoneTick != Long.MIN_VALUE && gameTime - state.lastBoneTick <= poseGraceTicks) {
            return;
        }

        float safeRadius = Mth.clamp(radius, 0.22f, 4.0f);
        float safeHeight = Mth.clamp(height, 0.35f, 6.0f);
        float intensity = Mth.clamp(fade * (0.45f + corruption * 0.75f), 0.0f, 1.25f);

        if (state.style != ShadowAuraStyle.SIGNATURE) {
            state.classicGeometry.clear();
            state.xdSurfaceCubes.clear();
            emitModelAura(EmitterFrame.fromEntity(entity, partialTicks), state,
                    shiftedBoneAnchors(fallbackBoneAnchors(safeRadius * 2.0f, safeHeight), entityBase),
                    intensity, quality, WORLD_PARTICLES);
            if (state.style == ShadowAuraStyle.COLOSSEUM && state.colosseumVolume != null) {
                LuminousMoteEmitters.observe(entity, state.colosseumVolume.modelBounds());
            }
            return;
        }

        if (state.signatureFrame.isPosed()) state.startSignatureFrame(entityBase, gameTime);
        state.signatureLayout.clearFollowPose();
        emitSignatureSurface(EmitterFrame.fromEntity(entity, partialTicks), state,
                SignatureSurfaceLayout.fallback(safeRadius * 2, safeHeight, Vec3.ZERO),
                intensity, quality, WORLD_PARTICLES);
    }

    public static void beginRenderedModelAnchorCapture(PokemonEntity entity, PoseStack renderStack, Bone rootPart) {
        activeRenderedAnchorCapture = null;
        if (entity == null || renderStack == null || rootPart == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (isIrisShadowRenderActive()) {
            return;
        }

        float strength = ShadowAuraEmitters.getFboAuraMaskStrength(entity);
        if (strength <= 0.001f) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        long gameTime = mc.level.getGameTime();
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float quality = auraQualityFor(entity, cameraPos, x, y, z, gameTime);

        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        updateSourceStyle(
                state,
                ShadowAuraStyleResolver.resolve(entity));
        state.species = entity.getPokemon().getSpecies().getResourceIdentifier().toString();
        state.modeTint = ShadowAuraRenderBatch.hasModeTint(entity.getAspects());
        if (state.style == ShadowAuraStyle.COLOSSEUM) LuminousMoteEmitters.observe(entity);
        state.lastSeenTick = gameTime;
        if (state.style != ShadowAuraStyle.SIGNATURE) state.lastBoneTick = gameTime;
        state.fade = strength;
        state.corruption = 1.0f;
        state.quality = quality;
        state.pixelCenterX = x;
        state.pixelCenterY = y + entity.getBbHeight() * 0.5;
        state.pixelCenterZ = z;
        state.pixelModelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());

        Vec3 sourceWorld = new Vec3(x, y, z);
        if (state.style == ShadowAuraStyle.SIGNATURE) {
            Matrix4f root = renderStack.last().pose();
            if (!root.isFinite() || Math.abs(root.determinant3x3()) < 1.0e-12f) return;
            state.updateSignaturePose(sourceWorld, cameraPos, root, gameTime);
        }
        int emissionInterval = boneEmissionInterval(quality);
        if (state.lastBoneEmitTick != Long.MIN_VALUE && gameTime - state.lastBoneEmitTick < emissionInterval) {
            return;
        }

        // The world entity stack is camera-relative. Keep the mounted renderer's pivot
        // translation when it banks the model around the middle of the hitbox.
        Vec3 poseToWorldOffset = cameraPos;
        if (state.style != ShadowAuraStyle.SIGNATURE) {
            Matrix4f modelPose = renderStack.last().pose();
            poseToWorldOffset = sourceWorld.subtract(modelPose.m30(), modelPose.m31(), modelPose.m32());
        }
        int anchorBudget = state.style == ShadowAuraStyle.SIGNATURE ? 0
                : adaptiveAnchorBudget(boneAnchorBudget(estimateModelGeometry(rootPart, 0)), quality);
        int candidateBudget = Math.min(MAX_BONE_ANCHORS * BONE_ANCHOR_CANDIDATE_MULTIPLIER,
                Math.max(anchorBudget, anchorBudget * BONE_ANCHOR_CANDIDATE_MULTIPLIER));
        int bodyAnchorBudget = Math.min(anchorBudget - 24, Math.max(42, Math.round(anchorBudget * BODY_CUBE_ANCHOR_BUDGET_FRACTION)));

        activeRenderedAnchorCapture = new RenderedAnchorCapture(
                entity,
                state,
                poseToWorldOffset,
                sourceWorld,
                strength,
                partialTicks,
                quality,
                gameTime,
                anchorBudget,
                candidateBudget,
                bodyAnchorBudget
        );
    }

    public static void captureRenderedModelPart(ModelPart part, PoseStack stack) {
        RenderedAnchorCapture capture = activeRenderedAnchorCapture;
        if (capture == null || part == null || stack == null) {
            return;
        }
        if (capture.state.style == ShadowAuraStyle.SIGNATURE) {
            Matrix4f partPose = stack.last().pose();
            if (capture.signatureToLocal != null) {
                partPose = capture.signatureToLocal.mul(partPose, capture.signaturePartPose);
            }
            capture.signaturePose.add(part, partPose);
            return;
        }

        int depth = capture.renderedPartStack.size() + 1;
        RenderedPartSample sample = renderedPartSample(part, stack, capture.poseToWorldOffset);
        boolean xdHidden = capture.state.style == ShadowAuraStyle.XD_FAITHFUL && (!part.visible || part.skipDraw);
        if (xdHidden) sample = new RenderedPartSample(sample.geometryWorld(), false, sample.hasChainableDescendant());
        RenderedPartNode parent = capture.renderedPartStack.isEmpty()
                ? null
                : capture.renderedPartStack.get(capture.renderedPartStack.size() - 1);

        capture.classicGeometry.put(part, describeClassicPart(part, stack.last().pose(),
                capture.poseToWorldOffset, parent == null ? null : parent.part, xdHidden));
        if (capture.state.style == ShadowAuraStyle.XD_FAITHFUL) {
            capture.xdSurfaceParts.put(part, captureXdSurfaces(part, stack.last().pose(), capture.poseToWorldOffset));
        }

        if (sample.chainable() && parent != null && parent.chainable && capture.anchorCandidates.size() < capture.candidateBudget) {
            collectRenderedBoneChainAnchors(
                    part,
                    capture.state.classicAnchors,
                    parent.geometryWorld,
                    sample.geometryWorld(),
                    !sample.hasChainableDescendant(),
                    capture.anchorCandidates,
                    depth,
                    capture.candidateBudget
            );
        }

        int anchorsBefore = capture.anchorCandidates.size();
        if (!xdHidden) collectCubeAnchors(
                part,
                stack,
                capture.poseToWorldOffset,
                capture.anchorCandidates,
                depth,
                capture.candidateBudget,
                capture.bodyAnchorBudget,
                capture.bodyAnchorCount,
                true
        );

        if (sample.chainable()
                && capture.anchorCandidates.size() < capture.candidateBudget
                && (capture.anchorCandidates.size() == anchorsBefore || depth <= 2)) {
            capture.anchorCandidates.add(new BoneAnchor(sample.geometryWorld(), depth, 1.0f, AnchorRole.JOINT, AnchorSource.JOINT, Vec3.ZERO));
        }

        capture.renderedPartStack.add(new RenderedPartNode(part, sample.geometryWorld(), sample.chainable()));
    }

    public static void endRenderedModelPart(ModelPart part) {
        RenderedAnchorCapture capture = activeRenderedAnchorCapture;
        if (capture == null || part == null || capture.renderedPartStack.isEmpty()) {
            return;
        }

        int last = capture.renderedPartStack.size() - 1;
        if (capture.renderedPartStack.get(last).part == part) {
            capture.renderedPartStack.remove(last);
            return;
        }

        for (int i = last - 1; i >= 0; i--) {
            if (capture.renderedPartStack.get(i).part == part) {
                capture.renderedPartStack.subList(i, capture.renderedPartStack.size()).clear();
                return;
            }
        }
    }

    public static void endRenderedModelAnchorCapture(PokemonEntity entity) {
        RenderedAnchorCapture capture = activeRenderedAnchorCapture;
        activeRenderedAnchorCapture = null;
        if (capture == null || capture.entity != entity) {
            return;
        }

        List<BoneAnchor> anchors = capture.state.style == ShadowAuraStyle.SIGNATURE
                ? capture.state.signatureLayout.resolve(capture.signaturePose,
                        capture.signatureToLocal == null ? capture.poseToWorldOffset : Vec3.ZERO,
                        Math.max(entity.getBbWidth(), entity.getBbHeight()))
                : downsampleBoneAnchors(capture.anchorCandidates, capture.anchorBudget);
        if (anchors.isEmpty()) {
            if (capture.state.style == ShadowAuraStyle.SIGNATURE) capture.state.signatureAnchors.clear();
            return;
        }

        capture.state.lastBoneTick = capture.gameTime;
        EmitterFrame frame = EmitterFrame.fromEntity(capture.entity, capture.partialTicks);
        capture.state.classicGeometry.clear();
        capture.state.classicGeometry.putAll(capture.classicGeometry);
        replaceXdSurfaces(capture.state, capture.xdSurfaceParts);
        emitModelAura(frame, capture.state, anchors, capture.strength, capture.quality, WORLD_PARTICLES);
        if (capture.state.style == ShadowAuraStyle.COLOSSEUM && capture.state.colosseumVolume != null) {
            LuminousMoteEmitters.observe(capture.entity, capture.state.colosseumVolume.modelBounds());
        }
        capture.state.lastBoneEmitTick = capture.gameTime;
    }

    public static void onPokemonDespawn(int entityId) {
        SourceState removed = SOURCES.remove(entityId);
        if (removed != null) {
            pendingEmitterMarkers = null;
            removed.classicAnchors.clear();
            removed.signatureAnchors.clear();
            removed.signatureLayout.clearFollowPose();
            removed.signatureFrame = null;
        }
    }

    public static void clear() {
        pendingEmitterMarkers = null;
        synchronized (ACTIVE) {
            ACTIVE.clear();
            WORLD_PARTICLES.wisps.clear();
        }
        SOURCES.clear();
        lastTickGameTime = Long.MIN_VALUE;
        lastRenderFrameToken = Long.MIN_VALUE;
        lastMaskFrameToken = Long.MIN_VALUE;
        activeRenderedAnchorCapture = null;
        maskCapturedThisFrame = false;
        MASK_CAPTURED_BATCHES.clear();
        renderingModelMask = false;
        resetWorldPixelLods();
        WORLD_PIXEL_GRIDS.clear();
        worldCompositePending = false;
        WORLD_PENDING_BATCHES.clear();
        pendingColosseumMoteFrame = null;
        LuminousMoteEmitters.clearColosseumParticles();
    }

    /**
     * Preview-owned version of the active world puff simulation.
     *
     * <p>Each GUI owner keeps one instance. It deliberately owns an independent
     * RNG, source state, clock, and puff pool so opening a screen can neither
     * consume the world's random stream nor enter {@link #ACTIVE}/{@link #SOURCES}.</p>
     */
    public static final class PreviewInstance {
        private static final long STEP_NANOS = 50_000_000L;
        private static final long MAX_ELAPSED_NANOS = STEP_NANOS * 5L;
        // Cover the longest quality-1 broad/tip lifetime so the first visible
        // frame starts at the same settled density as a standing world source.
        private static final int PREWARM_TICKS = 96;
        private static final int MAX_PREVIEW_PUFFS = MAX_PUFFS;

        private String identity;
        private int generation;
        private ParticleRuntime particles;
        private SourceState sourceState;
        private ShadowAuraStyle style = ShadowAuraStyle.DEFAULT;
        private PreviewAnchorCapture activeCapture;
        private List<BoneAnchor> latestAnchors = List.of();
        private Matrix4f simulationToGui = new Matrix4f();
        private float width = 1.0f;
        private float height = 1.0f;
        private long lastNanos = Long.MIN_VALUE;
        private long accumulatedNanos;
        private int simulationTick;
        private boolean prewarmed;
        private boolean fallbackPose;
        private long lastPoseCaptureNanos = Long.MIN_VALUE;

        /** Binds this owner to one logical Pokemon and resets only on identity/form changes. */
        public int bind(RenderablePokemon pokemon, String logicalIdentity) {
            ShadowAuraStyle nextStyle = ShadowAuraStyleResolver.resolve(pokemon);
            String nextIdentity = previewIdentity(pokemon, logicalIdentity, nextStyle);
            if (!nextIdentity.equals(identity)) {
                reset(nextIdentity, nextStyle);
            }
            style = nextStyle;
            updateSourceStyle(sourceState, nextStyle);
            sourceState.modeTint = pokemon != null && ShadowAuraRenderBatch.hasModeTint(pokemon.getAspects());
            sourceState.species = pokemon == null ? "" : pokemon.getSpecies().getResourceIdentifier().toString();
            updateFormDimensions(pokemon);
            return generation;
        }

        public ShadowAuraStyle style() {
            return style;
        }

        boolean modeTint() {
            return sourceState != null && sourceState.modeTint;
        }

        List<AuraEmitterDebug.Dot> emitterMarkers() {
            List<AuraEmitterDebug.Dot> dots = new ArrayList<>();
            if (style == ShadowAuraStyle.SIGNATURE) {
                for (BoneAnchor anchor : latestAnchors) dots.add(AuraEmitterDebug.surface(anchor));
            }
            if (sourceState.debugBirths != null) sourceState.debugBirths.append(particles.tickSerial, dots);
            return dots;
        }

        public EnumSet<ShadowAuraStyle> activeStyles() {
            EnumSet<ShadowAuraStyle> styles = EnumSet.of(style);
            if (particles != null) synchronized (particles.active) {
                for (Puff puff : particles.active) styles.add(puff.style);
                for (WispParticle wisp : particles.wisps) styles.add(wisp.style);
            }
            return styles;
        }

        public int generation() {
            return generation;
        }

        public boolean hasPose() {
            return !latestAnchors.isEmpty();
        }

        boolean needsSurfaceFallback() {
            return style == ShadowAuraStyle.SIGNATURE && (fallbackPose || !hasPose());
        }

        float pixelationModelSize() { return Math.max(width, height); }

        float pixelationModelHeight() { return height; }

        public int particleCount() {
            return particles == null ? 0 : particles.active.size() + particles.wisps.size();
        }

        public float interpolation() {
            return Mth.clamp(accumulatedNanos / (float) STEP_NANOS, 0.0f, 0.999999f);
        }

        public Matrix4f simulationToGui() {
            return new Matrix4f(simulationToGui);
        }

        public void clear() {
            if (identity == null
                    && activeCapture == null
                    && latestAnchors.isEmpty()
                    && (particles == null || particles.active.isEmpty())) {
                return;
            }
            identity = null;
            style = ShadowAuraStyle.DEFAULT;
            resetState(0L);
        }

        private void reset(String nextIdentity, ShadowAuraStyle nextStyle) {
            identity = nextIdentity;
            style = nextStyle == null ? ShadowAuraStyle.DEFAULT : nextStyle;
            resetState(previewSeed(nextIdentity));
        }

        private void resetState(long seed) {
            generation++;
            RandomSource random = RandomSource.create(seed);
            particles = new ParticleRuntime(random, new ArrayList<>(), () -> MAX_PREVIEW_PUFFS);
            sourceState = new SourceState(0.0, 0.0, 0.0, random);
            sourceState.style = style;
            activeCapture = null;
            latestAnchors = List.of();
            simulationToGui.identity();
            width = 1.0f;
            height = 1.0f;
            lastNanos = Long.MIN_VALUE;
            accumulatedNanos = 0L;
            simulationTick = 0;
            prewarmed = false;
            fallbackPose = false;
            lastPoseCaptureNanos = Long.MIN_VALUE;
        }

        private void updateFormDimensions(RenderablePokemon pokemon) {
            if (pokemon == null) {
                return;
            }
            float formScale = Math.max(0.001f, pokemon.getForm().getBaseScale());
            width = Math.max(0.22f, pokemon.getForm().getHitbox().width() * formScale);
            height = Math.max(0.35f, pokemon.getForm().getHitbox().height() * formScale);
        }

        boolean beginPoseCapture(int expectedGeneration,
                                 RenderablePokemon pokemon,
                                 PoseStack rootStack,
                                 Bone rootPart) {
            activeCapture = null;
            if (expectedGeneration != generation || pokemon == null || rootStack == null || rootPart == null) {
                return false;
            }

            float formScale = Math.max(0.001f, pokemon.getForm().getBaseScale());
            Matrix4f rootPose = new Matrix4f(rootStack.last().pose());
            float determinant = rootPose.determinant();
            if (!rootPose.isFinite() || !Float.isFinite(determinant) || Math.abs(determinant) <= 1.0e-12f) {
                return false;
            }

            Matrix4f inverseRootPose = new Matrix4f(rootPose).invert();
            Matrix4f rawToAura = new Matrix4f().scaling(-formScale, -formScale, formScale);
            Matrix4f inverseRawToAura = new Matrix4f(rawToAura).invert();
            if (!inverseRootPose.isFinite() || !inverseRawToAura.isFinite()) {
                return false;
            }

            simulationToGui.set(previewSimulationToGui(rootPose, rawToAura));
            long nowNanos = System.nanoTime();
            if (!latestAnchors.isEmpty()
                    && lastPoseCaptureNanos != Long.MIN_VALUE
                    && nowNanos - lastPoseCaptureNanos >= 0L
                    && nowNanos - lastPoseCaptureNanos < STEP_NANOS) {
                return false;
            }
            lastPoseCaptureNanos = nowNanos;

            int anchorBudget = style == ShadowAuraStyle.SIGNATURE ? 0
                    : adaptiveAnchorBudget(boneAnchorBudget(estimateModelGeometry(rootPart, 0)), 1.0f);
            int candidateBudget = Math.min(
                    MAX_BONE_ANCHORS * BONE_ANCHOR_CANDIDATE_MULTIPLIER,
                    Math.max(anchorBudget, anchorBudget * BONE_ANCHOR_CANDIDATE_MULTIPLIER)
            );
            int bodyAnchorBudget = Math.min(
                    anchorBudget - 24,
                    Math.max(42, Math.round(anchorBudget * BODY_CUBE_ANCHOR_BUDGET_FRACTION))
            );

            float formWidth = Math.max(0.22f, pokemon.getForm().getHitbox().width() * formScale);
            float formHeight = Math.max(0.35f, pokemon.getForm().getHitbox().height() * formScale);
            activeCapture = new PreviewAnchorCapture(
                    expectedGeneration,
                    inverseRootPose,
                    rawToAura,
                    new Matrix4f(simulationToGui),
                    formWidth,
                    formHeight,
                    anchorBudget,
                    candidateBudget,
                    bodyAnchorBudget
            );
            return true;
        }

        void capturePart(ModelPart part, PoseStack renderedStack) {
            PreviewAnchorCapture capture = activeCapture;
            if (capture == null || capture.generation != generation || part == null || renderedStack == null) {
                return;
            }

            Matrix4f canonicalPose = previewCanonicalPose(
                    capture.inverseRootPose,
                    capture.rawToAura,
                    renderedStack.last().pose()
            );
            if (!canonicalPose.isFinite()) {
                return;
            }
            if (style == ShadowAuraStyle.SIGNATURE) {
                capture.signaturePose.add(part, canonicalPose);
                return;
            }
            Matrix3f canonicalNormal = new Matrix3f(canonicalPose);
            float normalDeterminant = canonicalNormal.determinant();
            if (!Float.isFinite(normalDeterminant) || Math.abs(normalDeterminant) <= 1.0e-12f) {
                canonicalNormal.identity();
            } else {
                canonicalNormal.invert().transpose();
            }

            PoseStack canonicalStack = capture.canonicalStack;
            canonicalStack.last().pose().set(canonicalPose);
            canonicalStack.last().normal().set(canonicalNormal);

            int depth = capture.renderedPartStack.size() + 1;
            RenderedPartSample sample = renderedPartSample(part, canonicalStack, Vec3.ZERO);
            boolean xdHidden = style == ShadowAuraStyle.XD_FAITHFUL && (!part.visible || part.skipDraw);
            if (xdHidden) sample = new RenderedPartSample(sample.geometryWorld(), false, sample.hasChainableDescendant());
            RenderedPartNode parent = capture.renderedPartStack.isEmpty()
                    ? null
                    : capture.renderedPartStack.get(capture.renderedPartStack.size() - 1);

            capture.classicGeometry.put(part, describeClassicPart(part, canonicalPose,
                    Vec3.ZERO, parent == null ? null : parent.part, xdHidden));
            if (style == ShadowAuraStyle.XD_FAITHFUL) {
                capture.xdSurfaceParts.put(part, captureXdSurfaces(part, canonicalPose, Vec3.ZERO));
            }

            if (sample.chainable() && parent != null && parent.chainable
                    && capture.anchorCandidates.size() < capture.candidateBudget) {
                collectRenderedBoneChainAnchors(
                        part,
                        sourceState.classicAnchors,
                        parent.geometryWorld,
                        sample.geometryWorld(),
                        !sample.hasChainableDescendant(),
                        capture.anchorCandidates,
                        depth,
                        capture.candidateBudget
                );
            }

            int anchorsBefore = capture.anchorCandidates.size();
            if (!xdHidden) collectCubeAnchors(
                    part,
                    canonicalStack,
                    Vec3.ZERO,
                    capture.anchorCandidates,
                    depth,
                    capture.candidateBudget,
                    capture.bodyAnchorBudget,
                    capture.bodyAnchorCount,
                    true
            );

            if (sample.chainable()
                    && capture.anchorCandidates.size() < capture.candidateBudget
                    && (capture.anchorCandidates.size() == anchorsBefore || depth <= 2)) {
                capture.anchorCandidates.add(new BoneAnchor(
                        sample.geometryWorld(),
                        depth,
                        1.0f,
                        AnchorRole.JOINT,
                        AnchorSource.JOINT,
                        Vec3.ZERO
                ));
            }
            capture.renderedPartStack.add(new RenderedPartNode(part, sample.geometryWorld(), sample.chainable()));
        }

        void endPart(ModelPart part) {
            PreviewAnchorCapture capture = activeCapture;
            if (capture == null || part == null || capture.renderedPartStack.isEmpty()) {
                return;
            }
            int last = capture.renderedPartStack.size() - 1;
            if (capture.renderedPartStack.get(last).part == part) {
                capture.renderedPartStack.remove(last);
                return;
            }
            for (int i = last - 1; i >= 0; i--) {
                if (capture.renderedPartStack.get(i).part == part) {
                    capture.renderedPartStack.subList(i, capture.renderedPartStack.size()).clear();
                    return;
                }
            }
        }

        boolean finishPoseCapture(int expectedGeneration) {
            PreviewAnchorCapture capture = activeCapture;
            activeCapture = null;
            if (capture == null || capture.generation != generation || expectedGeneration != generation) {
                return false;
            }

            List<BoneAnchor> anchors = style == ShadowAuraStyle.SIGNATURE
                    ? sourceState.signatureLayout.resolve(capture.signaturePose, Vec3.ZERO,
                            Math.max(capture.width, capture.height))
                    : downsampleBoneAnchors(capture.anchorCandidates, capture.anchorBudget);
            if (anchors.isEmpty()) {
                if (style == ShadowAuraStyle.SIGNATURE) {
                    latestAnchors = List.of();
                    sourceState.signatureAnchors.clear();
                }
                return false;
            }
            latestAnchors = List.copyOf(anchors);
            sourceState.classicGeometry.clear();
            sourceState.classicGeometry.putAll(capture.classicGeometry);
            replaceXdSurfaces(sourceState, capture.xdSurfaceParts);
            simulationToGui.set(capture.simulationToGui);
            width = capture.width;
            height = capture.height;
            if (fallbackPose) {
                sourceState.signatureAnchors.clear();
                sourceState.classicAnchors.clear();
            }
            fallbackPose = false;
            return true;
        }

        void useFallbackPose(float centerX,
                             float centerY,
                             float z,
                             float pixelWidth,
                             float pixelHeight) {
            if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(z)
                    || !Float.isFinite(pixelWidth) || !Float.isFinite(pixelHeight)) {
                return;
            }
            if (!fallbackPose || latestAnchors.isEmpty()) {
                latestAnchors = style == ShadowAuraStyle.SIGNATURE ? SignatureSurfaceLayout.fallback(width, height, Vec3.ZERO)
                        : fallbackBoneAnchors(width, height);
                sourceState.signatureAnchors.clear();
                sourceState.classicAnchors.clear();
                sourceState.classicGeometry.clear();
                sourceState.signatureLayout.clearFollowPose();
                sourceState.xdSurfaceCubes.clear();
                fallbackPose = true;
            }
            float scaleX = Math.max(0.001f, Math.abs(pixelWidth) / Math.max(width, 0.001f));
            float scaleY = Math.max(0.001f, Math.abs(pixelHeight) / Math.max(height, 0.001f));
            float scaleZ = Math.min(scaleX, scaleY);
            simulationToGui.set(new Matrix4f()
                    .translation(centerX, centerY + height * 0.5f * scaleY, z)
                    .scale(scaleX, -scaleY, scaleZ));
        }

        void cancelPoseCapture(int expectedGeneration) {
            if (activeCapture != null && activeCapture.generation == expectedGeneration) {
                activeCapture = null;
            }
        }

        /** Advances the same surface emission and puff simulation used in the world. */
        public boolean advance(long nowNanos, float strength) {
            if (particles == null || sourceState == null || latestAnchors.isEmpty()) {
                lastNanos = nowNanos;
                accumulatedNanos = 0L;
                return false;
            }

            float safeStrength = Mth.clamp(strength, 0.0f, 1.0f);
            if (safeStrength <= 0.001f) {
                particles.clear();
                lastNanos = nowNanos;
                accumulatedNanos = 0L;
                prewarmed = false;
                return false;
            }

            if (!prewarmed) {
                for (int i = 0; i < PREWARM_TICKS; i++) {
                    emitAndTick(safeStrength);
                }
                prewarmed = true;
                lastNanos = nowNanos;
                accumulatedNanos = 0L;
                return !particles.active.isEmpty();
            }

            if (lastNanos == Long.MIN_VALUE) {
                lastNanos = nowNanos;
                return !particles.active.isEmpty();
            }
            long elapsed = nowNanos - lastNanos;
            lastNanos = nowNanos;
            PreviewClockAdvance clock = previewClockAdvance(accumulatedNanos, elapsed);
            accumulatedNanos = clock.remainderNanos;
            for (int steps = 0; steps < clock.steps; steps++) {
                emitAndTick(safeStrength);
            }
            return !particles.active.isEmpty();
        }

        private void emitAndTick(float strength) {
            EmitterFrame frame = new EmitterFrame(
                    Vec3.ZERO,
                    Vec3.ZERO,
                    width,
                    height,
                    simulationTick,
                    0.0f
            );
            emitModelAura(frame, sourceState, latestAnchors, strength,
                    com.jayemceekay.shadowedhearts.client.ClientEffects.auraQuality(), particles);
            tickOnce(particles);
            simulationTick++;
        }

        public void forEachPuff(float partialTicks, PreviewPuffConsumer consumer) {
            forEachPuff(partialTicks, style, consumer);
        }

        public void forEachPuff(float partialTicks, ShadowAuraStyle renderStyle, PreviewPuffConsumer consumer) {
            if (particles == null || consumer == null) {
                return;
            }
            float partial = Mth.clamp(partialTicks, 0.0f, 1.0f);
            Vector3d position = new Vector3d();
            Vector3d velocity = new Vector3d();
            synchronized (particles.active) {
                for (Puff puff : particles.active) {
                    if (puff.style != renderStyle || isXdFlake(puff)) continue;
                    float alpha = sampleAlpha(puff, partial);
                    float size = sampleSize(puff, partial);
                    if (alpha <= 0.001f || size <= 0.001f) {
                        continue;
                    }
                    float broadWeight = switch (puff.type) {
                        case BROAD_HAZE -> 1.00f;
                        case CORE_HAZE -> 0.72f;
                        case WISP -> 0.10f;
                        case HOT_FLECK -> puff.style == ShadowAuraStyle.COLOSSEUM && puff.lodRoll > 0.95f ? 1.00f : 0.00f;
                        case FILAMENT -> 0.00f;
                    };
                    float sparkWeight = switch (puff.type) {
                        case BROAD_HAZE -> 0.00f;
                        case CORE_HAZE -> 0.06f;
                        case WISP -> 0.18f;
                        case HOT_FLECK -> 1.00f;
                        case FILAMENT -> 1.00f;
                    };
                    if (puff.style == ShadowAuraStyle.COLOSSEUM && puff.type != PuffType.HOT_FLECK) {
                        sparkWeight = ColosseumSmokeAppearance.ageChannel(puff.age + partial, puff.lifetime);
                    }
                    float wispWeight = sampleWispWeight(puff, partial);
                    puff.renderPosition(partial, position);
                    puff.renderVelocity(partial, velocity);
                    double speed = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
                    PuffStretch stretch = samplePuffStretch(puff.type, speed);
                    consumer.accept(
                            position.x, position.y, position.z,
                            velocity.x, velocity.y, velocity.z,
                            size,
                            alpha,
                            puff.rotation,
                            stretch.majorScale(),
                            stretch.minorScale(),
                            broadWeight,
                            sparkWeight,
                            wispWeight,
                            puff.type == PuffType.FILAMENT
                    );
                }
            }
        }

        /** Cellular puffs share the world attachment/lifecycle and face the preview camera. */
        public void drawRibbons(BufferBuilder buffer, float partialTicks, ShadowAuraStyle renderStyle,
                                Matrix4f transform, float pixelScale) {
            if (particles != null && buffer != null) {
                renderClassicRibbons(particles, renderStyle, buffer, partialTicks,
                        Vec3.ZERO, transform, pixelScale, true);
            }
        }
    }

    @FunctionalInterface
    public interface PreviewPuffConsumer {
        void accept(double x,
                    double y,
                    double z,
                    double velocityX,
                    double velocityY,
                    double velocityZ,
                    float size,
                    float alpha,
                    float rotation,
                    float majorScale,
                    float minorScale,
                    float broadWeight,
                    float sparkWeight,
                    float wispWeight,
                    boolean filament);
    }

    private static String previewIdentity(RenderablePokemon pokemon,
                                          String logicalIdentity,
                                          ShadowAuraStyle style) {
        String species = pokemon == null
                ? "none"
                : pokemon.getSpecies().getResourceIdentifier().toString();
        String aspects = pokemon == null
                ? ""
                : pokemon.getAspects().stream().filter(ShadowPokemonAuraSystem::isPreviewFormAspect)
                        .sorted().toList().toString();
        return String.valueOf(logicalIdentity) + '|' + species + '|' + aspects;
    }

    private static boolean isPreviewFormAspect(String aspect) {
        if (SHAspects.HYPER_MODE.equals(aspect) || SHAspects.REVERSE_MODE.equals(aspect)) return false;
        for (ShadowAuraStyle style : ShadowAuraStyle.values()) {
            if (style.aspectId().equals(aspect)) return false;
        }
        return true;
    }

    private static void updateSourceStyle(SourceState state,
                                          ShadowAuraStyle nextStyle) {
        if (state == null) {
            return;
        }
        ShadowAuraStyle safeStyle = nextStyle == null
                ? ShadowAuraStyle.DEFAULT
                : nextStyle;
        if (state.style == safeStyle) {
            return;
        }
        // Existing puffs retain the style captured at spawn so recall tails
        // and live aspect changes drain with their original material.
        state.style = safeStyle;
        state.debugBirths = null;
        state.classicAnchors.clear();
        state.classicRegions.clear();
        state.classicGeometry.clear();
        state.xdSurfaceCubes.clear();
        state.colosseumVolume = null;
        state.colosseumBirthCarry = 0;
        state.colosseumInfillCarry = 0;
        state.colosseumAccentCarry = 0;
        state.colosseumSmokeSequence = null;
        state.lastClassicFrameTick = Integer.MIN_VALUE;
        state.classicEmissionTick = 0;
        state.xdCurveBirthCarry = 0;
        state.xdFlakeBirthCarry = 0;
        state.xdHazeBirthCarry = 0;
        state.signatureAnchors.clear();
        state.signatureLayout.clearFollowPose();
        state.signatureFrame = null;
        state.surfaceEmitterCursor = 0;
        state.surfaceBirthCarry = 0;
    }

    private static long previewSeed(String identity) {
        long value = identity == null ? 0L : identity.hashCode();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return value;
    }

    static PreviewClockAdvance previewClockAdvance(long accumulatedNanos, long elapsedNanos) {
        long accumulated = Math.max(0L, accumulatedNanos);
        if (elapsedNanos > 0L) {
            accumulated += Math.min(elapsedNanos, PreviewInstance.MAX_ELAPSED_NANOS);
        }
        int steps = (int) Math.min(5L, accumulated / PreviewInstance.STEP_NANOS);
        long remainder = accumulated - steps * PreviewInstance.STEP_NANOS;
        if (steps == 5 && remainder >= PreviewInstance.STEP_NANOS) {
            remainder %= PreviewInstance.STEP_NANOS;
        }
        return new PreviewClockAdvance(steps, remainder);
    }

    record PreviewClockAdvance(int steps, long remainderNanos) {}

    static Matrix4f previewCanonicalPose(Matrix4f inverseRootPose,
                                         Matrix4f rawToAura,
                                         Matrix4f renderedPartPose) {
        return new Matrix4f(rawToAura)
                .mul(inverseRootPose)
                .mul(renderedPartPose);
    }

    static Matrix4f previewSimulationToGui(Matrix4f rootPose,
                                           Matrix4f rawToAura) {
        return new Matrix4f(rootPose).mul(new Matrix4f(rawToAura).invert());
    }

    public static void renderModelMask(PokemonEntity entity,
                                       ResourceLocation texture,
                                       RenderContext context,
                                       PoseStack stack,
                                       Bone rootPart) {
        if (renderingModelMask || entity == null || texture == null || context == null || rootPart == null) {
            return;
        }
        if (ModShaders.SHADOW_POKEMON_AURA_MASK == null) {
            return;
        }

        float strength = ShadowAuraEmitters.getFboAuraMaskStrength(entity);
        if (strength <= 0.001f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        float quality = auraQualityFor(entity, cameraPos, x, y, z, mc.level.getGameTime());
        if (quality < MIN_MASK_RENDER_QUALITY) {
            return;
        }

        long frameToken = currentFrameToken(mc, partialTicks);
        if (frameToken != lastMaskFrameToken) {
            lastMaskFrameToken = frameToken;
            maskCapturedThisFrame = false;
            MASK_CAPTURED_BATCHES.clear();
        }

        ShadowAuraStyle style = ShadowAuraStyleResolver.resolve(entity);
        // Legacy model masks encode coverage in A. Colosseum reserves A for
        // independently authored dark smoke and must only receive its material.
        if (style == ShadowAuraStyle.COLOSSEUM) return;
        ShadowAuraRenderBatch batch = new ShadowAuraRenderBatch(style,
                ShadowAuraRenderBatch.hasModeTint(entity.getAspects()));
        boolean clearForFirstMask = !MASK_CAPTURED_BATCHES.contains(batch);
        if (!ShadowPokemonAuraFBO.beginDensityPass(
                batch,
                clearForFirstMask,
                clearForFirstMask)) {
            return;
        }

        try {
            renderingModelMask = true;
            setupMaskUniforms(entity);
            RenderType maskType = AuraRenderTypes.shadowPokemonAuraMask(texture);
            VertexConsumer consumer = MASK_BUFFERS.getBuffer(maskType);
            rootPart.render(context, stack, consumer, FULLBRIGHT, OverlayTexture.NO_OVERLAY, packMaskColor(entity, strength));
            MASK_BUFFERS.endBatch(maskType);
            maskCapturedThisFrame = true;
            MASK_CAPTURED_BATCHES.add(batch);
        } finally {
            renderingModelMask = false;
            ShadowPokemonAuraFBO.endDensityPass(batch);
        }

    }

    public static boolean isIrisShaderPackActive() {
        return IrisCompat.IRIS_HANDLER != null && IrisCompat.IRIS_HANDLER.isShaderPackInUse();
    }

    private static boolean isIrisShadowRenderActive() {
        return IrisCompat.IRIS_HANDLER != null && IrisCompat.IRIS_HANDLER.isShadowRenderActive();
    }

    public static void renderIris() {
        renderIrisDensityOnly();
    }

    public static void renderIrisDensityOnly() {
        if (!isIrisShaderPackActive() || isIrisShadowRenderActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return;
        }

        IrisHandler.IrisRenderingSnapshot frame = IrisCompat.IRIS_HANDLER.getIrisRenderingSnapshot();
        if (frame == null || frame.renderWidth <= 0 || frame.renderHeight <= 0
                || !frame.projectionMatrix.isFinite() || !frame.modelViewMatrix.isFinite()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            // Iris moves view bobbing from the projection into the model-view.
            // Keep its complete world pair for smoke, wisps, culling and motes.
            RenderSystem.setProjectionMatrix(frame.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(frame.modelViewMatrix);
            RenderSystem.viewport(0, 0, frame.renderWidth, frame.renderHeight);
            renderDensityPipelineInternal(camera, camera.getPartialTickTime(), false);
        }
    }

    public static void compositeIris() {
        if (!isIrisShaderPackActive()) {
            return;
        }
        compositePendingWorldAura();
    }

    /** Presents the pre-combine density after vanilla has finished its transparency passes. */
    public static void compositeWorld() {
        if (isIrisShaderPackActive()) return;
        compositePendingWorldAura();
    }

    private static void compositePendingWorldAura() {
        if (!worldCompositePending) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            worldCompositePending = false;
            WORLD_PENDING_BATCHES.clear();
            pendingColosseumMoteFrame = null;
            return;
        }

        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            RenderTarget main = mc.getMainRenderTarget();
            main.bindWrite(true);
            for (ShadowAuraRenderBatch batch : List.copyOf(WORLD_PENDING_BATCHES)) {
                main.bindWrite(true);
                ShadowPokemonAuraFBO.blur(batch);
                ShadowPokemonAuraFBO.composite(batch, worldPixelLodForStyle(batch.style()),
                        worldPixelGrid(batch.style()));
            }
            renderLateColosseumMotes();
            drawWorldEmitterMarkers(lastRenderFrameToken);
        } finally {
            worldCompositePending = false;
            WORLD_PENDING_BATCHES.clear();
            pendingColosseumMoteFrame = null;
        }
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks, Matrix4f projectionMatrix) {
        renderDensityPipeline(camera, partialTicks,
                projectionMatrix == null ? RenderSystem.getProjectionMatrix() : projectionMatrix,
                RenderSystem.getModelViewMatrix());
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks,
                                             Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
        renderVanillaDensityPipeline(camera, partialTicks, projectionMatrix, modelViewMatrix, true);
    }

    /**
     * Captures world depth before Fabulous' final blit replaces main depth with a fullscreen quad.
     * The loader must call {@link #compositeWorld()} after transparency composition.
     */
    public static void renderWorldDensityOnly(Camera camera, float partialTicks,
                                               Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
        if (isIrisShaderPackActive()) return;
        renderVanillaDensityPipeline(camera, partialTicks, projectionMatrix, modelViewMatrix, false);
    }

    private static void renderVanillaDensityPipeline(Camera camera, float partialTicks,
                                                      Matrix4f projectionMatrix, Matrix4f modelViewMatrix,
                                                      boolean compositeNow) {
        if (projectionMatrix == null || modelViewMatrix == null
                || !projectionMatrix.isFinite() || !modelViewMatrix.isFinite()) return;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            // Fabulous hooks can have a transparency target bound. Its depth
            // belongs to that layer; the main target holds the opaque world depth.
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f(projectionMatrix), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(modelViewMatrix);
            renderDensityPipelineInternal(camera, partialTicks, compositeNow);
        }
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks) {
        renderDensityPipeline(camera, partialTicks, RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix());
    }

    private static void renderDensityPipelineInternal(Camera camera,
                                                      float partialTicks,
                                                      boolean compositeNow) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || camera == null) {
            clear();
            return;
        }

        long gameTime = mc.level.getGameTime();
        long frameToken = currentFrameToken(mc, partialTicks);
        if (frameToken == lastRenderFrameToken) {
            return;
        }
        lastRenderFrameToken = frameToken;
        pendingColosseumMoteFrame = null;

        tickToGameTime(gameTime);
        // Refresh existing puffs too, so entering or leaving either mode changes
        // the complete aura using the client-synchronized aspects.
        SOURCES.forEach((id, state) -> {
            if (mc.level.getEntity(id) instanceof PokemonEntity pokemon) {
                state.modeTint = ShadowAuraRenderBatch.hasModeTint(pokemon.getAspects());
            }
        });
        SOURCES.entrySet().removeIf(e -> {
            if (gameTime - e.getValue().lastSeenTick <= 30) return false;
            e.getValue().classicAnchors.clear();
            return true;
        });

        Set<ShadowAuraRenderBatch> puffBatches = new LinkedHashSet<>();
        EnumSet<ShadowAuraStyle> puffStyles = EnumSet.noneOf(ShadowAuraStyle.class);
        synchronized (ACTIVE) {
            for (Puff puff : ACTIVE) {
                puffBatches.add(renderBatch(puff.style, puff.source));
                puffStyles.add(puff.style);
            }
            for (WispParticle wisp : WORLD_PARTICLES.wisps) {
                puffBatches.add(renderBatch(wisp.style, wisp.source));
                puffStyles.add(wisp.style);
            }
        }
        boolean hasPuffs = !puffStyles.isEmpty();
        boolean hasLateMotes = LuminousMoteEmitters.hasColosseumParticles();
        Set<ShadowAuraRenderBatch> maskBatches = new LinkedHashSet<>();
        if (maskCapturedThisFrame && lastMaskFrameToken == frameToken) {
            maskBatches.addAll(MASK_CAPTURED_BATCHES);
        }
        boolean hasModelMask = !maskBatches.isEmpty();
        pendingEmitterMarkers = ClientEffects.showAuraEmitters()
                ? collectWorldEmitterMarkers(camera, frameToken, gameTime) : null;

        if (!hasPuffs && !hasModelMask && !hasLateMotes && pendingEmitterMarkers == null) {
            updateWorldPixelLodsForFrame(null, puffStyles);
            WORLD_PIXEL_GRIDS.clear();
            worldCompositePending = false;
            WORLD_PENDING_BATCHES.clear();
            return;
        }

        Set<ShadowAuraRenderBatch> requestedBatches = new LinkedHashSet<>();
        requestedBatches.addAll(puffBatches);
        requestedBatches.addAll(maskBatches);
        if (hasLateMotes) requestedBatches.add(new ShadowAuraRenderBatch(ShadowAuraStyle.COLOSSEUM, false));
        Set<ShadowAuraRenderBatch> renderedBatches = new LinkedHashSet<>();
        Map<ShadowAuraStyle, List<WorldPixelContributor>>
                visiblePixelContributors = new EnumMap<>(ShadowAuraStyle.class);
        // Keep presentation order stable across particle births and mode changes.
        for (ShadowAuraRenderBatch batch : ShadowAuraRenderBatch.all()) {
            ShadowAuraStyle style = batch.style();
            if (!requestedBatches.contains(batch)) continue;
            boolean styleHasPuffs = puffBatches.contains(batch)
                    && ShadowPokemonAuraFBO.worldDensityShader(style) != null;
            boolean styleHasMask = maskBatches.contains(batch);
            boolean captureMoteDepth = hasLateMotes && style == ShadowAuraStyle.COLOSSEUM && !batch.modeTint();
            if (styleHasPuffs || captureMoteDepth) {
                if (!ShadowPokemonAuraFBO.beginDensityPass(
                        batch,
                        !styleHasMask,
                        true)) {
                    continue;
                }

                try {
                    if (styleHasPuffs) visiblePixelContributors
                            .computeIfAbsent(style, ignored -> new ArrayList<>())
                            .addAll(renderDensitySplats(camera, partialTicks, batch));
                } finally {
                    ShadowPokemonAuraFBO.endDensityPass(batch);
                }
                if (captureMoteDepth) captureColosseumMoteFrame(camera, partialTicks, frameToken);
            }
            if (styleHasPuffs || styleHasMask) {
                renderedBatches.add(batch);
            }
        }
        updateWorldPixelLodsForFrame(
                visiblePixelContributors,
                puffStyles
        );
        updateWorldPixelGrids(camera, visiblePixelContributors);

        if (!renderedBatches.isEmpty() || pendingColosseumMoteFrame != null || pendingEmitterMarkers != null) {
            if (compositeNow) {
                for (ShadowAuraRenderBatch batch : renderedBatches) {
                    ShadowPokemonAuraFBO.blur(batch);
                    ShadowPokemonAuraFBO.composite(
                            batch,
                            worldPixelLodForStyle(batch.style()),
                            worldPixelGrid(batch.style()));
                }
                renderLateColosseumMotes();
                drawWorldEmitterMarkers(frameToken);
                worldCompositePending = false;
                WORLD_PENDING_BATCHES.clear();
            } else {
                worldCompositePending = true;
                WORLD_PENDING_BATCHES.clear();
                WORLD_PENDING_BATCHES.addAll(renderedBatches);
            }
        } else {
            worldCompositePending = false;
            WORLD_PENDING_BATCHES.clear();
        }
    }

    private static AuraEmitterDebug.WorldFrame collectWorldEmitterMarkers(Camera camera, long frameToken, long gameTime) {
        List<AuraEmitterDebug.Dot> dots = new ArrayList<>();
        Vector3d scratch = new Vector3d();
        for (SourceState source : SOURCES.values()) {
            if (gameTime - source.lastSeenTick > 1) continue;
            if (source.style == ShadowAuraStyle.SIGNATURE) {
                for (BoneAnchor site : source.signatureAnchors.values()) {
                    if (source.signatureFrame == null) {
                        dots.add(AuraEmitterDebug.surface(site));
                        continue;
                    }
                    Vec3 normal = site.normal();
                    source.signatureFrame.toWorldDirection(scratch.set(normal.x, normal.y, normal.z));
                    double worldNormalY = scratch.y;
                    Vec3 position = site.pos();
                    source.signatureFrame.toWorldPosition(scratch.set(position.x, position.y, position.z));
                    dots.add(AuraEmitterDebug.surface(site, new Vec3(scratch.x, scratch.y, scratch.z), worldNormalY));
                }
            }
            if (source.debugBirths != null) source.debugBirths.append(WORLD_PARTICLES.tickSerial, dots);
        }
        if (dots.isEmpty()) return null;
        return new AuraEmitterDebug.WorldFrame(frameToken, camera.getPosition(), AuraEmitterDebug.currentTransform(), dots);
    }

    private static void drawWorldEmitterMarkers(long frameToken) {
        AuraEmitterDebug.WorldFrame frame = pendingEmitterMarkers;
        pendingEmitterMarkers = null;
        if (frame != null && frame.token() == frameToken && ClientEffects.showAuraEmitters()) {
            AuraEmitterDebug.draw(frame.dots(), frame.transform(), frame.camera(), -1, -1, 1, 1);
        }
    }

    /** Called on actual ParticleEngine births, including Signature's separate luminous-mote layer. */
    public static void recordMoteEmitter(PokemonEntity pokemon, double x, double y, double z) {
        if (!ClientEffects.showAuraEmitters()) return;
        SourceState source = SOURCES.get(pokemon.getId());
        if (source != null) {
            if (source.debugBirths == null) source.debugBirths = new AuraEmitterDebug.Births();
            source.debugBirths.add(WORLD_PARTICLES.tickSerial, new Vec3(x, y, z), Kind.MOTE);
        }
    }

    private record ColosseumMoteFrame(Object level, Camera camera, float partialTicks, long gameTime,
                                      long frameToken, Matrix4f projection, Matrix4f modelView,
                                      int sceneDepth, int depthWidth, int depthHeight, int width, int height) {}

    private static void captureColosseumMoteFrame(Camera camera, float partialTicks, long frameToken) {
        Minecraft mc = Minecraft.getInstance();
        RenderTargetAccessor main = (RenderTargetAccessor) (Object) mc.getMainRenderTarget();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        // Freeze the same world pair that produced this frame's smoke and depth.
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        int sceneDepth = ShadowPokemonAuraFBO.getDensityDepthTextureId(ShadowAuraStyle.COLOSSEUM);
        int depthWidth = ShadowPokemonAuraFBO.getDensityWidth(ShadowAuraStyle.COLOSSEUM);
        int depthHeight = ShadowPokemonAuraFBO.getDensityHeight(ShadowAuraStyle.COLOSSEUM);
        if (sceneDepth <= 0 || !projection.isFinite() || !modelView.isFinite()
                || !compatibleMoteDepthSize(depthWidth, depthHeight, main.getWidth(), main.getHeight())) return;
        pendingColosseumMoteFrame = new ColosseumMoteFrame(mc.level, camera, partialTicks, mc.level.getGameTime(),
                frameToken, projection, modelView, sceneDepth, depthWidth, depthHeight, main.getWidth(), main.getHeight());
    }

    static boolean compatibleMoteDepthSize(int depthWidth, int depthHeight, int outputWidth, int outputHeight) {
        if (depthWidth <= 0 || depthHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return false;
        // Scaled Iris views retain normalized depth alignment; allow only the
        // rounding error of two depth texels, not a changed/cropped aspect ratio.
        return Math.abs(depthWidth / (double) depthHeight - outputWidth / (double) outputHeight) <= 2.0 / depthHeight;
    }

    private static void renderLateColosseumMotes() {
        ColosseumMoteFrame frame = pendingColosseumMoteFrame;
        pendingColosseumMoteFrame = null;
        if (frame == null || !LuminousMoteEmitters.hasColosseumParticles()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        RenderTargetAccessor access = (RenderTargetAccessor) (Object) main;
        if (mc.level != frame.level || mc.level == null || mc.level.getGameTime() != frame.gameTime
                || frame.frameToken != lastRenderFrameToken || access.getWidth() != frame.width || access.getHeight() != frame.height) return;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            main.bindWrite(false);
            RenderSystem.viewport(0, 0, frame.width, frame.height);
            RenderSystem.setProjectionMatrix(frame.projection, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(frame.modelView);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            Runnable draw = () -> {
                RenderSystem.setProjectionMatrix(frame.projection, VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.getModelViewMatrix().set(frame.modelView);
                LuminousMoteEmitters.renderColosseumLate(frame.camera, frame.partialTicks,
                        frame.sceneDepth, frame.width, frame.height);
            };
            if (!ShadowPokemonAuraFBO.compositeColosseumMotes(
                    worldPixelGrid(ShadowAuraStyle.COLOSSEUM), draw)) draw.run();
        }
    }

    private static void updateWorldPixelGrids(Camera camera,
            Map<ShadowAuraStyle, List<WorldPixelContributor>> contributors) {
        WORLD_PIXEL_GRIDS.clear();
        int[] viewport = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        for (ShadowAuraStyle style : List.of(ShadowAuraStyle.COLOSSEUM, ShadowAuraStyle.XD_FAITHFUL)) {
            AuraPixelationStyle presentation = ShadowPokemonAuraFBO.pixelationStyle(style);
            if (presentation == AuraPixelationStyle.SMOOTH) continue;
            double size = Double.POSITIVE_INFINITY;
            List<WorldPixelContributor> visible = contributors.getOrDefault(style, List.of());
            for (WorldPixelContributor contributor : visible) {
                double candidate = AuraPixelationScale.worldSize(projection, contributor.cameraDistance,
                        contributor.source.pixelModelSize, viewport[2], viewport[3]);
                if (candidate > 0) size = Math.min(size, candidate);
            }
            // Retained Colosseum motes may outlive the final visible smoke puff.
            if (!Double.isFinite(size) && style == ShadowAuraStyle.COLOSSEUM && pendingColosseumMoteFrame != null) {
                for (SourceState source : SOURCES.values()) {
                    if (source.style != style || !pixelSourceOverlapsViewport(source, camera, projection)) continue;
                    double candidate = AuraPixelationScale.worldSize(projection,
                            sourceCameraDistance(source, camera.getPosition(), Double.NaN),
                            source.pixelModelSize, viewport[2], viewport[3]);
                    if (candidate > 0) size = Math.min(size, candidate);
                }
            }
            if (Double.isFinite(size) && size > 0 && viewport[3] > 0)
                WORLD_PIXEL_GRIDS.put(style, new WorldPixelGrid(presentation, size / viewport[3]));
        }
    }

    private static MoonwoundPixelationScale.Grid worldPixelGrid(ShadowAuraStyle style) {
        WorldPixelGrid saved = WORLD_PIXEL_GRIDS.get(style);
        if (saved == null) return null;
        int[] viewport = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
        // Iris may render geometry at a scaled resolution, then present at the window's size.
        return AuraPixelationScale.worldGrid(saved.style, saved.normalizedSize * viewport[3], viewport[2], viewport[3]);
    }

    private static boolean pixelSourceOverlapsViewport(SourceState source, Camera camera, Matrix4f projection) {
        Vec3 relative = new Vec3(source.pixelCenterX, source.pixelCenterY, source.pixelCenterZ)
                .subtract(camera.getPosition());
        Matrix4f transform = new Matrix4f(projection).mul(RenderSystem.getModelViewMatrix())
                .translate((float) relative.x, (float) relative.y, (float) relative.z).rotate(camera.rotation());
        float radius = (float) Math.max(.5, source.pixelModelSize * 2);
        return projectedPuffOverlapsViewport(
                transform.transform(new Vector4f(-radius, -radius, 0, 1)),
                transform.transform(new Vector4f(radius, -radius, 0, 1)),
                transform.transform(new Vector4f(radius, radius, 0, 1)),
                transform.transform(new Vector4f(-radius, radius, 0, 1)));
    }

    private static void updateWorldPixelLodsForFrame(
            Map<ShadowAuraStyle, List<WorldPixelContributor>> contributorsByStyle,
            EnumSet<ShadowAuraStyle> activePuffStyles) {
        int maximumPixelSize = ShadowPokemonAuraFBO.maximumWorldPixelSize();
        float maximumLod = maximumWorldPixelLod(maximumPixelSize);
        for (ShadowAuraStyle style : ShadowAuraStyle.values()) {
            List<WorldPixelContributor> contributors = contributorsByStyle == null
                    ? null
                    : contributorsByStyle.get(style);
            if (contributors != null && !contributors.isEmpty()) {
                float sharedStyleLod = maximumLod;
                for (WorldPixelContributor contributor : contributors) {
                    SourceState source = contributor.source;
                    source.stableWorldPixelLod = stabilizedWorldPixelLod(
                            source.stableWorldPixelLod,
                            maximumPixelSize,
                            contributor.cameraDistance);
                    sharedStyleLod = Math.min(
                            sharedStyleLod,
                            source.stableWorldPixelLod
                    );
                }
                WORLD_PIXEL_LODS_THIS_FRAME.put(style, sharedStyleLod);
            } else if (activePuffStyles == null
                    || !activePuffStyles.contains(style)) {
                WORLD_PIXEL_LODS_THIS_FRAME.put(style, maximumLod);
            }
        }
    }

    private static float worldPixelLodForStyle(ShadowAuraStyle style) {
        return WORLD_PIXEL_LODS_THIS_FRAME.getOrDefault(
                style == null ? ShadowAuraStyle.DEFAULT : style,
                maximumWorldPixelLod(
                        ShadowPokemonAuraFBO.maximumWorldPixelSize()));
    }

    private static Map<ShadowAuraStyle, Float> initialWorldPixelLods() {
        Map<ShadowAuraStyle, Float> lods = new EnumMap<>(ShadowAuraStyle.class);
        float maximumLod = maximumWorldPixelLod(
                ShadowPokemonAuraFBO.maximumWorldPixelSize());
        for (ShadowAuraStyle style : ShadowAuraStyle.values()) {
            lods.put(style, maximumLod);
        }
        return lods;
    }

    private static void resetWorldPixelLods() {
        WORLD_PIXEL_LODS_THIS_FRAME.clear();
        WORLD_PIXEL_LODS_THIS_FRAME.putAll(initialWorldPixelLods());
    }

    static boolean projectedPuffOverlapsViewport(
            Vector4f corner0,
            Vector4f corner1,
            Vector4f corner2,
            Vector4f corner3) {
        if (corner0 == null
                || corner1 == null
                || corner2 == null
                || corner3 == null
                || !corner0.isFinite()
                || !corner1.isFinite()
                || !corner2.isFinite()
                || !corner3.isFinite()) {
            return false;
        }

        for (int plane = 0; plane < 6; plane++) {
            if (outsideClipPlane(corner0, plane)
                    && outsideClipPlane(corner1, plane)
                    && outsideClipPlane(corner2, plane)
                    && outsideClipPlane(corner3, plane)) {
                return false;
            }
        }
        return true;
    }

    private static boolean outsideClipPlane(Vector4f point, int plane) {
        return switch (plane) {
            case 0 -> point.x + point.w < 0.0f;
            case 1 -> point.w - point.x < 0.0f;
            case 2 -> point.y + point.w < 0.0f;
            case 3 -> point.w - point.y < 0.0f;
            case 4 -> point.z + point.w < 0.0f;
            case 5 -> point.w - point.z < 0.0f;
            default -> true;
        };
    }

    private static long currentFrameToken(Minecraft mc, float partialTicks) {
        long frameToken = mc.getFrameTimeNs();
        if (frameToken == 0L && mc.level != null) {
            frameToken = (mc.level.getGameTime() << 20) ^ (long) (partialTicks * 1000.0f);
        }
        return frameToken;
    }

    private static int packMaskColor(PokemonEntity entity, float strength) {
        boolean hyperMode = entity.getAspects() != null && entity.getAspects().contains(SHAspects.HYPER_MODE);
        int alpha = Math.round(Mth.clamp(strength, 0.0f, 1.0f) * 255.0f);
        int hot = hyperMode ? 58 : 24;
        int core = hyperMode ? 232 : 206;
        return (alpha << 24) | (hot << 16) | (core << 8) | 255;
    }

    private static void setupMaskUniforms(PokemonEntity entity) {
        var shader = ModShaders.SHADOW_POKEMON_AURA_MASK;
        if (shader == null) {
            return;
        }

        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float expand = Mth.clamp(modelSize * 0.075f, 0.045f, 0.22f);
        float screenExpand = Mth.clamp(0.0045f + modelSize * 0.0008f, 0.0045f, 0.010f);

        Uniform uExpand = shader.getUniform("AuraExpand");
        if (uExpand != null) {
            uExpand.set(expand);
        }
        Uniform uScreenExpand = shader.getUniform("AuraScreenExpand");
        if (uScreenExpand != null) {
            uScreenExpand.set(screenExpand);
        }
    }

    private static float auraQualityFor(PokemonEntity entity, Vec3 cameraPos, double x, double y, double z, long gameTime) {
        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        double centerY = y + entity.getBbHeight() * 0.5;
        double distance = cameraPos.distanceTo(new Vec3(x, centerY, z));
        return AuraPerformanceBudget.auraQuality(modelSize, distance, activeAuraSourceCount(gameTime))
                * ClientEffects.auraQuality();
    }

    private static int activeAuraSourceCount(long gameTime) {
        int count = 0;
        for (SourceState state : SOURCES.values()) {
            if (gameTime - state.lastSeenTick <= 20L) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static int adaptiveAnchorBudget(int baseBudget, float quality) {
        return AuraPerformanceBudget.forMode(ClientEffects.auraPerformanceMode())
                .adaptiveAnchorBudget(baseBudget, quality);
    }

    private static void emitModelAura(EmitterFrame frame,
                                      SourceState state,
                                      List<BoneAnchor> anchors,
                                      float strength,
                                      float quality,
                                      ParticleRuntime particles) {
        // Keep render-quality changes local to admission. Existing particles keep
        // their age, movement and lifetime while the new population settles.
        state.performanceBudget = AuraPerformanceBudget.forMode(ClientEffects.auraPerformanceMode());
        if (state.style == ShadowAuraStyle.SIGNATURE) {
            emitSignatureSurface(frame, state, anchors, strength, quality, particles);
        } else {
            emitClassicAura(frame, state, anchors, strength, quality, particles,
                    ShadowAuraStyleProfiles.forStyle(state.style));
        }
    }

    private static List<XdAuraSurfaceSampler.Cube> captureXdSurfaces(ModelPart part, Matrix4f pose, Vec3 offset) {
        return XdAuraSurfaceSampler.capture(part, pose, offset, cube -> cubeTemplate(cube).auraAnchor());
    }

    private static void replaceXdSurfaces(SourceState state, Map<ModelPart, List<XdAuraSurfaceSampler.Cube>> parts) {
        state.xdSurfaceCubes.clear();
        for (List<XdAuraSurfaceSampler.Cube> cubes : parts.values()) state.xdSurfaceCubes.addAll(cubes);
    }

    private static List<ClassicRegion> rebuildClassicRegions(SourceState state, List<BoneAnchor> anchors) {
        return ClassicAuraRegions.rebuildClassicRegions(state.classicGeometry, state.xdSurfaceCubes,
                state.classicRegions, state.classicAnchors, anchors, state.style, state.species);
    }

    private static void emitClassicAura(EmitterFrame frame, SourceState state, List<BoneAnchor> anchors,
                                        float strength, float quality, ParticleRuntime particles,
                                        ShadowAuraStyleProfile profile) {
        if (anchors.isEmpty() || strength <= 0.001f) return;
        state.classicAnchors.clear();
        for (BoneAnchor anchor : anchors) if (anchor.key() != null && finite(anchor.pos())) state.classicAnchors.put(anchor.key(), anchor);
        if (state.classicAnchors.isEmpty()) return;
        List<ClassicRegion> regions = rebuildClassicRegions(state, anchors);
        if (regions.isEmpty()) return;
        state.classicAnchorTick = particles.tickSerial;
        boolean xd = state.style == ShadowAuraStyle.XD_FAITHFUL;
        if (!xd) state.colosseumVolume = colosseumVolume(state, regions);
        if (state.lastClassicFrameTick == frame.tickCount) return;
        if (!ClientEffects.showAuraEmitters()) state.debugBirths = null;
        else if (state.debugBirths == null) state.debugBirths = new AuraEmitterDebug.Births();
        int interval = state.lastClassicFrameTick == Integer.MIN_VALUE ? 1
                : Mth.clamp(frame.tickCount - state.lastClassicFrameTick, 1, 4);
        state.lastClassicFrameTick = frame.tickCount;
        state.classicEmissionTick++;
        if (!xd) {
            emitColosseumVolume(frame, state, strength, quality, interval, particles, profile);
            return;
        }
        float demand = 0;
        for (ClassicRegion region : regions) demand += region.demand;
        int fullNearTarget = ClassicAuraMorphology.nearCoverageTarget(demand, xd);
        int nearTarget = state.performanceBudget.xdHazeTarget(fullNearTarget);
        int plumeTarget = xd ? 0 : ClassicAuraMorphology.plumeCoverageTarget(demand);
        int curveTarget = xd ? state.performanceBudget.xdCellTarget(ClassicAuraMorphology.curveTarget(demand, quality)) : 0;
        int sourcePuffs = 0, sourceCurves = 0;
        // One inventory pass per emission tick, never per render frame. The existing
        // runtime remains owner-local in previews and globally bounded in world views.
        synchronized (particles.active) {
            for (Puff puff : particles.active) {
                if (puff.source != state || !puff.classic) continue;
                sourcePuffs++;
                if (puff.style != state.style) continue;
                ClassicRegion region = state.classicRegions.get(puff.classicRegionKey);
                if (region != null && puff.type == PuffType.BROAD_HAZE) {
                    float contribution = Math.max(0.20f, sampleAlpha(puff, 0) / Math.max(0.001f, puff.baseDensity));
                    if (puff.colosseumPlume) region.plumeOccupancy += contribution;
                    else region.nearOccupancy += contribution;
                }
            }
            for (WispParticle particle : particles.wisps) {
                if (particle.source != state) continue;
                sourceCurves++;
                if (particle.style != state.style) continue;
                ClassicRegion region = state.classicRegions.get(particle.regionKey);
                if (region != null) {
                    float visibility = Math.max(.15f, particle.wisp.opacity(0) / Math.max(.001f, strength * .94f));
                    region.curveOccupancy += visibility;
                }
            }
        }
        int nearBirths = Mth.clamp((int) Math.ceil(fullNearTarget / 32.0f * interval), 1, 20);
        if (state.performanceBudget == AuraPerformanceBudget.PERFORMANCE) {
            // Fractional admission also reduces a one-puff tick without changing
            // the simulation clock or introducing a permanent one-puff floor.
            state.xdHazeBirthCarry += state.performanceBudget.xdHazeRate(nearBirths);
            nearBirths = (int) state.xdHazeBirthCarry;
            state.xdHazeBirthCarry -= nearBirths;
        }
        int plumeBirths = xd ? 0 : Mth.clamp((int) Math.ceil(plumeTarget / 40.0f * interval), 2, 18);
        for (int i = 0; i < nearBirths + plumeBirths && sourcePuffs < CLASSIC_PUFFS_PER_SOURCE; i++) {
            boolean plume = i >= nearBirths;
            ClassicRegion region = mostUndercoveredRegion(regions, demand, plume ? plumeTarget : nearTarget, plume ? 1 : 0);
            if (region == null) continue;
            BoneAnchor anchor = region.nextAnchor();
            if (plume) {
                spawnColosseumPlume(state, anchor, region, strength, frame.modelSize(), particles, profile);
                region.plumeOccupancy += 0.60f;
            } else {
                spawnClassicPuff(state, anchor, region, strength, false, frame.modelSize(), particles, profile);
                region.nearOccupancy += 0.60f;
            }
            sourcePuffs++;
        }
        // XD points are accents. They no longer compete with the curling field;
        // regional random placement and transverse drift avoid vertical bead rows.
        // World Colosseum motes have a persistent ParticleEngine layer. GUI
        // previews retain their local FBO accents because that engine is absent.
        boolean worldColosseumMotes = !xd && particles == WORLD_PARTICLES;
        int moteBirths = quality <= 0.38f || worldColosseumMotes ? 0
                : xd ? (frame.tickCount % 10 < interval ? 1 : 0) : Math.min(4, interval * 2);
        if (xd) {
            state.xdFlakeBirthCarry += state.performanceBudget.accentRate(quality <= .38f ? 0 : .76 * quality) * interval;
            moteBirths = Math.min(4, (int) state.xdFlakeBirthCarry);
            state.xdFlakeBirthCarry -= Math.floor(state.xdFlakeBirthCarry);
        }
        for (int i = 0; i < moteBirths && sourcePuffs < CLASSIC_PUFFS_PER_SOURCE; i++) {
            ClassicRegion region = chooseClassicRegion(regions, demand, particles.random.nextFloat());
            if (!xd && particles.random.nextFloat() > region.detailEmphasis) continue;
            BoneAnchor anchor = region.anchors.get(particles.random.nextInt(region.anchors.size()));
            spawnClassicPuff(state, anchor, region, strength * (xd ? 0.95f : 0.75f), true,
                    frame.modelSize(), particles, profile);
            sourcePuffs++;
        }
        if (!xd || !profile.filament().enabled()) return;
        // Large puffs arrive independently and drift for longer; integrate their
        // admission rate so no lifetime cohort or missed tick creates a wave.
        state.xdCurveBirthCarry += curveTarget / 30.0 * interval;
        // Admit up to four missed ticks at the maximum 250-fold target.
        int curveBirths = Math.min(34, (int) state.xdCurveBirthCarry);
        state.xdCurveBirthCarry -= Math.floor(state.xdCurveBirthCarry);
        int curveLimit = (int) Math.ceil(curveTarget * 1.5);
        for (int i = 0; i < curveBirths && sourceCurves < curveLimit && particles.wisps.size() < CLASSIC_WISPS_GLOBAL; i++) {
            ClassicRegion region = mostUndercoveredRegion(regions, demand, curveTarget, 2);
            if (region == null) break;
            boolean lowerFold = state.species.endsWith(":ledyba") && particles.random.nextFloat() < .75f;
            BoneAnchor selected = region.nextCurveAnchor(lowerFold);
            Vec3 outward = region.normal(selected);
            float localSize = Math.max(ClassicAuraMorphology.curlScale(selected.localScale(), region.geometry.envelope()) * 1.18f,
                    region.xdFieldScale * .72f)
                    * (0.90f + particles.random.nextFloat() * 0.26f);
            float padding = 0;
            boolean useSurface = !region.xdSurfaceCubes.isEmpty() && particles.random.nextFloat() < .80f;
            XdAuraSurfaceSampler.Sample surface = useSurface
                    ? XdAuraSurfaceSampler.sample(region.xdSurfaceCubes, state.xdSurfaceCubes,
                            particles.random, localSize, lowerFold) : null;
            Vec3 root;
            if (surface != null) {
                root = surface.position();
                outward = surface.normal();
            } else {
                root = xdCloudPosition(state, region, localSize, particles, lowerFold, useSurface);
                if (root == null) continue;
                Vec3 radial = root.subtract(region.geometry.center());
                if (radial.lengthSqr() > .00001) outward = radial.normalize();
            }
            ClassicAuraWisp wisp = new ClassicAuraWisp(selected.key(), root, outward, localSize,
                    particles.random.nextFloat(), 36 + particles.random.nextInt(15), strength * 0.94f);
            synchronized (particles.active) {
                particles.wisps.add(new WispParticle(state, state.style, wisp, padding, region.key));
            }
            if (state.debugBirths != null) state.debugBirths.add(particles.tickSerial, root, Kind.XD_CELL);
            region.curveOccupancy++;
            sourceCurves++;
        }
    }

    private static Vec3 xdCloudPosition(SourceState state, ClassicRegion region, float scale,
                                      ParticleRuntime particles, boolean lowerBias) {
        return xdCloudPosition(state, region, scale, particles, lowerBias, false);
    }

    private static Vec3 xdCloudPosition(SourceState state, ClassicRegion region, float scale,
                                      ParticleRuntime particles, boolean lowerBias, boolean requireOutside) {
        ClassicPartGeometry g = region.geometry;
        Vec3 candidate = g.center();
        // Keep the filled, overlapping cloud, but spend fewer births inside the
        // opaque body or a neighbouring limb. Bounded retries keep emission work
        // predictable even for heavily overlapping authored model boxes.
        for (int attempt = 0; attempt < (requireOutside ? 12 : 4); attempt++) {
            candidate = XdAuraSpawnVolume.sample(g.center(), g.axisX(), g.axisY(), g.axisZ(), g.x(), g.y(), g.z(), scale,
                    particles.random.nextDouble(), particles.random.nextDouble(), particles.random.nextDouble(), lowerBias);
            if (!insideXdModel(state, candidate) && (!requireOutside || !insideXdCapturedSolid(state, candidate))) return candidate;
        }
        return requireOutside ? null : candidate;
    }

    private static boolean insideXdCapturedSolid(SourceState state, Vec3 point) {
        for (XdAuraSurfaceSampler.Cube cube : state.xdSurfaceCubes) if (cube.contains(point)) return true;
        return false;
    }

    private static boolean insideXdModel(SourceState state, Vec3 point) {
        if (state.classicGeometry.isEmpty()) {
            for (ClassicRegion region : state.classicRegions.values()) {
                if (insideXdSolid(region.geometry, point)) return true;
            }
        } else {
            for (ClassicPartGeometry geometry : state.classicGeometry.values()) {
                if (insideXdSolid(geometry, point)) return true;
            }
        }
        return false;
    }

    private static boolean insideXdSolid(ClassicPartGeometry g, Vec3 point) {
        return !g.surface() && XdAuraSpawnVolume.contains(point, g.center(), g.axisX(), g.axisY(), g.axisZ(), g.x(), g.y(), g.z());
    }

    private static ColosseumAuraVolume colosseumVolume(SourceState state, List<ClassicRegion> regions) {
        List<ClassicPartGeometry> geometry = new ArrayList<>();
        for (ClassicPartGeometry part : state.classicGeometry.values()) {
            if (!part.empty()) geometry.add(part);
        }
        if (geometry.isEmpty()) {
            for (ClassicRegion region : regions) geometry.add(region.geometry);
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (ClassicPartGeometry g : geometry) {
            // Use captured geometry before anatomical merging or region caps:
            // small offset limbs still belong to the whole posed-model volume.
            double hx = (Math.abs(g.axisX().x) * g.x() + Math.abs(g.axisY().x) * g.y() + Math.abs(g.axisZ().x) * g.z()) * .5;
            double hy = (Math.abs(g.axisX().y) * g.x() + Math.abs(g.axisY().y) * g.y() + Math.abs(g.axisZ().y) * g.z()) * .5;
            double hz = (Math.abs(g.axisX().z) * g.x() + Math.abs(g.axisY().z) * g.y() + Math.abs(g.axisZ().z) * g.z()) * .5;
            minX = Math.min(minX, g.center().x - hx); maxX = Math.max(maxX, g.center().x + hx);
            minY = Math.min(minY, g.center().y - hy); maxY = Math.max(maxY, g.center().y + hy);
            minZ = Math.min(minZ, g.center().z - hz); maxZ = Math.max(maxZ, g.center().z + hz);
        }
        return ColosseumAuraVolume.fromBounds(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static void emitColosseumVolume(EmitterFrame frame, SourceState state, float strength,
                                             float quality, int interval, ParticleRuntime particles,
                                             ShadowAuraStyleProfile profile) {
        ColosseumAuraVolume volume = state.colosseumVolume;
        int sourcePuffs = 0;
        synchronized (particles.active) {
            for (Puff puff : particles.active) {
                if (puff.source != state || !puff.classic) continue;
                sourcePuffs++;
            }
        }
        // Integrate a continuous rate instead of opening/closing births when
        // one cohort brightens or fades. Replenish the shorter-lived smoke
        // steadily so faster turnover does not open gaps between cohorts.
        double rate = state.performanceBudget.colosseumSmokeRate(volume.smokeTarget(quality) / 36.0);
        double desired = state.colosseumBirthCarry + rate * interval;
        int births = Math.min(28, (int) Math.floor(desired));
        state.colosseumBirthCarry = desired - Math.floor(desired);
        double infillDesired = state.colosseumInfillCarry
                + state.performanceBudget.colosseumInfillRate(rate * volume.infillRateMultiplier()) * interval;
        int infillBirths = Math.min(12, (int) Math.floor(infillDesired));
        state.colosseumInfillCarry = infillDesired - Math.floor(infillDesired);
        if (volume.largeModelWeight() > 0 && state.colosseumSmokeSequence == null) {
            state.colosseumSmokeSequence = new ColosseumSmokeSpawnSequence(
                    Float.floatToIntBits(state.seed) * 0x9E3779B97F4A7C15L);
        }
        for (int i = 0; i < births && sourcePuffs < CLASSIC_PUFFS_PER_SOURCE; i++) {
            emitColosseumSmokePuff(frame, state, strength, particles, profile, false);
            sourcePuffs++;
        }
        // Add detail without replacing or enlarging the established billows.
        // Separate fractional admission keeps their original replenishment rate.
        for (int i = 0; i < infillBirths && sourcePuffs < CLASSIC_PUFFS_PER_SOURCE; i++) {
            emitColosseumSmokePuff(frame, state, strength, particles, profile, true);
            sourcePuffs++;
        }
        // World points use the full-resolution late emitter. Preview accents
        // sample the same volume rather than lining up on anatomical anchors.
        if (particles == WORLD_PARTICLES || quality <= .38f) return;
        int accentBirths = Math.min(4, interval);
        if (state.performanceBudget == AuraPerformanceBudget.PERFORMANCE) {
            state.colosseumAccentCarry += state.performanceBudget.accentRate(accentBirths);
            accentBirths = (int) state.colosseumAccentCarry;
            state.colosseumAccentCarry -= accentBirths;
        }
        for (int i = 0; i < accentBirths && sourcePuffs < CLASSIC_PUFFS_PER_SOURCE; i++) {
            Vec3 position = volume.sample(particles.random.nextDouble(), particles.random.nextDouble(), particles.random.nextDouble());
            float local = volume.puffScale();
            Vec3 direction = randomUnitVector(particles.random);
            Puff mote = new Puff(position.x, position.y, position.z,
                    direction.x * local * .015, local * (.005 + particles.random.nextFloat() * .020), direction.z * local * .015,
                    Mth.clamp(local * .05f, .014f, .07f), 30 + particles.random.nextInt(15), strength * .54f,
                    PuffType.HOT_FLECK, frame.modelSize(), state, particles.random);
            mote.classic = true;
            particles.add(mote);
            if (state.debugBirths != null) state.debugBirths.add(particles.tickSerial, position, Kind.MOTE);
            sourcePuffs++;
        }
    }

    private static void emitColosseumSmokePuff(EmitterFrame frame, SourceState state, float strength,
                                               ParticleRuntime particles, ShadowAuraStyleProfile profile,
                                               boolean infill) {
        ColosseumAuraVolume volume = state.colosseumVolume;
        float largeWeight = volume.largeModelWeight();
        // Small models retain their exact sampling and random sequence. Larger
        // footprints receive jittered, shuffled coverage across inner/outer cells.
        Vec3 position = largeWeight > 0 ? state.colosseumSmokeSequence.next(volume)
                : volume.sampleSmokeBase(particles.random.nextDouble(),
                        particles.random.nextDouble(), particles.random.nextDouble());
        float local = volume.puffScale();
        double angle = particles.random.nextDouble() * Mth.TWO_PI;
        double drift = local * (.004 + particles.random.nextDouble() * .010);
        float size = local * 1.5625f * (.70f + particles.random.nextFloat() * .54f);
        if (infill) size *= .60f + particles.random.nextFloat() * .15f;
        int lifetime = 34 + particles.random.nextInt(29);
        Puff puff = new Puff(position.x, position.y, position.z,
                Math.cos(angle) * drift + frame.motion.x * .20,
                local * (.0364 + particles.random.nextDouble() * .0308) + frame.motion.y * .12,
                Math.sin(angle) * drift + frame.motion.z * .20,
                size, lifetime, strength * .17f * profile.broadHaze().densityScale(),
                PuffType.BROAD_HAZE, frame.modelSize(), state, particles.random);
        puff.classic = true;
        puff.colosseumPlume = true;
        puff.colosseumInfill = infill;
        puff.plumeLocalScale = local;
        puff.plumeRiseSpeed = local * (.0504f + particles.random.nextFloat() * .0364f);
        puff.plumeDirection = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        // Capture the emitting pose's limits so moving/recalling/changing style
        // cannot move the fade boundary through already detached smoke.
        puff.plumeFadeWeight = largeWeight;
        puff.plumeFadeStart = volume.plumeFadeStart();
        puff.plumeFadeEnd = volume.plumeFadeEnd();
        particles.add(puff);
        if (state.debugBirths != null) state.debugBirths.add(particles.tickSerial, position,
                infill ? Kind.COLOSSEUM_INFILL : Kind.COLOSSEUM_SMOKE);
    }

    private static ClassicRegion mostUndercoveredRegion(List<ClassicRegion> regions, float totalDemand,
                                                         int target, int layer) {
        ClassicRegion selected = null;
        float best = 0.0f;
        for (ClassicRegion region : regions) {
            float desired = target * region.demand / Math.max(0.01f, totalDemand);
            if (layer == 2) desired *= Math.min(1.3f, region.detailEmphasis / Math.max(0.05f, region.emphasis));
            float present = layer == 0 ? region.nearOccupancy : layer == 1 ? region.plumeOccupancy : region.curveOccupancy;
            float deficit = (desired - present) / Math.max(1.0f, desired);
            if (deficit > best) { best = deficit; selected = region; }
        }
        return selected;
    }

    private static ClassicRegion chooseClassicRegion(List<ClassicRegion> regions, float totalDemand, float random) {
        float choice = random * totalDemand;
        for (ClassicRegion region : regions) {
            choice -= region.demand;
            if (choice <= 0) return region;
        }
        return regions.get(regions.size() - 1);
    }

    private static void spawnClassicPuff(SourceState state, BoneAnchor anchor, ClassicRegion region, float strength,
                                         boolean mote, float modelSize, ParticleRuntime particles,
                                         ShadowAuraStyleProfile profile) {
        if (state.style == ShadowAuraStyle.XD_FAITHFUL) {
            spawnXdCloud(state, region, strength, mote, modelSize, particles, profile);
            return;
        }
        boolean xd = state.style == ShadowAuraStyle.XD_FAITHFUL;
        Vec3 normal = region.normal(anchor);
        float local = region.envelopeScale;
        float rootPadding = local * (mote ? 0.22f : xd ? 0.26f : 0.12f);
        Vec3 support = region.root(anchor, rootPadding);
        Vec3 position = support;
        ShadowAuraStyleProfile.PuffTuning tuning = mote ? profile.hotFleck() : profile.broadHaze();
        float size = mote ? Mth.clamp(local * 0.055f, 0.009f, 0.045f)
                : local * (xd ? 0.88f : 0.78f) * (xd
                ? 0.86f + particles.random.nextFloat() * 0.25f
                : 0.65f + particles.random.nextFloat() * 0.55f);
        if (mote && !xd) {
            position = region.root(anchor, local * (0.25f + particles.random.nextFloat() * 0.70f))
                    .add(0, local * particles.random.nextFloat() * 1.2f, 0)
                    .add(randomUnitVector(particles.random).scale(local * 0.16f));
            size = Mth.clamp(local * 0.09f, 0.014f, 0.07f);
        }
        int life = mote ? xd ? 20 + particles.random.nextInt(10) : 30 + particles.random.nextInt(13)
                : xd ? 26 + particles.random.nextInt(13) : 32;
        float density = strength * (mote ? 0.72f : xd ? 0.12f : 0.16f) * tuning.densityScale();
        Puff puff = new Puff(position.x, position.y, position.z,
                normal.x * local * 0.002, local * (mote ? xd ? 0.016 : 0.025 : xd ? 0.009 : 0.005), normal.z * local * 0.002,
                size, life, density, mote ? PuffType.HOT_FLECK : PuffType.BROAD_HAZE,
                modelSize, state, particles.random);
        puff.classic = true;
        puff.classicRegionKey = region.key;
        if (mote && xd) {
            Vec3 side = ClassicAuraWisp.transportTangent(normal, randomUnitVector(particles.random));
            puff.xd += side.x * local * 0.012;
            puff.zd += side.z * local * 0.012;
        }
        if (!mote) {
            puff.classicRootPadding = rootPadding;
            puff.anchorKey = anchor.key();
            puff.lastAnchorPosition = region.root(anchor, puff.classicRootPadding);
        }
        particles.add(puff);
    }

    private static void spawnXdCloud(SourceState state, ClassicRegion region, float strength, boolean flake,
                                      float modelSize, ParticleRuntime particles, ShadowAuraStyleProfile profile) {
        float local = region.envelopeScale;
        boolean lower = !flake && state.species.endsWith(":ledyba");
        Vec3 position = xdCloudPosition(state, region, local * (flake ? 1.35f : 1.0f), particles, lower);
        float size = flake ? Mth.clamp(local * .085f * (.75f + particles.random.nextFloat() * .60f), .018f, .11f)
                : local * 1.05f * (.86f + particles.random.nextFloat() * .28f);
        int lifetime = flake ? 50 + particles.random.nextInt(27) : 40 + particles.random.nextInt(15);
        float density = strength * (flake ? .72f * profile.hotFleck().densityScale()
                : .12f * profile.broadHaze().densityScale());
        Puff puff = new Puff(position.x, position.y, position.z, 0,
                local * (flake ? .006 : .0072) * ClassicAuraWisp.MOTION_SPEED, 0,
                size, lifetime, density, flake ? PuffType.HOT_FLECK : PuffType.BROAD_HAZE,
                modelSize, state, particles.random);
        puff.classic = true;
        puff.classicRegionKey = region.key;
        puff.xdDriftScale = local;
        // Clouds and feathers are born into the surrounding volume. They never
        // acquire an animated-bone attachment or a later release impulse.
        if (flake) {
            puff.xd = Math.cos(puff.rotation) * local * .006 * ClassicAuraWisp.MOTION_SPEED;
            puff.zd = Math.sin(puff.rotation) * local * .006 * ClassicAuraWisp.MOTION_SPEED;
        }
        particles.add(puff);
        if (state.debugBirths != null) state.debugBirths.add(particles.tickSerial, position,
                flake ? Kind.XD_FLAKE : Kind.XD_HAZE);
    }

    private static boolean isXdFlake(Puff puff) {
        return puff.classic && puff.style == ShadowAuraStyle.XD_FAITHFUL && puff.type == PuffType.HOT_FLECK;
    }

    private static void spawnColosseumPlume(SourceState state, BoneAnchor anchor, ClassicRegion region, float strength,
                                             float modelSize, ParticleRuntime particles,
                                             ShadowAuraStyleProfile profile) {
        float local = region.envelopeScale;
        Vec3 normal = region.normal(anchor);
        float rootPadding = local * (0.35f + particles.random.nextFloat() * 0.20f);
        Vec3 position = region.root(anchor, rootPadding).add(0, local * 0.08f, 0);
        float size = local * (1.25f + particles.random.nextFloat() * 0.40f);
        int lifetime = 56 + particles.random.nextInt(17);
        Puff puff = new Puff(position.x, position.y, position.z,
                normal.x * local * 0.004, local * (0.018f + particles.random.nextFloat() * 0.008f), normal.z * local * 0.004,
                size, lifetime, strength * 0.12f * profile.broadHaze().densityScale(), PuffType.BROAD_HAZE,
                modelSize, state, particles.random);
        puff.classic = true;
        puff.classicRegionKey = region.key;
        puff.colosseumPlume = true;
        puff.classicRootPadding = rootPadding;
        puff.plumeLocalScale = local;
        puff.plumeDirection = ClassicAuraWisp.transportTangent(normal,
                new Vec3(Math.cos(puff.rotation), 0, Math.sin(puff.rotation)));
        puff.anchorKey = anchor.key();
        puff.lastAnchorPosition = region.root(anchor, puff.classicRootPadding);
        particles.add(puff);
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    /** A full cycle visits every retained surface site, including the outermost points on moving parts. */
    private static void emitSignatureSurface(EmitterFrame frame, SourceState state,
                                             List<BoneAnchor> sites, float strength, float quality,
                                             ParticleRuntime particles) {
        state.signatureAnchors.clear();
        for (BoneAnchor site : sites) state.signatureAnchors.put(site.key(), site);
        state.surfacePoseTick = particles.tickSerial;
        if (sites.isEmpty() || strength <= 0.001f) return;

        float modelSize = frame.modelSize();
        // The hitbox fallback has no extra sites. Only the larger posed layouts need a higher ceiling.
        int maxBirths = sites.getFirst().source() == AnchorSource.SHELL ? 36 : 40;
        int fullRate = Mth.clamp(Math.round(10f + sites.size() * 0.16f + modelSize * 1.5f), 12, maxBirths);
        float rate = state.performanceBudget.signatureFillCount(fullRate) * adaptiveEmitterScale(quality);
        state.surfaceBirthCarry += rate * Mth.clamp(strength, 0.0f, 1.0f);
        int births = (int) state.surfaceBirthCarry;
        state.surfaceBirthCarry -= births;
        // Compact silhouettes pile several visible surfaces into the same pixels. On larger models,
        // the lower Performance birth rate needs wider, denser puffs to bridge the spaces on a wing.
        float largeModel = smoothstep(1.0f, 3.0f, modelSize);
        boolean performance = state.performanceBudget == AuraPerformanceBudget.PERFORMANCE;
        float widthScale = Mth.lerp(largeModel, 0.80f, performance ? 0.92f : 0.85f);
        float densityScale = performance ? Mth.lerp(largeModel, 0.50f, 0.72f)
                : Mth.lerp(largeModel, 0.42f, 0.52f);
        int cursor = Math.floorMod(state.surfaceEmitterCursor, sites.size());
        for (int i = 0; i < Math.min(births, sites.size()); i++) {
            spawnSurfacePuff(state, sites.get(cursor), modelSize, widthScale, strength * densityScale, particles);
            cursor = (cursor + 1) % sites.size();
        }
        state.surfaceEmitterCursor = cursor;
    }

    private static void spawnSurfacePuff(SourceState state, BoneAnchor site, float modelSize,
                                         float widthScale, float strength, ParticleRuntime particles) {
        RandomSource random = particles.random;
        Vec3 normal = site.normal();
        // Let widely spaced sites on long wings and tails cover a larger patch of skin.
        float size = widthScale * Mth.clamp(0.42f + modelSize * 0.14f + site.localScale() * 0.68f, 0.48f, 1.55f);
        float roll = random.nextFloat();
        PuffType type;
        int lifetime;
        float density;
        if (roll < 0.50f) {
            type = PuffType.BROAD_HAZE;
            lifetime = 42 + random.nextInt(17);
            density = 0.60f;
        } else if (roll < 0.85f) {
            type = PuffType.CORE_HAZE;
            size *= 0.76f;
            lifetime = 34 + random.nextInt(17);
            density = 0.82f;
        } else if (roll < 0.98f) {
            type = PuffType.WISP;
            size *= 0.50f;
            lifetime = 28 + random.nextInt(14);
            density = 0.88f;
        } else {
            type = PuffType.HOT_FLECK;
            size *= 0.16f;
            lifetime = 18 + random.nextInt(12);
            density = 0.67f;
        }
        size *= 0.90f + random.nextFloat() * 0.22f;
        Vec3 origin = site.pos().add(normal.scale(Math.min(0.045, size * 0.045)));
        double drift = 0.011 + random.nextDouble() * 0.014;
        Puff puff = new Puff(origin.x, origin.y, origin.z,
                normal.x * drift + (random.nextFloat() - 0.5) * 0.014,
                normal.y * drift + 0.006 + random.nextFloat() * 0.015,
                normal.z * drift + (random.nextFloat() - 0.5) * 0.014,
                size, lifetime, density * strength * (0.85f + random.nextFloat() * 0.30f),
                type, modelSize, state, random);
        puff.anchorKey = site.key();
        puff.lastAnchorPosition = site.pos();
        puff.signatureFrame = state.signatureFrame;
        // Use 44% of births for broad smoke and 6% for wisps along the flight path.
        boolean suppliesWake = roll < 0.44f || (type == PuffType.WISP && roll < 0.91f);
        if (puff.signatureFrame != null && suppliesWake) {
            puff.wakeReleaseAge = Mth.ceil(lifetime * SIGNATURE_WAKE_RELEASE);
        }
        particles.add(puff);
    }

    private static void followSignatureSurface(Puff puff, long tick) {
        float life = (float) puff.age / puff.lifetime;
        Vec3 next = null;
        if (life < SIGNATURE_SURFACE_RELEASE && tick - puff.source.surfacePoseTick <= 5
                && puff.source.style == puff.style && puff.signatureFrame == puff.source.signatureFrame) {
            BoneAnchor anchor = puff.source.signatureAnchors.get(puff.anchorKey);
            next = anchor != null ? anchor.pos() : puff.source.signatureLayout.followPosition(puff.anchorKey);
        }
        double maxStep = Math.max(0.8, puff.sourceModelSize * 0.85);
        if (next == null || next.distanceToSqr(puff.lastAnchorPosition) > maxStep * maxStep) {
            puff.anchorKey = null;
            return;
        }
        // New smoke follows the animated part. Older smoke gradually separates into a trail.
        float follow = 1.0f - smoothstep(0.18f, SIGNATURE_SURFACE_RELEASE, life);
        puff.x += (next.x - puff.lastAnchorPosition.x) * follow;
        puff.y += (next.y - puff.lastAnchorPosition.y) * follow;
        puff.z += (next.z - puff.lastAnchorPosition.z) * follow;
        puff.lastAnchorPosition = next;
    }

    private static Vec3 randomUnitVector(RandomSource random) {
        double x = random.nextFloat() * 2.0 - 1.0;
        double y = random.nextFloat() * 2.0 - 1.0;
        double z = random.nextFloat() * 2.0 - 1.0;
        Vec3 v = new Vec3(x, y, z);
        if (v.lengthSqr() < 0.0001) {
            return new Vec3(0.0, 1.0, 0.0);
        }
        return v.normalize();
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }


    private static int adaptivePuffLimit() {
        return AuraPerformanceBudget.adaptivePuffLimit(SOURCES.size());
    }

    private static void tickToGameTime(long gameTime) {
        if (lastTickGameTime == Long.MIN_VALUE) {
            lastTickGameTime = gameTime;
            return;
        }

        long delta = gameTime - lastTickGameTime;
        if (delta <= 0) {
            return;
        }

        tickOnce();
        // Classic admission accounts for up to four elapsed ticks. Advance
        // existing smoke and XD curves on the same clock when rendering falls
        // below TPS, instead of admitting faster than particles rise and expire.
        for (int step = 1; step < Math.min(delta, 4); step++) {
            tickOnce(WORLD_PARTICLES, true);
        }
        lastTickGameTime = gameTime;
    }

    private static void tickOnce() {
        tickOnce(WORLD_PARTICLES);
    }

    private static void tickOnce(ParticleRuntime particles) {
        tickOnce(particles, false);
    }

    private static void tickOnce(ParticleRuntime particles, boolean classicOnly) {
        synchronized (particles.active) {
            if (!classicOnly) particles.tickSerial++;
            Iterator<WispParticle> wisps = particles.wisps.iterator();
            while (wisps.hasNext()) {
                WispParticle particle = wisps.next();
                if (classicOnly && particle.style != ShadowAuraStyle.COLOSSEUM
                        && particle.style != ShadowAuraStyle.XD_FAITHFUL) continue;
                particle.wisp.tick(null, null);
                if (!particle.wisp.alive()) wisps.remove();
            }
            Iterator<Puff> it = particles.active.iterator();
            while (it.hasNext()) {
                Puff puff = it.next();
                if (classicOnly && puff.style != ShadowAuraStyle.COLOSSEUM
                        && puff.style != ShadowAuraStyle.XD_FAITHFUL) continue;
                if (puff.age == puff.wakeReleaseAge && puff.signatureFrame == puff.source.signatureFrame
                        && puff.signatureFrame.isTraveling()) {
                    puff.releaseIntoWake(particles.wakeScratch);
                }
                puff.xo = puff.x;
                puff.yo = puff.y;
                puff.zo = puff.z;
                puff.xdo = puff.xd;
                puff.ydo = puff.yd;
                puff.zdo = puff.zd;

                if (puff.anchorKey != null && puff.style == ShadowAuraStyle.SIGNATURE) {
                    followSignatureSurface(puff, particles.tickSerial);
                } else if (puff.anchorKey != null) {
                    BoneAnchor anchor = puff.source.style == puff.style
                            && particles.tickSerial - puff.source.classicAnchorTick <= 6
                            && puff.age < puff.lifetime * (puff.colosseumPlume ? 0.34f : 0.55f)
                            ? puff.source.classicAnchors.get(puff.anchorKey) : null;
                    ClassicRegion region = puff.source.classicRegions.get(puff.classicRegionKey);
                    Vec3 root = anchor == null || region == null ? null : region.root(anchor, puff.classicRootPadding);
                    if (root != null && root.distanceToSqr(puff.lastAnchorPosition)
                            < Math.pow(Math.max(0.65f, region.envelopeScale * 2.5f), 2.0)) {
                        Vec3 delta = root.subtract(puff.lastAnchorPosition);
                        puff.x += delta.x;
                        puff.y += delta.y;
                        puff.z += delta.z;
                        puff.lastAnchorPosition = root;
                    } else puff.anchorKey = null;
                }

                puff.x += puff.xd;
                puff.y += puff.yd;
                puff.z += puff.zd;

        switch (puff.type) {
                    case BROAD_HAZE -> {
                        if (puff.colosseumPlume) {
                            if (puff.plumeRiseSpeed > 0) {
                                // Independent eddies accompany buoyant rise;
                                // no shared world-time oscillation moves the field together.
                                double phase = puff.rotation;
                                double bend = puff.plumeLocalScale * .0012;
                                puff.xd = puff.xd * .95 + Math.sin(puff.age * (.075 + puff.lodRoll * .03) + phase) * bend;
                                puff.zd = puff.zd * .95 + Math.cos(puff.age * (.061 + puff.lodRoll * .04) + phase * 1.7) * bend;
                                puff.yd += (puff.plumeRiseSpeed - puff.yd) * .055;
                            } else {
                                double bend = Math.sin(puff.age * 0.09 + puff.rotation) * puff.plumeLocalScale * 0.00030;
                                puff.xd = puff.xd * 0.97 + puff.plumeDirection.x * bend;
                                puff.yd *= 0.994;
                                puff.zd = puff.zd * 0.97 + puff.plumeDirection.z * bend;
                            }
                        } else {
                            double horizontalRetention = puff.style == ShadowAuraStyle.SIGNATURE ? 0.93 : 0.89;
                            puff.xd *= horizontalRetention;
                            puff.yd *= 0.982;
                            puff.zd *= horizontalRetention;
                        }
                    }
                    case CORE_HAZE -> {
                        puff.xd *= 0.91;
                        puff.yd *= 0.985;
                        puff.zd *= 0.91;
                    }
                    case WISP -> {
                        puff.xd *= 0.93;
                        puff.yd *= 0.985;
                        puff.yd += 0.0030;
                        puff.zd *= 0.93;
                    }
                    case HOT_FLECK -> {
                        if (isXdFlake(puff)) {
                            double phase = puff.rotation, scale = puff.xdDriftScale * ClassicAuraWisp.MOTION_SPEED;
                            double motionAge = puff.age * ClassicAuraWisp.MOTION_SPEED;
                            double targetX = (Math.cos(phase) * .005 + Math.sin(motionAge * .075 + phase) * .012) * scale;
                            double targetZ = (Math.sin(phase) * .005 + Math.cos(motionAge * .061 + phase) * .012) * scale;
                            puff.xd += (targetX - puff.xd) * .12;
                            puff.zd += (targetZ - puff.zd) * .12;
                            puff.yd += (scale * (.006 + .002 * Math.sin(motionAge * .045 + phase)) - puff.yd) * .10;
                        } else {
                            puff.xd *= 0.96;
                            puff.yd *= 0.990;
                            puff.yd += puff.classic ? 0.00012 : 0.0015;
                            puff.zd *= 0.96;
                        }
                    }
                    case FILAMENT -> {
                        puff.xd *= 0.94;
                        puff.yd *= 0.988;
                        puff.yd += 0.0010;
                        puff.zd *= 0.94;
                    }
                }

                puff.age++;
                if (puff.age >= puff.lifetime) {
                    it.remove();
                }
            }
        }
    }

    private static List<WorldPixelContributor> renderDensitySplats(
            Camera camera,
            float partialTicks,
            ShadowAuraRenderBatch batch) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);

        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        // Use the installed world transform for both GPU drawing and CPU culling.
        // Reconstructing camera rotation here drops Iris' walking/hurt bobbing.
        Matrix4f viewProjection = new Matrix4f(projection).mul(savedModelView);

        ShadowAuraStyle safeStyle = batch.style();
        ShadowAuraStyleProfile.RenderTuning renderTuning =
                ShadowAuraStyleProfiles.forStyle(safeStyle).render();
        var shader = ShadowPokemonAuraFBO.worldDensityShader(safeStyle);
        var filamentShader = safeStyle == ShadowAuraStyle.XD_FAITHFUL
                ? ShadowPokemonAuraFBO.filamentDensityShader(false)
                : null;
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, DENSITY_TEXTURE);

        float gameTime = 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            gameTime = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        }
        com.mojang.blaze3d.shaders.Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) {
            uGameTime.set(gameTime / 1200.0f);
        }
        Uniform auraTime = shader.getUniform("AuraTime");
        if (auraTime != null) auraTime.set(gameTime / 1200.0f);

        Vec3 camPos = camera.getPosition();
        com.mojang.blaze3d.shaders.Uniform uCameraPos = shader.getUniform("CameraPos");
        if (uCameraPos != null) {
            uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        }
        com.mojang.blaze3d.shaders.Uniform uNoiseScale = shader.getUniform("AuraNoiseScale");
        if (uNoiseScale != null) {
            uNoiseScale.set(1.0f);
        }
        com.mojang.blaze3d.shaders.Uniform uMaskFalloff =
                shader.getUniform("AuraMaskFalloff");
        if (uMaskFalloff != null) {
            uMaskFalloff.set(4.2f);
        }

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        ByteBufferBuilder filamentAllocator = filamentShader == null
                ? null
                : new ByteBufferBuilder(262144);
        BufferBuilder filamentBuf = filamentShader == null
                ? null
                : new BufferBuilder(
                        filamentAllocator,
                        VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.PARTICLE);
        PoseStack stack = new PoseStack();
        Quaternionf camOrientation = camera.rotation();
        Vector3f cameraRight = camOrientation.transform(new Vector3f(1, 0, 0));
        Vector3f cameraUp = camOrientation.transform(new Vector3f(0, 1, 0));
        Vector3d worldPosition = new Vector3d();
        Vector3d velocity = new Vector3d();
        Matrix4f puffClipTransform = new Matrix4f();
        Vector4f clipCorner0 = new Vector4f();
        Vector4f clipCorner1 = new Vector4f();
        Vector4f clipCorner2 = new Vector4f();
        Vector4f clipCorner3 = new Vector4f();
        List<WorldPixelContributor> visiblePixelContributors =
                new ArrayList<>();
        float[] savedShaderColor = RenderSystem.getShaderColor().clone();

        try {
            // Vertex RGBA stores density channels, so incoming model tint must not
            // alter the buffer's data. The caller's color is restored below.
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            synchronized (ACTIVE) {
                for (Puff puff : ACTIVE) {
                if (puff.style != safeStyle || renderBatch(puff.style, puff.source).modeTint() != batch.modeTint()
                        || isXdFlake(puff)) {
                    continue;
                }
                float alpha = sampleAlpha(puff, partialTicks);
                if (alpha <= 0.001f) continue;

                float size = sampleSize(puff, partialTicks);
                if (size <= 0.001f) continue;

                puff.renderPosition(partialTicks, worldPosition);
                double rx = worldPosition.x - camPos.x;
                double ry = worldPosition.y - camPos.y;
                double rz = worldPosition.z - camPos.z;
                double distance = Math.sqrt(rx * rx + ry * ry + rz * rz);
                float renderQuality = puffRenderQuality(distance);
                if (shouldSkipPuffRender(puff, renderQuality)) {
                    continue;
                }
                alpha *= 0.78f + renderQuality * 0.22f;
                size *= 0.92f + renderQuality * 0.08f;

                stack.pushPose();
                stack.translate(rx, ry, rz);
                stack.mulPose(camOrientation);

                puff.renderVelocity(partialTicks, velocity);
                double speed = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
                float halfWidth = size;
                float halfHeight = size;

                if (puff.type == PuffType.HOT_FLECK) {
                    stack.mulPose(com.mojang.math.Axis.ZP.rotation(puff.rotation));
                    stack.scale(size, size, 1.0f);
                } else {
                    PuffStretch stretch = samplePuffStretch(puff.type, speed);

                    float velAngle = puff.rotation;
                    if (speed > 0.0005) {
                        float dotR = (float) (velocity.x * cameraRight.x + velocity.y * cameraRight.y + velocity.z * cameraRight.z);
                        float dotU = (float) (velocity.x * cameraUp.x + velocity.y * cameraUp.y + velocity.z * cameraUp.z);
                        velAngle = (float) Math.atan2(dotU, dotR);
                    }
                    float speedBlend = (float) Math.min(speed / 0.005, 1.0);
                    float angle = puff.rotation + speedBlend * wrapAngle(velAngle - puff.rotation);
                    stack.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
                    halfWidth = size * stretch.majorScale();
                    halfHeight = size * stretch.minorScale();
                    stack.scale(
                            halfWidth,
                            halfHeight,
                            1.0f
                    );
                }

                Matrix4f pose = stack.last().pose();
                puffClipTransform.set(viewProjection).mul(pose);
                puffClipTransform.transform(
                        clipCorner0.set(-1.0f, -1.0f, 0.0f, 1.0f)
                );
                puffClipTransform.transform(
                        clipCorner1.set(1.0f, -1.0f, 0.0f, 1.0f)
                );
                puffClipTransform.transform(
                        clipCorner2.set(1.0f, 1.0f, 0.0f, 1.0f)
                );
                puffClipTransform.transform(
                        clipCorner3.set(-1.0f, 1.0f, 0.0f, 1.0f)
                );
                if (projectedPuffOverlapsViewport(
                        clipCorner0,
                        clipCorner1,
                        clipCorner2,
                        clipCorner3
                )) {
                    double pixelDistance = sourceCameraDistance(
                            puff.source,
                            camPos,
                            distance
                    );
                    if (Double.isFinite(pixelDistance)) {
                        includeWorldPixelContributor(
                                visiblePixelContributors,
                                puff.source,
                                pixelDistance
                        );
                    }
                }

                float broadWeight = switch (puff.type) {
                    case BROAD_HAZE -> 1.00f;
                    case CORE_HAZE -> 0.72f;
                    case WISP -> 0.10f;
                    case HOT_FLECK -> puff.style == ShadowAuraStyle.COLOSSEUM && puff.lodRoll > 0.95f ? 1.00f : 0.00f;
                    case FILAMENT -> 0.00f;
                };
                float sparkWeight = switch (puff.type) {
                    case BROAD_HAZE -> 0.00f;
                    case CORE_HAZE -> 0.06f;
                    case WISP -> 0.18f;
                    case HOT_FLECK -> 1.00f;
                    case FILAMENT -> 1.00f;
                };
                if (puff.style == ShadowAuraStyle.COLOSSEUM && puff.type != PuffType.HOT_FLECK) {
                    sparkWeight = ColosseumSmokeAppearance.ageChannel(puff.age + partialTicks, puff.lifetime);
                }
                float wispWeight = sampleWispWeight(puff, partialTicks);

                broadWeight *= renderTuning.broadChannelScale();
                sparkWeight = puffGreenChannel(puff.style, sparkWeight, renderTuning.heatChannelScale());
                wispWeight *= renderTuning.wispChannelScale();
                alpha *= renderTuning.coverageScale()
                        * renderTuning.opacityScale();

                if (safeStyle != ShadowAuraStyle.SIGNATURE) {
                    // PARTICLE colors are normalized bytes; values above one wrap.
                    broadWeight = Mth.clamp(broadWeight, 0, 1);
                    sparkWeight = Mth.clamp(sparkWeight, 0, 1);
                    wispWeight = Mth.clamp(wispWeight, 0, 1);
                    alpha = Mth.clamp(alpha, 0, 1);
                }

                BufferBuilder target = puff.type == PuffType.FILAMENT
                        && filamentBuf != null
                        ? filamentBuf
                        : buf;
                if (target == filamentBuf) {
                    float seedOffset = fract(puff.rotation / Mth.TWO_PI);
                    float phaseOffset = puff.lodRoll;
                    addPuffVertices(
                            target,
                            pose,
                            seedOffset,
                            phaseOffset,
                            renderTuning.wispChannelScale(),
                            alpha);
                } else {
                    addPuffVertices(
                            target,
                            pose,
                            broadWeight,
                            sparkWeight,
                            wispWeight,
                            alpha);
                }

                    stack.popPose();
                }
            }

            if (filamentBuf != null) {
                renderClassicRibbons(WORLD_PARTICLES, safeStyle, filamentBuf, partialTicks,
                        camPos, new Matrix4f().rotation(camOrientation),
                        1.0f, false, batch.modeTint(),
                        ShadowPokemonAuraFBO.pixelationStyle(safeStyle) == AuraPixelationStyle.SMOOTH
                                ? null : visiblePixelContributors, viewProjection);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            if (filamentBuf != null) {
                MeshData filamentMesh = filamentBuf.build();
                if (filamentMesh != null) {
                    setupFilamentDensityShader(
                            filamentShader,
                            gameTime,
                            false);
                    BufferUploader.drawWithShader(filamentMesh);
                }
            }
        } finally {
            if (filamentAllocator != null) {
                filamentAllocator.close();
            }
            RenderSystem.getModelViewMatrix().set(savedModelView);
            RenderSystem.setShaderColor(savedShaderColor[0], savedShaderColor[1], savedShaderColor[2], savedShaderColor[3]);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        return visiblePixelContributors;
    }

    private static void addPuffVertices(BufferBuilder buffer,
                                        Matrix4f pose,
                                        float red,
                                        float green,
                                        float blue,
                                        float alpha) {
        buffer.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f)
                .setColor(red, green, blue, alpha).setLight(FULLBRIGHT);
        buffer.addVertex(pose, 1f, -1f, 0f).setUv(1f, 1f)
                .setColor(red, green, blue, alpha).setLight(FULLBRIGHT);
        buffer.addVertex(pose, 1f, 1f, 0f).setUv(1f, 0f)
                .setColor(red, green, blue, alpha).setLight(FULLBRIGHT);
        buffer.addVertex(pose, -1f, 1f, 0f).setUv(0f, 0f)
                .setColor(red, green, blue, alpha).setLight(FULLBRIGHT);
    }

    static void setupFilamentDensityShader(ShaderInstance shader,
                                           float animationTicks,
                                           boolean gui) {
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, DENSITY_TEXTURE);
        Uniform time = shader.getUniform("AuraTime");
        if (time != null) {
            time.set(animationTicks / 1200.0f);
        }
        Uniform seed = shader.getUniform("FilamentSeed");
        if (seed != null) {
            seed.set(0.0f);
        }
        Uniform phase = shader.getUniform("FilamentPhase");
        if (phase != null) {
            phase.set(0.0f);
        }
    }

    private static double sourceCameraDistance(
            SourceState source,
            Vec3 cameraPosition,
            double fallbackDistance) {
        if (source == null || cameraPosition == null) {
            return fallbackDistance;
        }
        double dx = source.pixelCenterX - cameraPosition.x;
        double dy = source.pixelCenterY - cameraPosition.y;
        double dz = source.pixelCenterZ - cameraPosition.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void includeWorldPixelContributor(
            List<WorldPixelContributor> contributors,
            SourceState source,
            double cameraDistance) {
        if (source == null || !Double.isFinite(cameraDistance)) {
            return;
        }
        for (WorldPixelContributor contributor : contributors) {
            if (contributor.source == source) {
                contributor.cameraDistance = Math.max(
                        contributor.cameraDistance,
                        cameraDistance
                );
                return;
            }
        }
        contributors.add(new WorldPixelContributor(
                source,
                cameraDistance
        ));
    }

    private static boolean shouldSkipPuffRender(Puff puff, float renderQuality) {
        if (renderQuality >= 0.92f) {
            return false;
        }

        return switch (puff.type) {
            case HOT_FLECK -> renderQuality < 0.88f && puff.lodRoll > renderQuality * 0.42f;
            case WISP -> renderQuality < 0.68f && puff.lodRoll > renderQuality * 1.05f;
            case FILAMENT -> renderQuality < 0.54f && puff.lodRoll > renderQuality * 1.12f;
            case CORE_HAZE -> renderQuality < 0.46f && puff.lodRoll > renderQuality + 0.25f;
            case BROAD_HAZE -> false;
        };
    }

    private static PuffStretch samplePuffStretch(PuffType type, double speed) {
        float safeSpeed = Double.isFinite(speed) ? Math.max(0.0f, (float) speed) : 0.0f;
        return switch (type) {
            case BROAD_HAZE -> areaPreservingStretch(
                    Math.min(1.0f + safeSpeed * 3.0f, 1.12f)
            );
            case CORE_HAZE -> areaPreservingStretch(
                    Math.min(1.0f + safeSpeed * 5.0f, 1.20f)
            );
            case WISP -> {
                float stretch = Math.min(1.0f + safeSpeed * 16.0f, 1.65f);
                yield new PuffStretch(
                        stretch,
                        1.0f / Math.max(stretch * 0.62f, 1.0f)
                );
            }
            case HOT_FLECK, FILAMENT -> PuffStretch.NONE;
        };
    }

    private static PuffStretch areaPreservingStretch(float aspect) {
        float axis = (float) Math.sqrt(Math.max(1.0f, aspect));
        return new PuffStretch(axis, 1.0f / axis);
    }

    private static float sampleWispWeight(Puff puff, float partialTicks) {
        return switch (puff.type) {
            // Let the violet detail emerge as the haze drifts into the wake.
            case BROAD_HAZE -> puff.inWake() ? 0.05f + 0.80f * puff.wakeBlend(partialTicks) : 0.05f;
            case CORE_HAZE, HOT_FLECK -> 0.25f;
            case WISP, FILAMENT -> 1.00f;
        };
    }

    private static float sampleSize(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        if (puff.classic && puff.style == ShadowAuraStyle.XD_FAITHFUL) {
            if (puff.type == PuffType.BROAD_HAZE) return XdAuraFade.hazeSize(puff.baseSize, puff.age + partialTicks, t);
            if (isXdFlake(puff)) return XdAuraFade.flakeSize(puff.baseSize, t);
        }
        return switch (puff.type) {
            case BROAD_HAZE -> {
                float grow = smoothstep(0.0f, 0.22f, t);
                float lateShrink = 1.0f - 0.12f * smoothstep(0.84f, 1.0f, t);
                float initialScale = puff.style == ShadowAuraStyle.COLOSSEUM || puff.style == ShadowAuraStyle.XD_FAITHFUL ? 0.48f : 0.28f;
                yield puff.baseSize * Mth.lerp(grow, initialScale, 0.86f) * lateShrink;
            }
            case CORE_HAZE -> {
                float grow = smoothstep(0.0f, 0.18f, t);
                float lateShrink = 1.0f - 0.10f * smoothstep(0.84f, 1.0f, t);
                yield puff.baseSize * Mth.lerp(grow, 0.24f, 0.99f) * lateShrink;
            }
            case WISP -> {
                float grow = Math.min(t * 8.0f, 1.0f);
                float fade = 1.0f - smoothstep(0.55f, 1.0f, t);
                yield puff.baseSize * (0.08f + 0.92f * grow) * fade;
            }
            case HOT_FLECK -> {
                float grow = Math.min(t * 12.0f, 1.0f);
                float fade = 1.0f - smoothstep(0.72f, 1.0f, t);
                yield puff.baseSize * grow * fade;
            }
            case FILAMENT -> {
                float grow = smoothstep(0.0f, 0.12f, t);
                float fade = 1.0f - smoothstep(0.70f, 1.0f, t);
                yield puff.baseSize * Mth.lerp(grow, 0.72f, 1.0f) * fade;
            }
        };
    }

    /** Low G is per-puff age, while G >= .75 remains reserved for Colosseum preview motes. */
    public static float colosseumSmokeAgeChannel(float ageTicks, float lifetimeTicks) {
        return ColosseumSmokeAppearance.ageChannel(ageTicks, lifetimeTicks);
    }

    /** Color has time to turn purple before the entire puff loses visibility. */
    public static float colosseumSmokeOpacity(float ageTicks, float lifetimeTicks) {
        return ColosseumSmokeAppearance.opacity(ageTicks, lifetimeTicks);
    }

    /** Transport lifecycle bits unchanged; only actual heat/core channels use profile scaling. */
    public static float puffGreenChannel(ShadowAuraStyle style, float rawGreen, float heatScale) {
        return style == ShadowAuraStyle.COLOSSEUM && rawGreen <= 0.5f ? rawGreen : rawGreen * heatScale;
    }

    private static float sampleAlpha(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        if (puff.classic && puff.style == ShadowAuraStyle.XD_FAITHFUL
                && (puff.type == PuffType.BROAD_HAZE || isXdFlake(puff))) {
            return isXdFlake(puff)
                    ? XdAuraFade.flakeOpacity(puff.baseDensity, puff.age + partialTicks, t)
                    : XdAuraFade.hazeOpacity(puff.baseDensity, puff.age + partialTicks, t);
        }
        float alpha = switch (puff.type) {
            case BROAD_HAZE -> {
                if (puff.style == ShadowAuraStyle.COLOSSEUM) {
                    yield ColosseumSmokeAppearance.opacity(puff.age + partialTicks, puff.lifetime) * puff.baseDensity;
                }
                float fadeIn = smoothstep(0.0f, 0.18f, t);
                if (puff.inWake()) {
                    float density = puff.baseDensity * (1.0f + 0.25f * puff.wakeBlend(partialTicks));
                    yield fadeIn * (1.0f - smoothstep(0.68f, 1.0f, t)) * density;
                }
                float fadeOut = 1.0f - smoothstep(0.60f, 0.98f, t);
                yield fadeIn * fadeOut * puff.baseDensity;
            }
            case CORE_HAZE -> {
                float fadeIn = smoothstep(0.0f, 0.14f, t);
                float fadeOut = 1.0f - smoothstep(0.64f, 0.98f, t);
                yield fadeIn * fadeOut * puff.baseDensity;
            }
            case WISP -> {
                float fadeIn = Math.min(1.0f, t / 0.18f);
                float fadeOut = 1.0f - smoothstep(0.74f, 1.0f, t);
                yield Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity;
            }
            case HOT_FLECK -> {
                float fadeIn = Math.min(1.0f, t / 0.10f);
                float fadeOut = 1.0f - smoothstep(0.55f, 1.0f, t);
                float twinkle = 0.76f + 0.24f * (float) Math.sin(puff.age * 0.85f + puff.rotation * 8.0f);
                yield Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity * twinkle;
            }
            case FILAMENT -> {
                float fadeIn = smoothstep(0.0f, 0.12f, t);
                float fadeOut = 1.0f - smoothstep(0.68f, 1.0f, t);
                float pulse = 0.84f + 0.16f
                        * (float) Math.sin(puff.age * 0.42f + puff.rotation * 5.0f);
                yield fadeIn * fadeOut * puff.baseDensity * pulse;
            }
        };
        if (puff.style == ShadowAuraStyle.XD_FAITHFUL && !puff.classic) {
            alpha *= XdAuraFade.burstEnvelope(puff.age + partialTicks, puff.lifetime);
        }
        if (puff.colosseumPlume && puff.plumeFadeWeight > 0) {
            // Subtract the world-space origin before casting to float so the
            // soft upper fade stays smooth at distant map coordinates.
            double height = Mth.lerp(partialTicks, puff.yo, puff.y) - puff.plumeFadeStart;
            float above = (float) (height / Math.max(.001, puff.plumeFadeEnd - puff.plumeFadeStart));
            alpha *= 1 - puff.plumeFadeWeight * smoothstep(0, 1, above);
        }
        return alpha;
    }

    private static float wrapAngle(float a) {
        while (a > (float) Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -(float) Math.PI) a += (float) (Math.PI * 2.0);
        return a;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private enum PuffType {
        BROAD_HAZE,
        CORE_HAZE,
        WISP,
        HOT_FLECK,
        FILAMENT
    }

    private record PuffStretch(float majorScale, float minorScale) {
        private static final PuffStretch NONE = new PuffStretch(1.0f, 1.0f);
    }

    private static final class WorldPixelContributor {
        final SourceState source;
        double cameraDistance;

        WorldPixelContributor(
                SourceState source,
                double cameraDistance) {
            this.source = source;
            this.cameraDistance = cameraDistance;
        }
    }

    private static ShadowAuraRenderBatch renderBatch(ShadowAuraStyle style, SourceState source) {
        return ShadowAuraRenderBatch.of(style, source != null && source.modeTint);
    }

    private static final class SourceState {
        AuraEmitterDebug.Births debugBirths;
        final SignatureSurfaceLayout signatureLayout = new SignatureSurfaceLayout();
        SignatureAuraFrame signatureFrame;

        void startSignatureFrame(Vec3 base, long gameTime) {
            signatureFrame = new SignatureAuraFrame(base, gameTime);
            signatureAnchors.clear();
            signatureLayout.clearFollowPose();
        }

        void updateSignatureLocation(Vec3 base, long gameTime) {
            if (signatureFrame == null || !signatureFrame.canContinue(base, gameTime)) {
                startSignatureFrame(base, gameTime);
            } else {
                signatureFrame.move(base, gameTime);
            }
        }

        void updateSignaturePose(Vec3 base, Vec3 camera, Matrix4f root, long gameTime) {
            updateSignatureLocation(base, gameTime);
            // Shell puffs were born before a drawn model supplied its pivot.
            if (!signatureFrame.isPosed() && !signatureAnchors.isEmpty()) startSignatureFrame(base, gameTime);
            signatureFrame.pose(camera, root);
        }

        ShadowAuraStyle style = ShadowAuraStyle.DEFAULT;
        boolean modeTint;
        ColosseumAuraVolume colosseumVolume;
        double colosseumBirthCarry;
        double colosseumInfillCarry;
        double colosseumAccentCarry;
        ColosseumSmokeSpawnSequence colosseumSmokeSequence;
        String species = "";
        int classicEmissionTick;
        double xdCurveBirthCarry;
        double xdFlakeBirthCarry;
        double xdHazeBirthCarry;
        long classicAnchorTick;
        final Map<Object, BoneAnchor> classicAnchors = new HashMap<>();
        final Map<Object, ClassicPartGeometry> classicGeometry = new LinkedHashMap<>();
        final List<XdAuraSurfaceSampler.Cube> xdSurfaceCubes = new ArrayList<>();
        final Map<Object, ClassicRegion> classicRegions = new LinkedHashMap<>();
        int lastClassicFrameTick = Integer.MIN_VALUE;
        double pixelCenterX;
        double pixelCenterY;
        double pixelCenterZ;
        double pixelModelSize = 1.0;
        long lastEmitTick = Long.MIN_VALUE;
        long lastBoneEmitTick = Long.MIN_VALUE;
        long lastBoneTick = Long.MIN_VALUE;
        long lastSeenTick = Long.MIN_VALUE;
        int surfaceEmitterCursor;
        double surfaceBirthCarry;
        long surfacePoseTick;
        final Map<Object, BoneAnchor> signatureAnchors = new HashMap<>();
        float fade = 1.0f;
        float corruption = 1.0f;
        float quality = 1.0f;
        AuraPerformanceBudget performanceBudget = AuraPerformanceBudget.QUALITY;
        float stableWorldPixelLod = maximumWorldPixelLod(
                ShadowPokemonAuraFBO.maximumWorldPixelSize());
        final float seed;

        SourceState(double x, double y, double z) {
            this(x, y, z, RANDOM);
        }

        SourceState(double x, double y, double z, RandomSource random) {
            this.pixelCenterX = x;
            this.pixelCenterY = y;
            this.pixelCenterZ = z;
            this.seed = random.nextFloat() * Mth.TWO_PI;
        }
    }

    private record EmitterFrame(Vec3 base,
                                Vec3 motion,
                                float width,
                                float height,
                                int tickCount,
                                float partialTicks) {
        static EmitterFrame fromEntity(PokemonEntity entity, float partialTicks) {
            return new EmitterFrame(
                    new Vec3(
                            Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                            Mth.lerp(partialTicks, entity.yOld, entity.getY()),
                            Mth.lerp(partialTicks, entity.zOld, entity.getZ())
                    ),
                    entity.getDeltaMovement(),
                    entity.getBbWidth(),
                    entity.getBbHeight(),
                    entity.tickCount,
                    partialTicks
            );
        }

        Vec3 center(float heightFraction) {
            return base.add(0.0, height * heightFraction, 0.0);
        }

        float modelSize() {
            return Math.max(width, height);
        }
    }

    private static final class ParticleRuntime {
        final RandomSource random;
        final List<Puff> active;
        final IntSupplier limit;
        final List<WispParticle> wisps = new ArrayList<>();
        final Vector3d wakeScratch = new Vector3d();
        long tickSerial;

        ParticleRuntime(RandomSource random, List<Puff> active, IntSupplier limit) {
            this.random = random;
            this.active = active;
            this.limit = limit;
        }

        void add(Puff puff) {
            synchronized (active) {
                int maxPuffs = Math.max(1, limit.getAsInt());
                if (active.size() >= maxPuffs) {
                    int overflow = active.size() - maxPuffs + 1;
                    active.subList(0, Math.min(overflow, active.size())).clear();
                }
                active.add(puff);
            }
        }

        void clear() {
            synchronized (active) {
                active.clear();
                wisps.clear();
            }
        }
    }

    /** Camera-facing cellular patches; the simulated path supplies their moving centers. */
    private static void renderClassicRibbons(ParticleRuntime particles, ShadowAuraStyle style,
                                             BufferBuilder buffer, float partial, Vec3 camera,
                                             Matrix4f transform, float widthScale, boolean gui) {
        renderClassicRibbons(particles, style, buffer, partial, camera, transform, widthScale, gui, null);
    }

    private static void renderClassicRibbons(ParticleRuntime particles, ShadowAuraStyle style,
                                             BufferBuilder buffer, float partial, Vec3 camera,
                                             Matrix4f transform, float widthScale, boolean gui, Boolean modeTint) {
        renderClassicRibbons(particles, style, buffer, partial, camera, transform, widthScale, gui, modeTint, null, null);
    }

    private static void renderClassicRibbons(ParticleRuntime particles, ShadowAuraStyle style,
                                             BufferBuilder buffer, float partial, Vec3 camera,
                                             Matrix4f transform, float widthScale, boolean gui, Boolean modeTint,
                                             List<WorldPixelContributor> pixelContributors, Matrix4f clipTransform) {
        ShadowAuraStyleProfile.RenderTuning tuning = ShadowAuraStyleProfiles.forStyle(style).render();
        synchronized (particles.active) {
            for (WispParticle particle : particles.wisps) {
                if (particle.style != style || (modeTint != null
                        && renderBatch(particle.style, particle.source).modeTint() != modeTint)) continue;
                ClassicAuraWisp wisp = particle.wisp;
                float alpha = wisp.opacity(partial) * tuning.coverageScale() * tuning.opacityScale() * tuning.wispChannelScale();
                if (alpha <= 0.001f) continue;
                Vec3[] corners = xdBillboardCorners(wisp, partial, camera, transform, widthScale, gui);
                includeRibbonPixelContributor(pixelContributors, particle.source, corners, clipTransform, camera);
                float seed = wisp.seed();
                float life = wisp.life(partial);
                float role = wisp.materialRole();
                addRibbonVertex(buffer, corners[0], 0, 0, seed, life, role, alpha);
                addRibbonVertex(buffer, corners[1], 1, 0, seed, life, role, alpha);
                addRibbonVertex(buffer, corners[2], 1, 1, seed, life, role, alpha);
                addRibbonVertex(buffer, corners[3], 0, 1, seed, life, role, alpha);
            }
            if (style == ShadowAuraStyle.XD_FAITHFUL) {
                for (Puff flake : particles.active) {
                    if (!isXdFlake(flake) || (modeTint != null
                            && renderBatch(flake.style, flake.source).modeTint() != modeTint)) continue;
                    Vec3 position = new Vec3(Mth.lerp(partial, flake.xo, flake.x),
                            Mth.lerp(partial, flake.yo, flake.y), Mth.lerp(partial, flake.zo, flake.z));
                    if (!gui && shouldSkipPuffRender(flake, puffRenderQuality(position.distanceTo(camera)))) continue;
                    float alpha = sampleAlpha(flake, partial) * tuning.coverageScale() * tuning.opacityScale();
                    double size = sampleSize(flake, partial) * widthScale;
                    if (alpha <= .001 || size <= .001) continue;
                    double time = flake.age + partial;
                    double roll = flake.rotation + Math.sin(time * .065 + flake.rotation) * .7 + time * .028;
                    double flutter = .70 + .30 * Math.abs(Math.cos(time * .075 + flake.rotation));
                    Vec3 center = ribbonRenderPoint(position, camera, transform, gui);
                    Vec3[] corners = xdBillboardQuad(center, transform, gui, size * 1.30 * flutter, size * 1.05, roll);
                    includeRibbonPixelContributor(pixelContributors, flake.source, corners, clipTransform, camera);
                    float life = Mth.clamp((float) time / flake.lifetime, 0, 1);
                    addRibbonVertex(buffer, corners[0], 0, 0, flake.lodRoll, life, 0, alpha);
                    addRibbonVertex(buffer, corners[1], 1, 0, flake.lodRoll, life, 0, alpha);
                    addRibbonVertex(buffer, corners[2], 1, 1, flake.lodRoll, life, 0, alpha);
                    addRibbonVertex(buffer, corners[3], 0, 1, flake.lodRoll, life, 0, alpha);
                }
            }
        }
    }

    /** World transform is camera-to-world rotation; GUI transform maps the center to pixels. */
    private static void includeRibbonPixelContributor(List<WorldPixelContributor> contributors,
                                                       SourceState source, Vec3[] corners,
                                                       Matrix4f clipTransform, Vec3 camera) {
        if (contributors == null || source == null || clipTransform == null) return;
        Vector4f[] projected = new Vector4f[4];
        for (int i = 0; i < 4; i++) {
            Vec3 point = corners[i];
            projected[i] = clipTransform.transform(new Vector4f((float) point.x, (float) point.y, (float) point.z, 1));
        }
        if (projectedPuffOverlapsViewport(projected[0], projected[1], projected[2], projected[3]))
            includeWorldPixelContributor(contributors, source, sourceCameraDistance(source, camera, Double.NaN));
    }

    static Vec3[] xdBillboardCorners(ClassicAuraWisp wisp, float partial, Vec3 camera,
                                     Matrix4f transform, float sizeScale, boolean gui) {
        Vec3 center = ribbonRenderPoint(wisp.point(.5f, partial), camera, transform, gui);
        double halfWidth = wisp.halfWidth(.5f, partial) * 1.28 * sizeScale;
        double halfHeight = halfWidth * (1.10 + wisp.seed() * .10);
        return xdBillboardQuad(center, transform, gui, halfWidth, halfHeight, 0);
    }

    private static Vec3[] xdBillboardQuad(Vec3 center, Matrix4f transform, boolean gui,
                                          double halfWidth, double halfHeight, double roll) {
        Vector3f rightAxis = new Vector3f(1, 0, 0);
        Vector3f upAxis = new Vector3f(0, 1, 0);
        if (!gui) {
            transform.transformDirection(rightAxis).normalize();
            transform.transformDirection(upAxis).normalize();
        }
        // Both dimensions stay in the camera plane even when the attachment is
        // underneath a body or the camera looks along the simulated rise direction.
        Vec3 r = new Vec3(rightAxis.x, rightAxis.y, rightAxis.z);
        Vec3 u = new Vec3(upAxis.x, upAxis.y, upAxis.z);
        Vec3 right = r.scale(Math.cos(roll)).add(u.scale(Math.sin(roll))).scale(halfWidth);
        Vec3 up = u.scale(Math.cos(roll)).subtract(r.scale(Math.sin(roll))).scale(halfHeight);
        return new Vec3[] {center.subtract(right).subtract(up), center.add(right).subtract(up),
                center.add(right).add(up), center.subtract(right).add(up)};
    }

    private static void addRibbonVertex(BufferBuilder buffer, Vec3 point, float u, float v,
                                        float seed, float life, float intensity, float alpha) {
        buffer.addVertex((float) point.x, (float) point.y, (float) point.z).setUv(u, v)
                .setColor(seed, life, Mth.clamp(intensity, 0, 1), Mth.clamp(alpha, 0, 1)).setLight(FULLBRIGHT);
    }

    static Vec3 ribbonRenderPoint(Vec3 point, Vec3 camera, Matrix4f transform, boolean gui) {
        // World positions must lose their large origin in double precision before
        // conversion to packed float vertices; GUI simulation coordinates are local.
        if (!gui) return point.subtract(camera);
        Vector3f p = transform.transformPosition((float) point.x, (float) point.y, (float) point.z, new Vector3f());
        return new Vec3(p.x, p.y, p.z);
    }

    private static final int CLASSIC_PUFFS_PER_SOURCE = 900;
    private static final int CLASSIC_WISPS_GLOBAL = 384;
    private record WispParticle(SourceState source, ShadowAuraStyle style, ClassicAuraWisp wisp,
                                float rootPadding, Object regionKey) {}

    private static final class RenderedAnchorCapture {
        final SignatureSurfaceLayout.Pose signaturePose = new SignatureSurfaceLayout.Pose();
        final Matrix4f signatureToLocal;
        final Matrix4f signaturePartPose;
        final PokemonEntity entity;
        final SourceState state;
        final Vec3 poseToWorldOffset;
        final Vec3 sourceWorld;
        final float strength;
        final float partialTicks;
        final float quality;
        final long gameTime;
        final int anchorBudget;
        final int candidateBudget;
        final int bodyAnchorBudget;
        final int[] bodyAnchorCount = new int[1];
        final List<BoneAnchor> anchorCandidates;
        final Map<Object, ClassicPartGeometry> classicGeometry = new LinkedHashMap<>();
        final Map<ModelPart, List<XdAuraSurfaceSampler.Cube>> xdSurfaceParts = new LinkedHashMap<>();
        final List<RenderedPartNode> renderedPartStack = new ArrayList<>();

        RenderedAnchorCapture(PokemonEntity entity,
                              SourceState state,
                              Vec3 poseToWorldOffset,
                              Vec3 sourceWorld,
                              float strength,
                              float partialTicks,
                              float quality,
                              long gameTime,
                              int anchorBudget,
                              int candidateBudget,
                              int bodyAnchorBudget) {
            this.entity = entity;
            this.state = state;
            this.poseToWorldOffset = poseToWorldOffset;
            this.signatureToLocal = state.signatureFrame == null ? null : state.signatureFrame.captureToLocal(poseToWorldOffset);
            this.signaturePartPose = signatureToLocal == null ? null : new Matrix4f();
            this.sourceWorld = sourceWorld;
            this.strength = strength;
            this.partialTicks = partialTicks;
            this.quality = quality;
            this.gameTime = gameTime;
            this.anchorBudget = anchorBudget;
            this.candidateBudget = candidateBudget;
            this.bodyAnchorBudget = bodyAnchorBudget;
            this.anchorCandidates = new ArrayList<>(candidateBudget);
        }
    }

    private static final class PreviewAnchorCapture {
        final SignatureSurfaceLayout.Pose signaturePose = new SignatureSurfaceLayout.Pose();
        final int generation;
        final Matrix4f inverseRootPose;
        final Matrix4f rawToAura;
        final Matrix4f simulationToGui;
        final float width;
        final float height;
        final int anchorBudget;
        final int candidateBudget;
        final int bodyAnchorBudget;
        final int[] bodyAnchorCount = new int[1];
        final List<BoneAnchor> anchorCandidates;
        final Map<Object, ClassicPartGeometry> classicGeometry = new LinkedHashMap<>();
        final Map<ModelPart, List<XdAuraSurfaceSampler.Cube>> xdSurfaceParts = new LinkedHashMap<>();
        final List<RenderedPartNode> renderedPartStack = new ArrayList<>();
        final PoseStack canonicalStack = new PoseStack();

        PreviewAnchorCapture(int generation,
                             Matrix4f inverseRootPose,
                             Matrix4f rawToAura,
                             Matrix4f simulationToGui,
                             float width,
                             float height,
                             int anchorBudget,
                             int candidateBudget,
                             int bodyAnchorBudget) {
            this.generation = generation;
            this.inverseRootPose = inverseRootPose;
            this.rawToAura = rawToAura;
            this.simulationToGui = simulationToGui;
            this.width = width;
            this.height = height;
            this.anchorBudget = anchorBudget;
            this.candidateBudget = candidateBudget;
            this.bodyAnchorBudget = bodyAnchorBudget;
            this.anchorCandidates = new ArrayList<>(candidateBudget);
        }
    }

    private record RenderedPartNode(ModelPart part, Vec3 geometryWorld, boolean chainable) {}

    private static final class Puff {
        SignatureAuraFrame signatureFrame;
        int wakeReleaseAge = -1;
        double x, y, z;
        double xo, yo, zo;
        double xd, yd, zd;
        double xdo, ydo, zdo;

        final float baseSize;
        final int lifetime;
        final float baseDensity;
        final float rotation;
        final float lodRoll;
        final float sourceModelSize;
        final SourceState source;
        final ShadowAuraStyle style;
        final PuffType type;
        int age;
        boolean classic;
        boolean colosseumPlume;
        boolean colosseumInfill;
        float plumeFadeWeight;
        double plumeFadeStart, plumeFadeEnd;
        float plumeLocalScale;
        float xdDriftScale;
        float plumeRiseSpeed;
        Vec3 plumeDirection;
        Object anchorKey;
        Object classicRegionKey;
        float classicRootPadding;
        Vec3 lastAnchorPosition;

        boolean inWake() {
            return wakeReleaseAge >= 0 && signatureFrame == null;
        }

        float wakeBlend(float partialTicks) {
            // age has advanced by the time we draw the release tick's interpolation interval.
            return smoothstep(0.0f, 5.0f, age - 1 - wakeReleaseAge + partialTicks);
        }

        void releaseIntoWake(Vector3d scratch) {
            // Rebase before the tick saves the interpolation endpoints, so both are in world space.
            signatureFrame.toWorldPosition(scratch.set(x, y, z));
            x = scratch.x;
            y = scratch.y;
            z = scratch.z;
            signatureFrame.toWorldDirection(scratch.set(xd, yd, zd));
            xd = scratch.x;
            yd = scratch.y;
            zd = scratch.z;
            signatureFrame = null;
            anchorKey = null;
            lastAnchorPosition = null;
        }

        void renderPosition(float partial, Vector3d position) {
            position.set(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z));
            if (signatureFrame != null) signatureFrame.toWorldPosition(position);
        }

        void renderVelocity(float partial, Vector3d velocity) {
            velocity.set(Mth.lerp(partial, xdo, xd), Mth.lerp(partial, ydo, yd), Mth.lerp(partial, zdo, zd));
            if (signatureFrame != null) signatureFrame.toWorldDirection(velocity);
        }

        Puff(double x, double y, double z,
             double xd, double yd, double zd,
             float baseSize, int lifetime,
             float baseDensity, PuffType type,
             float sourceModelSize,
             SourceState source) {
            this(
                    x, y, z,
                    xd, yd, zd,
                    baseSize, lifetime,
                    baseDensity, type,
                    sourceModelSize,
                    source,
                    RANDOM
            );
        }

        Puff(double x, double y, double z,
             double xd, double yd, double zd,
             float baseSize, int lifetime,
             float baseDensity, PuffType type,
             float sourceModelSize,
             SourceState source,
             RandomSource random) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.xdo = xd;
            this.ydo = yd;
            this.zdo = zd;
            this.baseSize = baseSize;
            this.lifetime = lifetime;
            this.baseDensity = baseDensity;
            this.type = type;
            this.sourceModelSize = Float.isFinite(sourceModelSize)
                    ? Math.max(MIN_PIXEL_MODEL_SIZE, sourceModelSize)
                    : 1.0f;
            this.source = source;
            this.style = source == null || source.style == null
                    ? ShadowAuraStyle.DEFAULT
                    : source.style;
            this.rotation = random.nextFloat() * Mth.TWO_PI;
            this.lodRoll = random.nextFloat();
        }
    }
}
