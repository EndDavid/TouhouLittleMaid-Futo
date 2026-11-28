package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built;

import com.mojang.math.Vector3f;
import net.minecraft.core.Direction;

public class GeoQuad {
    public final Vector3f normal;
    public GeoVertex[] vertices;
    public Direction direction;

    public GeoQuad(GeoVertex[] verticesIn, float u1, float v1, float uSize, float vSize, float texWidth,
                   float texHeight, Boolean mirrorIn, Direction directionIn) {
        this.direction = directionIn;
        this.vertices = verticesIn;

        float u2 = u1 + uSize;
        float v2 = v1 + vSize;

        u1 /= texWidth;
        u2 /= texWidth;
        v1 /= texHeight;
        v2 /= texHeight;

        if (mirrorIn != null && mirrorIn) {
            vertices[0] = verticesIn[0].setTextureUV(u1, v1);
            vertices[1] = verticesIn[1].setTextureUV(u2, v1);
            vertices[2] = verticesIn[2].setTextureUV(u2, v2);
            vertices[3] = verticesIn[3].setTextureUV(u1, v2);
        } else {
            vertices[0] = verticesIn[0].setTextureUV(u2, v1);
            vertices[1] = verticesIn[1].setTextureUV(u1, v1);
            vertices[2] = verticesIn[2].setTextureUV(u1, v2);
            vertices[3] = verticesIn[3].setTextureUV(u2, v2);
        }

        this.normal = directionIn.step();
        if (mirrorIn != null && mirrorIn) {
            this.normal.mul(-1.0F, 1.0F, 1.0F);
        }
    }

    public GeoQuad(GeoVertex[] verticesIn, double[] uvCoords, double[] uvSize, float texWidth, float texHeight,
                   Boolean mirrorIn, Direction directionIn) {
        this(verticesIn, (float) uvCoords[0], (float) uvCoords[1], (float) uvSize[0], (float) uvSize[1], texWidth,
                texHeight, mirrorIn, directionIn);
    }

    /**
     * 直接给定 4 个顶点的 (u, v) —— 供基岩格式的 {@code uv_rotation} 使用。
     *
     * <p>{@code uvs} 是 8 个数 {@code u0,v0,u1,v1,u2,v2,u3,v3}，按顶点顺序对应。
     * 上游 {@code FaceItem#getRotatedUVs(texW, texH)} 返回的就是这个形状，
     * 这里只照单赋值，不做任何换算（换算在 {@code GeoCube} 里）。</p>
     */
    public GeoQuad(GeoVertex[] verticesIn, float[] uvs, Direction directionIn) {
        this.direction = directionIn;
        this.vertices = verticesIn;
        for (int i = 0; i < 4; i++) {
            this.vertices[i] = verticesIn[i].setTextureUV(uvs[i * 2], uvs[i * 2 + 1]);
        }
        this.normal = directionIn.step();
    }
}
