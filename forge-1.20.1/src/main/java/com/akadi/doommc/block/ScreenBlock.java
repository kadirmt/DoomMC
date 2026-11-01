package com.akadi.doommc.block;

import com.akadi.doommc.DoomMC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.Set;

/**
 * One panel of a screen wall. Exactly one block per wall carries {@code origin=true};
 * that block is the anchor the renderer and the remote both key off.
 */
public class ScreenBlock extends Block {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ORIGIN = BooleanProperty.create("origin");

	/** Stops the cascade of onRemove calls while a wall is coming down. */
	private static boolean demolishing;

	public ScreenBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(FACING, Direction.NORTH)
				.setValue(ORIGIN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ORIGIN);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && !demolishing) {
			demolish(level, pos);
		}
		super.onRemove(state, level, pos, newState, moved);
	}

	/** Break any one panel and the whole wall comes down, handing the item back. */
	private static void demolish(Level level, BlockPos brokenPos) {
		demolishing = true;
		try {
			Set<BlockPos> parts = ScreenLayout.connected(level, brokenPos);
			for (BlockPos p : parts) {
				if (DoomMC.isScreen(level.getBlockState(p))) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			Block.popResource(level, brokenPos, new ItemStack(DoomMC.SCREEN_ITEM.get()));
		} finally {
			demolishing = false;
		}
	}
}
