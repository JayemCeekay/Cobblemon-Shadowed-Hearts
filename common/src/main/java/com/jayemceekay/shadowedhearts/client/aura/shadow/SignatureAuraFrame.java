package com.jayemceekay.shadowedhearts.client.aura.shadow;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/** The moving reference frame of one world aura. Retired frames stay at their last drawn pose. */
final class SignatureAuraFrame {
    private Vec3 entityBase;
    private Vec3 lastTickBase;
    private Vec3 origin;
    private long lastSeen;
    private boolean traveling;
    private boolean posed;
    private final Matrix3f initialInverse = new Matrix3f();
    private final Matrix3f basis = new Matrix3f();
    private final Matrix3f toWorld = new Matrix3f();
    private final Matrix3f toLocal = new Matrix3f();

    SignatureAuraFrame(Vec3 base, long gameTime) {
        entityBase = origin = lastTickBase = base;
        lastSeen = gameTime;
    }

    boolean canContinue(Vec3 base, long gameTime) {
        long elapsed = gameTime - lastSeen;
        double maxTravel = 16.0 * Math.max(1L, elapsed);
        return elapsed >= 0 && elapsed <= 5 && entityBase.distanceToSqr(base) <= maxTravel * maxTravel;
    }

    void move(Vec3 base, long gameTime) {
        if (gameTime != lastSeen) {
            // Sample entity travel once per tick. A bank around the hitbox can move the model pivot in place.
            double minTravel = 0.02 * (gameTime - lastSeen);
            traveling = base.distanceToSqr(lastTickBase) > minTravel * minTravel;
            lastTickBase = base;
        }
        if (!base.equals(entityBase)) origin = origin.add(base.subtract(entityBase));
        entityBase = base;
        lastSeen = gameTime;
    }

    boolean isPosed() {
        return posed;
    }

    boolean isTraveling() {
        return traveling;
    }

    void pose(Vec3 camera, Matrix4f root) {
        origin = camera.add(root.m30(), root.m31(), root.m32());
        // Puffs have sizes in blocks. Inherit the model's orientation, leaving its render scale on the mesh.
        root.normalize3x3(basis);
        if (!posed) {
            basis.invert(initialInverse);
            posed = true;
        } else {
            basis.mul(initialInverse, toWorld);
            toWorld.invert(toLocal);
        }
    }

    Matrix4f captureToLocal(Vec3 poseToWorldOffset) {
        Vec3 offset = poseToWorldOffset.subtract(origin);
        return new Matrix4f().set(toLocal).translate((float) offset.x, (float) offset.y, (float) offset.z);
    }

    void toWorldDirection(Vector3d v) {
        v.set(toWorld.m00() * v.x + toWorld.m10() * v.y + toWorld.m20() * v.z,
              toWorld.m01() * v.x + toWorld.m11() * v.y + toWorld.m21() * v.z,
              toWorld.m02() * v.x + toWorld.m12() * v.y + toWorld.m22() * v.z);
    }

    void toWorldPosition(Vector3d v) {
        toWorldDirection(v);
        v.add(origin.x, origin.y, origin.z);
    }
}
