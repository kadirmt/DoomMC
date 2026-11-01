package com.akadi.doommc.block;

import com.akadi.doommc.DoomMC;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geometry of one assembled screen wall.
 *
 * <p>{@code origin} is the bottom block on the viewer's LEFT, {@code facing} is the
 * direction the screen looks (out toward the viewer) and {@code right} walks along
 * the wall to the viewer's right.
 */
public record ScreenLayout(BlockPos origin, Direction facing, Direction right, int width, int height) {

	public static Direction rightOf(Direction facing) {
		// standing in front of a screen that faces north, your right hand points west
		return facing.rotateYCounterclockwise();
	}

	public static ScreenLayout of(BlockPos origin, Direction facing) {
		return new ScreenLayout(origin, facing, rightOf(facing), DoomMC.SCREEN_WIDTH, DoomMC.SCREEN_HEIGHT);
	}

	public BlockPos posAt(int col, int row) {
		return origin.offset(right, col).up(row);
	}

	public List<BlockPos> allPositions() {
		List<BlockPos> out = new ArrayList<>(width * height);
		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				out.add(posAt(col, row));
			}
		}
		return out;
	}

	/**
	 * Walk the connected run of screen blocks starting at {@code start} and return the
	 * layout anchored on whichever one is flagged as the origin. Returns null when the
	 * structure has been damaged past recognition.
	 */
	public static ScreenLayout resolve(BlockView world, BlockPos start) {
		BlockState startState = world.getBlockState(start);
		if (!DoomMC.isScreen(startState)) return null;

		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);

		int limit = DoomMC.SCREEN_WIDTH * DoomMC.SCREEN_HEIGHT * 2;
		while (!queue.isEmpty() && seen.size() <= limit) {
			BlockPos cur = queue.poll();
			BlockState state = world.getBlockState(cur);
			if (!DoomMC.isScreen(state)) continue;
			if (state.get(ScreenBlock.ORIGIN)) {
				return of(cur, state.get(ScreenBlock.FACING));
			}
			for (Direction d : Direction.values()) {
				BlockPos next = cur.offset(d);
				if (seen.add(next) && DoomMC.isScreen(world.getBlockState(next))) {
					queue.add(next);
				}
			}
		}
		return null;
	}

	/** Every screen block reachable from {@code start}, used when tearing one down. */
	public static Set<BlockPos> connected(BlockView world, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		if (!DoomMC.isScreen(world.getBlockState(start))) return seen;

		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		int limit = DoomMC.SCREEN_WIDTH * DoomMC.SCREEN_HEIGHT * 2;

		while (!queue.isEmpty() && seen.size() <= limit) {
			BlockPos cur = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos next = cur.offset(d);
				if (seen.contains(next)) continue;
				if (!DoomMC.isScreen(world.getBlockState(next))) continue;
				seen.add(next);
				queue.add(next);
			}
		}
		return seen;
	}
}
