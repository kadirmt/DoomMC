package com.akadi.doommc.item;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.block.ScreenLayout;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Right click a surface and a whole 25x16 wall goes up, facing you. */
public class ScreenItem extends Item {

	public ScreenItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		World world = ctx.getWorld();
		PlayerEntity player = ctx.getPlayer();
		if (player == null) return ActionResult.PASS;

		// the screen should end up looking back at whoever placed it
		Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
		Direction right = ScreenLayout.rightOf(facing);

		BlockPos anchor = ctx.getBlockPos().offset(ctx.getSide());
		// the clicked spot becomes the middle of the bottom row
		BlockPos origin = anchor.offset(right, -(DoomMC.SCREEN_WIDTH / 2));
		ScreenLayout layout = ScreenLayout.of(origin, facing);

		BlockPos blocked = firstObstruction(world, layout);
		if (blocked != null) {
			if (world.isClient) {
				player.sendMessage(Text.translatable("message.doommc.no_room",
						DoomMC.SCREEN_WIDTH, DoomMC.SCREEN_HEIGHT), true);
			}
			return ActionResult.FAIL;
		}

		if (world.isClient) return ActionResult.SUCCESS;

		BlockState base = DoomMC.SCREEN_BLOCK.getDefaultState().with(ScreenBlock.FACING, facing);
		for (int row = 0; row < layout.height(); row++) {
			for (int col = 0; col < layout.width(); col++) {
				boolean isOrigin = col == 0 && row == 0;
				world.setBlockState(layout.posAt(col, row), base.with(ScreenBlock.ORIGIN, isOrigin));
			}
		}

		world.playSound(null, anchor, SoundEvents.BLOCK_BEACON_ACTIVATE,
				SoundCategory.BLOCKS, 0.7f, 1.4f);
		player.sendMessage(Text.translatable("message.doommc.screen_placed"), true);

		if (!player.getAbilities().creativeMode) {
			ctx.getStack().decrement(1);
		}
		return ActionResult.SUCCESS;
	}

	/** First position in the way, or null when the whole wall fits. */
	private static BlockPos firstObstruction(World world, ScreenLayout layout) {
		for (BlockPos p : layout.allPositions()) {
			if (!world.isInBuildLimit(p)) return p;
			if (!world.getBlockState(p).isReplaceable()) return p;
		}
		return null;
	}
}
