package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;


final class MechanicalFluidGunMount {

	private MechanicalFluidGunMount() {
	}

	static Direction getMountFace(BlockState state) {
		return state.hasProperty(MechanicalFluidGunBlock.MOUNT_FACE)
			? state.getValue(MechanicalFluidGunBlock.MOUNT_FACE)
			: Direction.UP;
	}

	static Direction getFacing(BlockState state) {
		Direction facing = state.hasProperty(MechanicalFluidGunBlock.FACING)
			? state.getValue(MechanicalFluidGunBlock.FACING)
			: Direction.NORTH;
		return normalizeFacing(getMountFace(state), facing);
	}

	static boolean isFacingValid(Direction mountFace, Direction facing) {
		return facing.getAxis().isHorizontal() && facing.getAxis() != mountFace.getAxis();
	}

	static Direction normalizeFacing(Direction mountFace, Direction facing) {
		if (isFacingValid(mountFace, facing)) return facing;
		for (Direction candidate : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			if (candidate.getAxis() != mountFace.getAxis()) return candidate;
		}
		return Direction.NORTH;
	}

	static Direction resolveFacing(Direction mountFace, Direction playerHorizontal) {
		if (isFacingValid(mountFace, playerHorizontal)) return playerHorizontal;
		return playerHorizontal.getClockWise();
	}

	static void rotateModel(PoseStack ms, BlockState state) {
		Direction mountFace = getMountFace(state);
		rotateModel(ms, mountFace);
	}

	static void rotateModel(PoseStack ms, Direction mountFace) {
		ms.translate(0.5, 0.5, 0.5);

		switch (mountFace) {
			case UP -> {} // default, no rotation
			case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(180));
			case NORTH -> ms.mulPose(Axis.XP.rotationDegrees(-90));
			case SOUTH -> ms.mulPose(Axis.XP.rotationDegrees(90));
			case EAST -> ms.mulPose(Axis.ZP.rotationDegrees(-90));
			case WEST -> ms.mulPose(Axis.ZP.rotationDegrees(90));
		}

		ms.translate(-0.5, -0.5, -0.5);
	}

	static Vec3 toLocal(Direction mountFace, Vec3 worldOffset) {
		Vec3 v = worldOffset.subtract(0.5, 0.5, 0.5);
		v = undoMountRotation(v, mountFace);
		return v.add(0.5, 0.5, 0.5);
	}

	static Vec3 toWorld(Direction mountFace, Vec3 localOffset) {
		Vec3 v = localOffset.subtract(0.5, 0.5, 0.5);
		v = applyMountRotation(v, mountFace);
		return v.add(0.5, 0.5, 0.5);
	}

	static Direction toLocalDirection(Direction mountFace, Direction worldDirection) {
		Vec3 v = Vec3.atLowerCornerOf(worldDirection.getNormal());
		v = undoMountRotation(v, mountFace);
		return Direction.getNearest(v.x, v.y, v.z);
	}

	private static Vec3 applyMountRotation(Vec3 v, Direction mountFace) {
		return switch (mountFace) {
			case UP -> v;
			case DOWN -> new Vec3(v.x, -v.y, -v.z);
			case NORTH -> new Vec3(v.x, v.z, -v.y);
			case SOUTH -> new Vec3(v.x, -v.z, v.y);
			case EAST -> new Vec3(v.y, -v.x, v.z);
			case WEST -> new Vec3(-v.y, v.x, v.z);
		};
	}

	private static Vec3 undoMountRotation(Vec3 v, Direction mountFace) {
		return switch (mountFace) {
			case UP -> v;
			case DOWN -> new Vec3(v.x, -v.y, -v.z);
			case NORTH -> new Vec3(v.x, -v.z, v.y);
			case SOUTH -> new Vec3(v.x, v.z, -v.y);
			case EAST -> new Vec3(-v.y, v.x, v.z);
			case WEST -> new Vec3(v.y, -v.x, v.z);
		};
	}

	private static final VoxelShape FLOOR_SHAPE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 10, 16);

	static VoxelShape getShapeForMount(Direction mountFace) {
		return switch (mountFace) {
			case UP -> FLOOR_SHAPE;
			case DOWN -> net.minecraft.world.level.block.Block.box(0, 6, 0, 16, 16, 16);
			case NORTH -> net.minecraft.world.level.block.Block.box(0, 0, 6, 16, 16, 16);
			case SOUTH -> net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 16, 10);
			case EAST -> net.minecraft.world.level.block.Block.box(0, 0, 0, 10, 16, 16);
			case WEST -> net.minecraft.world.level.block.Block.box(6, 0, 0, 16, 16, 16);
		};
	}
}
