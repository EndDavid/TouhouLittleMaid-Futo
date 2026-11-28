package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.VectorUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GeoCube {
    public final List<GeoQuad> quads = new ObjectArrayList<>(6);
    public final Vector3f size = new Vector3f();

    private GeoCube(double[] size) {
        if (size.length >= 3) {
            this.size.set((float) size[0], (float) size[1], (float) size[2]);
        }
    }

    public static GeoCube createFromPojoCube(Cube cubeIn, ModelProperties properties, Double boneInflate, Boolean mirror) {
        GeoCube cube = new GeoCube(cubeIn.getSize());

        UvUnion uvUnion = cubeIn.getUv();
        UvFaces faces = uvUnion.faceUV;
        boolean isBoxUV = uvUnion.isBoxUV;
        double inflate = cubeIn.getInflate() == null ? (boneInflate == null ? 0 : boneInflate) : cubeIn.getInflate() / 16;

        // 零厚度的轴：只有「size 为 0 且 inflate 为负」这一种写法是真的坏了。
        //
        // 顶点用的是「origin - inflate」到「origin + size + inflate」。对正常盒子没问题，
        // 但 inflate 为负、size 又为 0 时下界会跑到上界**之上**：
        // {@code cchess_pieces.json} 里每个棋子都带一个
        // {@code origin [-32, 31.9, -32], size [64, 0, 64], inflate -30.75} 的 cube，
        // 代进去得到 y 从 3.9156 到 0.071875 —— 一张本该贴在棋子顶面的薄片，
        // 会被拉成 3.8 格高、0.16 格粗的柱子。
        //
        // 而这些薄片不是垃圾数据，它们就是棋子顶面的**汉字**：14 个 cube 各带独立 uv，
        // box-UV 的 up 面（uv.x + size.z, uv.y，大小 size.x × size.z）框住的
        // 正好是贴图里的一整字 —— 已把每个框逐个取图核对过。
        //
        // 所以正确处理不是丢掉，而是把这条轴**塌成一个平面**：取 + 侧边界
        // （= origin + inflate = 1.15 px，正好落在棋子顶面上），
        // 负向那个面便会与它完全重合，丢掉即可。
        //
        // 其余 1390 个零厚度 cube（inflate >= 0，绝大多数在女仆模型上）区间本来就正常，
        // 不碰 —— 动它们等于给所有角色换脸。
        double[] rawSize = cubeIn.getSize();
        boolean hasRawSize = rawSize != null && rawSize.length >= 3;
        boolean flatX = hasRawSize && rawSize[0] == 0 && inflate < 0;
        boolean flatY = hasRawSize && rawSize[1] == 0 && inflate < 0;
        boolean flatZ = hasRawSize && rawSize[2] == 0 && inflate < 0;

        float textureHeight = properties.getTextureHeight().floatValue();
        float textureWidth = properties.getTextureWidth().floatValue();

        Vec3 size = VectorUtils.fromArray(cubeIn.getSize());
        Vec3 origin = VectorUtils.fromArray(cubeIn.getOrigin());
        origin = new Vec3(-(origin.x + size.x) / 16, origin.y / 16, origin.z / 16);

        size = size.multiply(0.0625f, 0.0625, 0.0625f);

        Vector3f rotation = VectorUtils.convertDoubleToFloat(VectorUtils.fromArray(cubeIn.getRotation()));
        rotation.mul(-1, -1, 1);

        rotation.set((float) Math.toRadians(rotation.x()), (float) Math.toRadians(rotation.y()), (float) Math.toRadians(rotation.z()));

        Vector3f pivot = VectorUtils.convertDoubleToFloat(VectorUtils.fromArray(cubeIn.getPivot()));
        pivot.mul(-1, 1, 1);

        double loX = origin.x - inflate;
        double hiX = origin.x + size.x + inflate;
        double loY = origin.y - inflate;
        double hiY = origin.y + size.y + inflate;
        double loZ = origin.z - inflate;
        double hiZ = origin.z + size.z + inflate;

        // 见上：零厚度轴塌成单值
        if (flatX) {
            loX = hiX;
        }
        if (flatY) {
            loY = hiY;
        }
        if (flatZ) {
            loZ = hiZ;
        }

        GeoVertex P1 = new GeoVertex(loX, loY, loZ);
        GeoVertex P2 = new GeoVertex(loX, loY, hiZ);
        GeoVertex P3 = new GeoVertex(loX, hiY, loZ);
        GeoVertex P4 = new GeoVertex(loX, hiY, hiZ);
        GeoVertex P5 = new GeoVertex(hiX, loY, loZ);
        GeoVertex P6 = new GeoVertex(hiX, loY, hiZ);
        GeoVertex P7 = new GeoVertex(hiX, hiY, loZ);
        GeoVertex P8 = new GeoVertex(hiX, hiY, hiZ);

        GeoQuad quadWest;
        GeoQuad quadEast;
        GeoQuad quadNorth;
        GeoQuad quadSouth;
        GeoQuad quadUp;
        GeoQuad quadDown;

        if (!isBoxUV) {
            FaceUv west = faces.getWest();
            FaceUv east = faces.getEast();
            FaceUv north = faces.getNorth();
            FaceUv south = faces.getSouth();
            FaceUv up = faces.getUp();
            FaceUv down = faces.getDown();

            quadWest = perFace(new GeoVertex[]{P4, P3, P1, P2}, west, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.WEST);
            quadEast = perFace(new GeoVertex[]{P7, P8, P6, P5}, east, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.EAST);
            quadNorth = perFace(new GeoVertex[]{P3, P7, P5, P1}, north, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.NORTH);
            quadSouth = perFace(new GeoVertex[]{P8, P4, P2, P6}, south, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.SOUTH);
            quadUp = perFace(new GeoVertex[]{P4, P8, P7, P3}, up, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.UP);
            quadDown = perFace(new GeoVertex[]{P1, P5, P6, P2}, down, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.DOWN);

            if (Boolean.TRUE.equals(cubeIn.getMirror()) || Boolean.TRUE.equals(mirror)) {
                quadWest = perFace(new GeoVertex[]{P7, P8, P6, P5}, west, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.WEST);
                quadEast = perFace(new GeoVertex[]{P4, P3, P1, P2}, east, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.EAST);
                quadNorth = perFace(new GeoVertex[]{P3, P7, P5, P1}, north, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.NORTH);
                quadSouth = perFace(new GeoVertex[]{P8, P4, P2, P6}, south, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.SOUTH);
                quadUp = perFace(new GeoVertex[]{P1, P5, P6, P2}, up, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.UP);
                quadDown = perFace(new GeoVertex[]{P4, P8, P7, P3}, down, textureWidth, textureHeight,
                    cubeIn.getMirror(), Direction.DOWN);
            }
        } else {
            double[] uv = cubeIn.getUv().boxUVCoords;
            Vec3 uvSize = VectorUtils.fromArray(cubeIn.getSize());
            uvSize = new Vec3(Math.floor(uvSize.x), Math.floor(uvSize.y), Math.floor(uvSize.z));

            quadWest = new GeoQuad(new GeoVertex[]{P4, P3, P1, P2},
                    new double[]{uv[0] + uvSize.z + uvSize.x, uv[1] + uvSize.z}, new double[]{uvSize.z, uvSize.y},
                    textureWidth, textureHeight, cubeIn.getMirror(), Direction.WEST);
            quadEast = new GeoQuad(new GeoVertex[]{P7, P8, P6, P5}, new double[]{uv[0], uv[1] + uvSize.z},
                    new double[]{uvSize.z, uvSize.y}, textureWidth, textureHeight, cubeIn.getMirror(),
                    Direction.EAST);
            quadNorth = new GeoQuad(new GeoVertex[]{P3, P7, P5, P1},
                    new double[]{uv[0] + uvSize.z, uv[1] + uvSize.z}, new double[]{uvSize.x, uvSize.y},
                    textureWidth, textureHeight, cubeIn.getMirror(), Direction.NORTH);
            quadSouth = new GeoQuad(new GeoVertex[]{P8, P4, P2, P6},
                    new double[]{uv[0] + uvSize.z + uvSize.x + uvSize.z, uv[1] + uvSize.z},
                    new double[]{uvSize.x, uvSize.y}, textureWidth, textureHeight, cubeIn.getMirror(),
                    Direction.SOUTH);
            quadUp = new GeoQuad(new GeoVertex[]{P4, P8, P7, P3}, new double[]{uv[0] + uvSize.z, uv[1]},
                    new double[]{uvSize.x, uvSize.z}, textureWidth, textureHeight, cubeIn.getMirror(), Direction.UP);
            quadDown = new GeoQuad(new GeoVertex[]{P1, P5, P6, P2},
                    new double[]{uv[0] + uvSize.z + uvSize.x, uv[1] + uvSize.z},
                    new double[]{uvSize.x, -uvSize.z}, textureWidth, textureHeight, cubeIn.getMirror(),
                    Direction.DOWN);

            if (Boolean.TRUE.equals(cubeIn.getMirror()) || Boolean.TRUE.equals(mirror)) {
                quadWest = new GeoQuad(new GeoVertex[]{P7, P8, P6, P5},
                        new double[]{uv[0] + uvSize.z + uvSize.x, uv[1] + uvSize.z},
                        new double[]{uvSize.z, uvSize.y}, textureWidth, textureHeight, cubeIn.getMirror(),
                        Direction.WEST);
                quadEast = new GeoQuad(new GeoVertex[]{P4, P3, P1, P2}, new double[]{uv[0], uv[1] + uvSize.z},
                        new double[]{uvSize.z, uvSize.y}, textureWidth, textureHeight, cubeIn.getMirror(),
                        Direction.EAST);
                quadNorth = new GeoQuad(new GeoVertex[]{P3, P7, P5, P1},
                        new double[]{uv[0] + uvSize.z, uv[1] + uvSize.z}, new double[]{uvSize.x, uvSize.y},
                        textureWidth, textureHeight, cubeIn.getMirror(), Direction.NORTH);
                quadSouth = new GeoQuad(new GeoVertex[]{P8, P4, P2, P6},
                        new double[]{uv[0] + uvSize.z + uvSize.x + uvSize.z, uv[1] + uvSize.z},
                        new double[]{uvSize.x, uvSize.y}, textureWidth, textureHeight, cubeIn.getMirror(),
                        Direction.SOUTH);
                quadUp = new GeoQuad(new GeoVertex[]{P4, P8, P7, P3}, new double[]{uv[0] + uvSize.z, uv[1]},
                        new double[]{uvSize.x, uvSize.z}, textureWidth, textureHeight, cubeIn.getMirror(), Direction.UP);
                quadDown = new GeoQuad(new GeoVertex[]{P1, P5, P6, P2},
                        new double[]{uv[0] + uvSize.z + uvSize.x, uv[1] + uvSize.z},
                        new double[]{uvSize.x, -uvSize.z}, textureWidth, textureHeight, cubeIn.getMirror(),
                        Direction.DOWN);
            }
        }

        // 塌陷之后，负向那个面与正向那个面顶点完全相同（只有绕序和 UV 相反），
        // 两个都画就是同一张贴图叠两遍、后者覆盖前者，字会变成镜像。
        // 只保留正向那一个。
        if (flatX) {
            quadWest = null;
        }
        if (flatY) {
            quadDown = null;
        }
        if (flatZ) {
            quadNorth = null;
        }

        if (quadWest != null) {
            cube.quads.add(quadWest);
        }
        if (quadEast != null) {
            cube.quads.add(quadEast);
        }
        if (quadNorth != null) {
            cube.quads.add(quadNorth);
        }
        if (quadSouth != null) {
            cube.quads.add(quadSouth);
        }
        if (quadUp != null) {
            cube.quads.add(quadUp);
        }
        if (quadDown != null) {
            cube.quads.add(quadDown);
        }


        PoseStack poseStack = new PoseStack();
        poseStack.translate(pivot.x() / 16f, pivot.y() / 16f, pivot.z() / 16f);
        poseStack.mulPose(Vector3f.ZP.rotation(rotation.z()));
        poseStack.mulPose(Vector3f.YP.rotation(rotation.y()));
        poseStack.mulPose(Vector3f.XP.rotation(rotation.x()));
        poseStack.translate(-pivot.x() / 16f, -pivot.y() / 16f, -pivot.z() / 16f);

        for (var quad : cube.quads) {
            quad.normal.transform(poseStack.last().normal());
            for (var vertex : quad.vertices) {
                var vec4 = new Vector4f(vertex.position.x(), vertex.position.y(), vertex.position.z(), 1);
                vec4.transform(poseStack.last().pose());
                vertex.position.set(vec4.x(), vec4.y(), vec4.z());
            }
        }

        return cube;
    }

    /**
     * per-face UV 的四边形，并按 {@code uv_rotation} 旋转贴图矩形。
     *
     * <p>基岩格式允许每个面单独写 {@code "uv_rotation": 180}，意思和原版模型里
     * 「把这张贴图转 180°」一样。上游（1.20.1 jar 里的 simplebedrockmodel）在
     * {@code FaceItem#getRotatedUVs(texW, texH)} 里处理它，返回 8 个数
     * {@code u0,v0,u1,v1,u2,v2,u3,v3} 直接按顶点顺序赋值；本移植版此前完全没有这个字段，
     * 于是所有带 {@code uv_rotation} 的面都被当成「没转」采样。</p>
     *
     * <p>零食柜的玻璃罩正好全是 {@code 180}：外框、内框、两块斜板、玻璃片都带它。
     * 忽略之后框线/亮点落到了矩形的另一侧，看上去就是「玻璃显示不对、斜角多出一块」。</p>
     *
     * <p>{@code mirror} 与 {@code uv_rotation} 同时出现时仍走旧路径：上游 per-face
     * 分支本身也不处理 mirror，这里不替它做决定。</p>
     */
    private static GeoQuad perFace(GeoVertex[] vertices, FaceUv face, float texWidth, float texHeight,
                                   Boolean cubeMirror, Direction direction) {
        if (face == null) {
            return null;
        }
        Integer rotation = face.getUvRotation();
        if (rotation == null || rotation == 0 || Boolean.TRUE.equals(cubeMirror)) {
            return new GeoQuad(vertices, face.getUv(), face.getUvSize(), texWidth, texHeight, cubeMirror, direction);
        }
        double[] uv = face.getUv();
        double[] size = face.getUvSize();
        float u1 = (float) (uv[0] / texWidth);
        float v1 = (float) (uv[1] / texHeight);
        float u2 = (float) ((uv[0] + size[0]) / texWidth);
        float v2 = (float) ((uv[1] + size[1]) / texHeight);
        float[] uvs = new float[8];
        if (rotation == 90) {
            uvs[0] = u1;
            uvs[1] = v1;
            uvs[2] = u1;
            uvs[3] = v2;
            uvs[4] = u2;
            uvs[5] = v2;
            uvs[6] = u2;
            uvs[7] = v1;
        } else if (rotation == 180) {
            uvs[0] = u1;
            uvs[1] = v2;
            uvs[2] = u2;
            uvs[3] = v2;
            uvs[4] = u2;
            uvs[5] = v1;
            uvs[6] = u1;
            uvs[7] = v1;
        } else if (rotation == 270) {
            uvs[0] = u2;
            uvs[1] = v2;
            uvs[2] = u2;
            uvs[3] = v1;
            uvs[4] = u1;
            uvs[5] = v1;
            uvs[6] = u1;
            uvs[7] = v2;
        } else {
            // 非法角度：退回「不转」，与上游 default 分支一致
            return new GeoQuad(vertices, face.getUv(), face.getUvSize(), texWidth, texHeight, cubeMirror, direction);
        }
        return new GeoQuad(vertices, uvs, direction);
    }
}
