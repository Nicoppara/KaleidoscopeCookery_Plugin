package net.kaleidoscope.cookery.block.entity;

import org.joml.Quaternionf;
import org.joml.Vector3f;

final class MillstoneAnimation {

    private MillstoneAnimation() {
    }

    static float baseYawRad(float yRotDeg) {
        return (float) Math.toRadians(-yRotDeg - 90);
    }

    // 自转棍 固定高度 仅随基准朝向 + 角度自转
    static Vector3f stick1Translation(float baseYawRad) {
        Vector3f translation = new Vector3f(0f, 0.9f, 0f);
        translation.rotateY(baseYawRad);
        return translation;
    }

    static Quaternionf stick1Rotation(float baseYawRad, float angleY) {
        return new Quaternionf()
                .rotateY(baseYawRad)
                .rotateY((float) Math.toRadians(angleY));
    }

    // 公转支架 随角度公转平移并同步自转
    static Vector3f stick2Translation(float baseYawRad, float angleY) {
        float radians = (float) Math.toRadians(angleY);
        return new Vector3f(0f, 0.6f, 0f)
                .rotateY(radians)
                .rotateY(baseYawRad);
    }

    static Quaternionf stick2Rotation(float baseYawRad, float angleY) {
        float radians = (float) Math.toRadians(angleY);
        return new Quaternionf()
                .rotateY(baseYawRad)
                .rotateY(radians);
    }

    // 磨石 绕中心公转并附加自转
    static Vector3f stoneTranslation(float baseYawRad, float angleY) {
        float rad = (float) Math.toRadians(angleY);
        Vector3f orbit = new Vector3f(0f, 1.4f, -0.5f);
        orbit.rotateY(rad).rotateY(baseYawRad + (float) Math.PI);
        return orbit;
    }

    static Quaternionf stoneRotation(float baseYawRad, float angleY) {
        float rad = (float) Math.toRadians(angleY);
        float spin = rad * 6.0f;
        return new Quaternionf()
                .rotateY(-baseYawRad)
                .rotateY(rad)
                .rotateZ(spin);
    }

    // 研磨槽 八个槽沿环形均布并朝内倾斜
    static Vector3f grindTranslation(int slot) {
        float rad = (float) Math.toRadians(slot * 45f);
        return new Vector3f(0.6f, 0.9f, 0f).rotateY(rad);
    }

    static Quaternionf grindRotation(int slot) {
        float rad = (float) Math.toRadians(slot * 45f);
        return new Quaternionf().rotateY(rad).rotateX((float) Math.toRadians(-80));
    }
}
