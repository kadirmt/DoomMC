package com.akadi.doommc.item;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenLayout;
import com.akadi.doommc.server.ServerDoom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The handheld. Right click a screen to bind it, right click anywhere after that to
 * play, sneak plus right click to shut the session down. Pairing lives in item NBT.
 */
public class RemoteItem extends Item {
	private static final String NBT_LINKED = "LinkedScreen";

	public RemoteItem(Properties properties) {
		super(properties);
	}

	@Nullable
	public static BlockPos linked(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(NBT_LINKED)) return null;
		return BlockPos.of(tag.getLong(NBT_LINKED));
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		if (player == null) return InteractionResult.PASS;
		if (!DoomMC.isScreen(level.getBlockState(ctx.getClickedPos()))) return InteractionResult.PASS;

		ScreenLayout layout = ScreenLayout.resolve(level, ctx.getClickedPos());
		if (layout == null) {
			if (!level.isClientSide) {
				player.displayClientMessage(Component.translatable("message.doommc.broken_screen"), true);
			}
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide) {
			ctx.getItemInHand().getOrCreateTag().putLong(NBT_LINKED, layout.origin().asLong());
			level.playSound(null, ctx.getClickedPos(), SoundEvents.NOTE_BLOCK_BIT.value(),
					SoundSource.BLOCKS, 0.6f, 1.8f);
			player.displayClientMessage(Component.translatable("message.doommc.linked"), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		BlockPos linked = linked(stack);

		if (linked == null) {
			if (!level.isClientSide) {
				user.displayClientMessage(Component.translatable("message.doommc.not_linked"), true);
			}
			return InteractionResultHolder.fail(stack);
		}

		// the server owns the game, so it decides who gets the controls
		if (user instanceof ServerPlayer serverPlayer) {
			if (user.isShiftKeyDown()) {
				ServerDoom.stop(serverPlayer, linked);
			} else {
				ServerDoom.requestControl(serverPlayer, linked);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		BlockPos linked = linked(stack);
		if (linked == null) {
			tooltip.add(Component.translatable("tooltip.doommc.remote_unlinked").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("tooltip.doommc.remote_linked",
					linked.getX(), linked.getY(), linked.getZ()).withStyle(ChatFormatting.GREEN));
		}
		tooltip.add(Component.translatable("tooltip.doommc.remote_help").withStyle(ChatFormatting.DARK_GRAY));
	}
}
