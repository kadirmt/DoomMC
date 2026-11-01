package com.akadi.doommc.block;

import com.akadi.doommc.DoomMC;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Set;

/**
 * One panel of a screen wall. Exactly one block per wall carries {@code origin=true};
 * that block is the anchor the renderer and the remote both key off.
 */
public class ScreenBlock extends Block {
	public static final MapCodec<ScreenBlock> CODEC = createCodec(ScreenBlock::new);

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty ORIGIN = BooleanProperty.of("origin");

	/** Stops the cascade of onStateReplaced calls while a wall is coming down. */
	private static boolean demolishing;

	public ScreenBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(ORIGIN, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, ORIGIN);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && !world.isClient && !demolishing) {
			demolish(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	/** Break any one panel and the whole wall comes down, handing the item back. */
	private static void demolish(World world, BlockPos brokenPos) {
		demolishing = true;
		try {
			Set<BlockPos> parts = ScreenLayout.connected(world, brokenPos);
			for (BlockPos p : parts) {
				if (DoomMC.isScreen(world.getBlockState(p))) {
					world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
				}
			}
			dropStack(world, brokenPos, new ItemStack(DoomMC.SCREEN_ITEM));
		} finally {
			demolishing = false;
		}
	}
}
