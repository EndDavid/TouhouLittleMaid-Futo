package com.github.tartaricacid.touhoulittlemaid.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 碰撞箱旋转工具，逐字移植自上游 {@code util/VoxelShapeUtils}
 * （上游又转自 Mekanism 的同名文件，MIT 协议）。
 *
 * <p>本移植版只有稻草人用到它：稻草人上半部分的碰撞箱是按「朝北」写的单个盒子，
 * 朝东 / 南 / 西时要在平面上转 90° / 180° / 270°，而不是再手写三个坐标。</p>
 */
public final class VoxelShapeUtils {
    private static final Vec3 FROM_ORIGIN = new Vec3(-0.5, -0.5, -0.5);

    private VoxelShapeUtils() {
    }

    /**
     * 把 {@link AABB} 转到指定面，等价于方块模型绕该面旋转。
     */
    public static AABB rotate(AABB box, Direction side) {
        return switch (side) {
            case DOWN -> box;
            case UP -> new AABB(box.minX, -box.minY, -box.minZ, box.maxX, -box.maxY, -box.maxZ);
            case NORTH -> new AABB(box.minX, -box.minZ, box.minY, box.maxX, -box.maxZ, box.maxY);
            case SOUTH -> new AABB(-box.minX, -box.minZ, -box.minY, -box.maxX, -box.maxZ, -box.maxY);
            case WEST -> new AABB(box.minY, -box.minZ, -box.minX, box.maxY, -box.maxZ, -box.maxX);
            case EAST -> new AABB(-box.minY, -box.minZ, box.minX, -box.maxY, -box.maxZ, box.maxX);
        };
    }

    /**
     * 把 {@link AABB} 按给定旋转角旋转。
     */
    public static AABB rotate(AABB box, Rotation rotation) {
        return switch (rotation) {
            case NONE -> box;
            case CLOCKWISE_90 -> new AABB(-box.minZ, box.minY, box.minX, -box.maxZ, box.maxY, box.maxX);
            case CLOCKWISE_180 -> new AABB(-box.minX, box.minY, -box.minZ, -box.maxX, box.maxY, -box.maxZ);
            case COUNTERCLOCKWISE_90 -> new AABB(box.minZ, box.minY, -box.minX, box.maxZ, box.maxY, -box.maxX);
        };
    }

    /**
     * 水平方向的「默认旋转约定」：北 = 不转，南 = 180°，西 = 逆时针 90°，东 = 顺时针 90°。
     * 与 {@code BlockState#rotate} 的语义一致。
     */
    public static AABB rotateHorizontal(AABB box, Direction side) {
        return switch (side) {
            case NORTH -> rotate(box, Rotation.NONE);
            case SOUTH -> rotate(box, Rotation.CLOCKWISE_180);
            case WEST -> rotate(box, Rotation.COUNTERCLOCKWISE_90);
            case EAST -> rotate(box, Rotation.CLOCKWISE_90);
            default -> box;
        };
    }

    /**
     * 对形状里的每个 {@link AABB} 施加同一个变换，再合并回一个形状。
     *
     * <p>关键点：先把盒子平移到以「方块中心」为原点，旋转完再移回去 ——
     * 否则绕世界原点旋转会让形状飞出方块。</p>
     */
    public static VoxelShape rotate(VoxelShape shape, UnaryOperator<AABB> rotateFunction) {
        List<VoxelShape> rotatedPieces = new ArrayList<>();
        List<AABB> sourceBoundingBoxes = shape.toAabbs();
        for (AABB sourceBoundingBox : sourceBoundingBoxes) {
            rotatedPieces.add(Shapes.create(
                    rotateFunction.apply(sourceBoundingBox.move(FROM_ORIGIN.x, FROM_ORIGIN.y, FROM_ORIGIN.z))
                            .move(-FROM_ORIGIN.x, -FROM_ORIGIN.z, -FROM_ORIGIN.z)));
        }
        return combine(rotatedPieces);
    }

    public static VoxelShape rotateHorizontal(VoxelShape shape, Direction side) {
        return rotate(shape, box -> rotateHorizontal(box, side));
    }

    public static VoxelShape combine(Collection<VoxelShape> shapes) {
        return batchCombine(Shapes.empty(), BooleanOp.OR, true, shapes);
    }

    private static VoxelShape batchCombine(VoxelShape initial, BooleanOp function, boolean simplify,
                                           Collection<VoxelShape> shapes) {
        VoxelShape combinedShape = initial;
        for (VoxelShape shape : shapes) {
            combinedShape = Shapes.joinUnoptimized(combinedShape, shape, function);
        }
        return simplify ? combinedShape.optimize() : combinedShape;
    }
}

