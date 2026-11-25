package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.AnchorSource;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.BoneAnchor;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.AnchorRole;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraSurfaceSampler;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraSurfaceSampler.Face;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A reusable, camera-independent layout on the exterior of a posed model. */
final class SignatureSurfaceLayout {
    private static final int GRID = 24;
    private static final int CELLS = GRID * GRID;
    private static final int MIN_BASE_SITES = 48;
    private static final int MAX_BASE_SITES = 128;
    private static final Vec3[] SHELL = fallbackDirections();
    // Multiple entities can render the same baked model. A cached layout itself references the parts,
    // so both sides of this render-thread cache must be weak across model/resource reload.
    private static final Map<ModelPart, WeakReference<Layout>> SHARED = new WeakHashMap<>();

    /** Keep a few recurring layouts for alternate visible geometry such as eyes or folded wings. */
    private Layout current;
    private final List<Layout> recent = new ArrayList<>(3);
    private int poseCheck;
    private Pose followPose;
    private Vec3 followOffset = Vec3.ZERO;
    private final Map<ModelPart, PartPose> followParts = new IdentityHashMap<>();
    private final Map<SurfacePoint, Vec3> followPositions = new HashMap<>();

    static final class Pose {
        private final List<PartPose> parts = new ArrayList<>();

        void add(ModelPart part, Matrix4f transform) {
            if (!part.visible || part.skipDraw || part.cubes.isEmpty() || !transform.isFinite()) return;
            Matrix3f normal = new Matrix3f(transform);
            float determinant = normal.determinant();
            if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0e-12f) return;
            float scale = (float)Math.cbrt(Math.abs(determinant));
            parts.add(new PartPose(part, new Matrix4f(transform), normal.invert().transpose(), scale));
        }
    }

    private record PartPose(ModelPart part, Matrix4f transform, Matrix3f normal, float scale) {}
    private record PartGeometry(ModelPart part, List<ModelPart.Cube> cubes,
                                Vector3f probe, Vector3f originalInRoot) {}

    private record SurfacePoint(ModelPart part, ModelPart.Cube cube, int sample, Vec3 localPoint) {}
    private record Site(int owner, SurfacePoint key, Vec3 localNormal, float spacing, AnchorSource source) {}

    private record Layout(List<PartGeometry> geometry, List<Site> sites, double movementThresholdSquared,
                          float logicalSize) {
        boolean matches(Pose pose, float modelSize) {
            if (pose.parts.size() != geometry.size() || Math.abs(modelSize - logicalSize) > .15 * logicalSize)
                return false;
            for (int i = 0; i < geometry.size(); i++) {
                PartGeometry stored = geometry.get(i);
                ModelPart part = pose.parts.get(i).part;
                if (stored.part != part || !stored.cubes.equals(part.cubes)) return false;
            }
            return true;
        }

        boolean matchesPose(Pose pose) {
            // Express probes relative to the model. Whole-entity movement, turning and send-out scaling
            // do not change which pieces of skin are exposed.
            Matrix4f inverseRoot = new Matrix4f(pose.parts.getFirst().transform).invert();
            Vector3f point = new Vector3f();
            for (int i = 0; i < geometry.size(); i++) {
                PartGeometry g = geometry.get(i);
                pose.parts.get(i).transform.transformPosition(g.probe, point);
                inverseRoot.transformPosition(point);
                if (point.distanceSquared(g.originalInRoot) > movementThresholdSquared) return false;
            }
            return true;
        }
    }

    List<BoneAnchor> resolve(Pose pose, Vec3 offset, float modelSize) {
        clearFollowPose();
        if (pose.parts.isEmpty()) return List.of();
        followPose = pose;
        followOffset = offset;
        if (current == null || !current.matches(pose, modelSize) || (++poseCheck >= 20 && !current.matchesPose(pose))) {
            Layout next = null;
            for (int i = 0; i < recent.size(); i++) {
                Layout candidate = recent.get(i);
                if (candidate.matches(pose, modelSize) && candidate.matchesPose(pose)) {
                    next = recent.remove(i);
                    break;
                }
            }
            if (current != null) {
                if (recent.size() == 3) recent.removeLast();
                recent.addFirst(current);
            }
            if (next == null) {
                ModelPart root = pose.parts.getFirst().part;
                WeakReference<Layout> reference = SHARED.get(root);
                next = reference == null ? null : reference.get();
                if (next == null || !next.matches(pose, modelSize) || !next.matchesPose(pose)) {
                    next = bake(pose, modelSize);
                    SHARED.put(root, new WeakReference<>(next));
                }
            }
            current = next;
            poseCheck = 0;
        }
        if (poseCheck >= 20) poseCheck = 0;
        List<BoneAnchor> anchors = new ArrayList<>(current.sites.size());
        Vector3f scratch = new Vector3f();
        for (Site site : current.sites) {
            PartPose owner = pose.parts.get(site.owner);
            Vec3 p = site.key.localPoint;
            owner.transform.transformPosition((float)p.x, (float)p.y, (float)p.z, scratch);
            Vec3 world = offset.add(scratch.x, scratch.y, scratch.z);
            Vec3 n = site.localNormal;
            owner.normal.transform((float)n.x, (float)n.y, (float)n.z, scratch).normalize();
            Vec3 normal = new Vec3(scratch.x, scratch.y, scratch.z);
            anchors.add(anchor(world, normal, site.key, site.spacing * owner.scale, site.source));
        }
        return anchors;
    }

    void clearFollowPose() {
        followPose = null;
        followParts.clear();
        followPositions.clear();
    }

    /** A layout refresh can retire an emitter while its young smoke still follows that piece of skin. */
    Vec3 followPosition(Object key) {
        if (!(key instanceof SurfacePoint point) || followPose == null) return null;
        Vec3 cached = followPositions.get(point);
        if (cached != null) return cached;
        if (followParts.isEmpty()) {
            for (PartPose part : followPose.parts) followParts.put(part.part, part);
        }
        PartPose owner = followParts.get(point.part);
        if (owner == null || !owner.part.cubes.contains(point.cube)) return null;
        Vector3f posed = owner.transform.transformPosition(vector(point.localPoint));
        Vec3 world = followOffset.add(posed.x, posed.y, posed.z);
        followPositions.put(point, world);
        return world;
    }

    private static BoneAnchor anchor(Vec3 point, Vec3 normal, Object key, float spacing, AnchorSource source) {
        return new BoneAnchor(point, 1, 1f, AnchorRole.SURFACE, source, normal, key, spacing, normal);
    }

    /** Stable substitute while the actual posed mesh is unavailable. */
    static List<BoneAnchor> fallback(float width, float height, Vec3 base) {
        double rx = Math.max(.22, width) * .5, ry = Math.max(.35, height) * .5;
        List<BoneAnchor> result = new ArrayList<>(SHELL.length);
        float spacing = (float)Math.sqrt((rx * rx + 2 * rx * ry) * 4 * Math.PI / (3 * SHELL.length));
        for (int i = 0; i < SHELL.length; i++) {
            Vec3 p = SHELL[i];
            Vec3 normal = new Vec3(p.x / rx, p.y / ry, p.z / rx).normalize();
            result.add(anchor(base.add(p.x * rx, (1 + p.y) * ry, p.z * rx), normal, i, spacing, AnchorSource.SHELL));
        }
        return result;
    }

    private static Vec3[] fallbackDirections() {
        Vec3[] shell = new Vec3[MIN_BASE_SITES];
        for (int i = 0; i < shell.length; i++) {
            // Coprime stride gives an even sweep for small per-tick emitter budgets.
            int sample = i * 19 % shell.length;
            double y = (sample + .5) * 2 / shell.length - 1;
            double r = Math.sqrt(1 - y * y);
            double angle = sample * Math.PI * (3 - Math.sqrt(5));
            shell[i] = new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
        }
        return shell;
    }

    private record Quad(int owner, ModelPart.Cube cube, int faceIndex, Face face,
                        Vector3d origin, Vector3d u, Vector3d v, Vector3d normal) {}
    // On a face edge, rim is 0/1 for u=0/1 and 2/3 for v=0/1.
    private record Hit(Quad quad, double u, double v, Vector3d point, int rim) {}

    private static Layout bake(Pose pose, float modelSize) {
        List<PartGeometry> geometry = new ArrayList<>();
        List<Quad> quads = new ArrayList<>();
        Vector3d low = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d high = new Vector3d(Double.NEGATIVE_INFINITY);
        Matrix4f inverseRoot = new Matrix4f(pose.parts.getFirst().transform).invert();
        for (int i = 0; i < pose.parts.size(); i++) {
            PartPose part = pose.parts.get(i);
            Vector3f probe = new Vector3f();
            for (ModelPart.Cube cube : part.part.cubes) {
                List<Face> faces = XdAuraSurfaceSampler.localFacesOf(cube);
                for (int faceIndex = 0; faceIndex < faces.size(); faceIndex++) {
                    Face face = faces.get(faceIndex);
                    Vector3d origin = new Vector3d(part.transform.transformPosition(vector(face.origin())));
                    Vector3d u = new Vector3d(part.transform.transformDirection(vector(face.u())));
                    Vector3d v = new Vector3d(part.transform.transformDirection(vector(face.v())));
                    Vector3d normal = new Vector3d(part.normal.transform(vector(face.normal())).normalize());
                    Quad quad = new Quad(i, cube, faceIndex, face, origin, u, v, normal);
                    quads.add(quad);
                    Vector3f probeCandidate = vector(face.origin());
                    if (probeCandidate.lengthSquared() > probe.lengthSquared()) probe.set(probeCandidate);
                    for (int k = 0; k < 4; k++) {
                        Vector3d corner = new Vector3d(origin).fma(k & 1, u).fma(k >> 1, v);
                        low.min(corner);
                        high.max(corner);
                    }
                }
            }
            Vector3f original = part.transform.transformPosition(probe, new Vector3f());
            geometry.add(new PartGeometry(part.part, List.copyOf(part.part.cubes), probe,
                    inverseRoot.transformPosition(original)));
        }
        if (quads.isEmpty()) return new Layout(geometry, List.of(), Double.POSITIVE_INFINITY, modelSize);

        // Keep the nearest actual face along each ray. An internal cube cannot win a depth cell.
        // Mirror/back faces on thin, two-sided parts receive separate front/back depth tests.
        Hit[] cells = new Hit[6 * CELLS];
        double[] depth = new double[cells.length];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        Vector3d extent = high.sub(low, new Vector3d());
        for (Quad quad : quads) {
            for (int axis = 0; axis < 3; axis++) raster(quad, axis, low, extent, depth, cells);
        }
        List<Hit> samples = new ArrayList<>(cells.length);
        double projectedArea = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) continue;
            samples.add(cells[i]);
            int axis = i / (2 * CELLS);
            projectedArea += extent.get((axis + 1) % 3) * extent.get((axis + 2) % 3) / CELLS;
        }
        // Logical size is stable during send-out, even when the first rendered pose is nearly zero scale.
        int baseSites = (int)Math.clamp(Math.ceil(32 + 18 * modelSize + 2 * Math.sqrt(quads.size() / 6.0)),
                MIN_BASE_SITES, MAX_BASE_SITES);
        int faceCount = samples.size();
        appendEdges(cells, extent, samples);
        double rootSpan = Math.max(extent.x, Math.max(extent.y, extent.z)) / pose.parts.getFirst().scale;
        double threshold = Math.max(.025, rootSpan * .12);
        return new Layout(geometry, spread(samples, faceCount, baseSites, projectedArea, pose), threshold * threshold, modelSize);
    }

    private static Vector3f vector(Vec3 p) {
        return new Vector3f((float)p.x, (float)p.y, (float)p.z);
    }

    private static void raster(Quad face, int axis, Vector3d low, Vector3d extent, double[] depth, Hit[] cells) {
        int ax = (axis + 1) % 3, ay = (axis + 2) % 3;
        double ux = face.u.get(ax), uy = face.u.get(ay), vx = face.v.get(ax), vy = face.v.get(ay);
        double det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-14 || extent.get(ax) < 1e-12 || extent.get(ay) < 1e-12) return;
        double ox = face.origin.get(ax) - low.get(ax), oy = face.origin.get(ay) - low.get(ay);
        double stepX = extent.get(ax) / GRID, stepY = extent.get(ay) / GRID;
        int x0 = Math.max(0, (int)Math.ceil((ox + Math.min(0, ux) + Math.min(0, vx)) / stepX - .5));
        int x1 = Math.min(GRID - 1, (int)Math.floor((ox + Math.max(0, ux) + Math.max(0, vx)) / stepX - .5));
        int y0 = Math.max(0, (int)Math.ceil((oy + Math.min(0, uy) + Math.min(0, vy)) / stepY - .5));
        int y1 = Math.min(GRID - 1, (int)Math.floor((oy + Math.max(0, uy) + Math.max(0, vy)) / stepY - .5));
        boolean positive = face.normal.get(axis) > 0;
        int plane = (2 * axis + (positive ? 1 : 0)) * CELLS;
        for (int y = y0; y <= y1; y++) {
            double sy = (y + .5) * stepY - oy;
            for (int x = x0; x <= x1; x++) {
                double sx = (x + .5) * stepX - ox;
                double u = (sx * vy - sy * vx) / det, v = (ux * sy - uy * sx) / det;
                if (u < 0 || u > 1 || v < 0 || v > 1) continue;
                double z = face.origin.get(axis) + u * face.u.get(axis) + v * face.v.get(axis);
                int index = plane + y * GRID + x;
                double signedDepth = positive ? z : -z;
                if (signedDepth <= depth[index]) continue;
                depth[index] = signedDepth;
                cells[index] = new Hit(face, u, v, new Vector3d(face.origin).fma(u, face.u).fma(v, face.v), -1);
            }
        }
    }

    private static void appendEdges(Hit[] cells, Vector3d extent, List<Hit> samples) {
        // Follow the exposed outline and creases in the same six depth views as the faces. Crossing
        // from one cell to its neighbor supplies a ray segment on the actual authored quadrilateral.
        // Only keep the intersection if that segment really exits the quad; a depth discontinuity
        // in the middle of an overlapped face is not a model edge.
        for (int i = 0; i < cells.length; i++) {
            Hit hit = cells[i];
            if (hit == null) continue;
            int x = i % GRID, y = i / GRID % GRID;
            int axis = i / (2 * CELLS), ax = (axis + 1) % 3, ay = (axis + 2) % 3;
            Quad q = hit.quad;
            double ux = q.u.get(ax), uy = q.u.get(ay), vx = q.v.get(ax), vy = q.v.get(ay);
            double det = ux * vy - uy * vx;
            double dx = extent.get(ax) / (GRID * det), dy = extent.get(ay) / (GRID * det);
            appendEdge(hit, x > 0 ? cells[i - 1] : null, -dx * vy, dx * uy, samples);
            appendEdge(hit, x + 1 < GRID ? cells[i + 1] : null, dx * vy, -dx * uy, samples);
            appendEdge(hit, y > 0 ? cells[i - GRID] : null, dy * vx, -dy * ux, samples);
            appendEdge(hit, y + 1 < GRID ? cells[i + GRID] : null, -dy * vx, dy * ux, samples);
        }
    }

    private static void appendEdge(Hit hit, Hit neighbor, double du, double dv, List<Hit> samples) {
        Quad q = hit.quad;
        if (neighbor != null && neighbor.quad == q) return;
        // First crossing of either pair of UV boundaries along the segment to the neighbor.
        double tu = du == 0 ? Double.POSITIVE_INFINITY : ((du < 0 ? 0 : 1) - hit.u) / du;
        double tv = dv == 0 ? Double.POSITIVE_INFINITY : ((dv < 0 ? 0 : 1) - hit.v) / dv;
        double t = Math.min(tu, tv);
        if (!(t >= 0 && t <= 1)) return;
        if (neighbor != null) {
            // Distance from the neighboring surface to this plane. A continuation in the same
            // plane is an internal cube seam. A surface in front covers the edge from this view.
            Vector3d p = neighbor.point;
            double gap = q.normal.x * (p.x - hit.point.x) + q.normal.y * (p.y - hit.point.y)
                    + q.normal.z * (p.z - hit.point.z);
            double tolerance = 1e-5 * Math.max(q.u.length(), q.v.length());
            if (gap > tolerance || (gap >= -tolerance && q.normal.dot(neighbor.quad.normal) > .999)) return;
        }
        int rim = tu <= tv ? (du < 0 ? 0 : 1) : (dv < 0 ? 2 : 3);
        double u = rim < 2 ? rim : Math.clamp(hit.u + t * du, 0, 1);
        double v = rim >= 2 ? rim - 2 : Math.clamp(hit.v + t * dv, 0, 1);
        samples.add(new Hit(q, u, v, new Vector3d(q.origin).fma(u, q.u).fma(v, q.v), rim));
    }

    private static List<Site> spread(List<Hit> samples, int faceCount, int baseSites, double area, Pose pose) {
        if (samples.isEmpty()) return List.of();
        // Extend the same farthest-point sequence into the remaining gaps. Using the base spacing
        // keeps the first sites on their established faces and edges when the budget grows.
        int siteBudget = baseSites + Math.round(baseSites * 0.125f);
        List<Hit> selected = new ArrayList<>(siteBudget);
        int chosen = 0;
        for (int i = 1; i < faceCount; i++)
            if (samples.get(i).point.y < samples.get(chosen).point.y) chosen = i;
        double[] nearest = new double[samples.size()];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        float spacing = (float)Math.sqrt(area / baseSites);
        double normalWeight = .16 * spacing * spacing;
        // Give the largest layouts more edge sites for extremities, and compact models more face sites.
        int edgeInterval = baseSites == MAX_BASE_SITES ? 4 : 6;
        for (int n = 0; n < siteBudget && chosen >= 0; n++) {
            Hit hit = samples.get(chosen);
            Quad q = hit.quad;
            selected.add(hit);
            nearest[chosen] = 0;
            int nextFace = -1, nextEdge = -1;
            double bestFace = 1e-12, bestEdge = .08 * spacing * spacing;
            for (int i = 0; i < samples.size(); i++) {
                Hit other = samples.get(i);
                double distance = other.point.distanceSquared(hit.point);
                if (hit.rim < 0 && other.rim < 0)
                    distance += normalWeight * (1 - Math.clamp(q.normal.dot(other.quad.normal), -1, 1));
                nearest[i] = Math.min(nearest[i], distance);
                if (i < faceCount && nearest[i] > bestFace) {
                    nextFace = i;
                    bestFace = nearest[i];
                } else if (i >= faceCount && nearest[i] > bestEdge) {
                    nextEdge = i;
                    bestEdge = nearest[i];
                }
            }
            // Keep the base layout's face/edge sequence. Spend the extra sites alternately on
            // exposed edges and broad faces so thin crests and wingtips also get more coverage.
            int nextIndex = n + 1;
            boolean preferEdge = nextIndex < baseSites
                    ? n % edgeInterval == 0
                    : (nextIndex - baseSites) % 2 == 0;
            chosen = nextEdge >= 0 && (preferEdge || nextFace < 0) ? nextEdge : nextFace;
        }
        List<Site> sites = new ArrayList<>(selected.size());
        for (Hit hit : selected) {
            Quad q = hit.quad;
            Vec3 local = q.face.origin().add(q.face.u().scale(hit.u)).add(q.face.v().scale(hit.v));
            int faceId = hit.rim < 0 ? q.faceIndex : q.faceIndex + 8;
            int sample = (faceId * 1024 + (int)Math.round(hit.u * 1023)) * 1024 + (int)Math.round(hit.v * 1023);
            PartPose owner = pose.parts.get(q.owner);
            SurfacePoint key = new SurfacePoint(owner.part, q.cube, sample, local);
            double closest = Double.POSITIVE_INFINITY;
            for (Hit other : selected) {
                if (other == hit || q.normal.dot(other.quad.normal) < -.5) continue;
                closest = Math.min(closest, hit.point.distanceSquared(other.point));
            }
            float localSpacing = Double.isFinite(closest) ? (float)Math.sqrt(closest) : spacing;
            Vec3 normal = q.face.normal();
            AnchorSource source = AnchorSource.CUBE;
            if (hit.rim >= 0) {
                Vec3 across = (hit.rim < 2 ? q.face.u() : q.face.v()).normalize();
                normal = normal.add(across.scale(hit.rim % 2 == 0 ? -1 : 1)).normalize();
                source = AnchorSource.EDGE;
            }
            sites.add(new Site(q.owner, key, normal, localSpacing / owner.scale, source));
        }
        return sites;
    }
}
