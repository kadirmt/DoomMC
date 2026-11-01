package com.akadi.doommc.item;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.block.ScreenLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Right click a surface and a whole 25x16 wall goes up, facing you. */
public class ScreenItem extends Item {

	public ScreenItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		if (player == null) return InteractionResult.PASS;

		// the screen should end up looking back at whoever placed it
		Direction facing = ctx.getHorizontalDirection().getOpposite();
		Direction right = ScreenLayout.rightOf(facing);

		BlockPos anchor = ctx.getClickedPos().relative(ctx.getClickedFace());
		// the clicked spot becomes the middle of the bottom row
		BlockPos origin = anchor.relative(right, -(DoomMC.SCREEN_WIDTH / 2));
		ScreenLayout layout = ScreenLayout.of(origin, facing);

		BlockPos blocked = firstObstruction(level, layout);
		if (blocked != null) {
			if (level.isClientSide) {
				player.displayClientMessage(Component.translatable("message.doommc.no_room",
						DoomMC.SCREEN_WIDTH, DoomMC.SCREEN_HEIGHT), true);
			}
			return InteractionResult.FAIL;
		}

		if (level.isClientSide) return InteractionResult.SUCCESS;

		BlockState base = DoomMC.SCREEN_BLOCK.get().defaultBlockState().setValue(ScreenBlock.FACING, facing);
		for (int row = 0; row < layout.height(); row++) {
			for (int col = 0; col < layout.width(); col++) {
				boolean isOrigin = col == 0 && row == 0;
				level.setBlock(layout.posAt(col, row), base.setValue(ScreenBlock.ORIGIN, isOrigin), 3);
			}
		}

		level.playSound(null, anchor, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.4f);
		player.displayClientMessage(Component.translatable("message.doommc.screen_placed"), true);

		if (!player.getAbilities().instabuild) {
			ctx.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** First position in the way, or null when the whole wall fits. */
	private static BlockPos firstObstruction(Level level, ScreenLayout layout) {
		for (BlockPos p : layout.allPositions()) {
			if (!level.isInWorldBounds(p)) return p;
			if (!level.getBlockState(p).canBeReplaced()) return p;
		}
		return null;
	}
}
