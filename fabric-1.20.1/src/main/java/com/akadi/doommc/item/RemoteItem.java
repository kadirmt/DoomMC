package com.akadi.doommc.item;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenLayout;
import com.akadi.doommc.server.ServerDoom;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The handheld. Right click a screen to bind it, right click anywhere after that to
 * play, sneak plus right click to shut the session down.
 *
 * <p>1.20.1 has no data components, so the paired position lives in item NBT.
 */
public class RemoteItem extends Item {
	private static final String NBT_LINKED = "LinkedScreen";

	public RemoteItem(Settings settings) {
		super(settings);
	}

	@Nullable
	public static BlockPos linked(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(NBT_LINKED)) return null;
		return NbtHelper.toBlockPos(nbt.getCompound(NBT_LINKED));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		World world = ctx.getWorld();
		PlayerEntity player = ctx.getPlayer();
		if (player == null) return ActionResult.PASS;
		if (!DoomMC.isScreen(world.getBlockState(ctx.getBlockPos()))) return ActionResult.PASS;

		ScreenLayout layout = ScreenLayout.resolve(world, ctx.getBlockPos());
		if (layout == null) {
			if (!world.isClient) player.sendMessage(Text.translatable("message.doommc.broken_screen"), true);
			return ActionResult.FAIL;
		}

		if (!world.isClient) {
			ctx.getStack().getOrCreateNbt().put(NBT_LINKED, NbtHelper.fromBlockPos(layout.origin()));
			world.playSound(null, ctx.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
					SoundCategory.BLOCKS, 0.6f, 1.8f);
			player.sendMessage(Text.translatable("message.doommc.linked"), true);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BlockPos linked = linked(stack);

		if (linked == null) {
			if (!world.isClient) user.sendMessage(Text.translatable("message.doommc.not_linked"), true);
			return TypedActionResult.fail(stack);
		}

		// the server owns the game, so it decides who gets the controls
		if (user instanceof ServerPlayerEntity serverPlayer) {
			if (user.isSneaking()) {
				ServerDoom.stop(serverPlayer, linked);
			} else {
				ServerDoom.requestControl(serverPlayer, linked);
			}
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		BlockPos linked = linked(stack);
		if (linked == null) {
			tooltip.add(Text.translatable("tooltip.doommc.remote_unlinked").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.doommc.remote_linked",
					linked.getX(), linked.getY(), linked.getZ()).formatted(Formatting.GREEN));
		}
		tooltip.add(Text.translatable("tooltip.doommc.remote_help").formatted(Formatting.DARK_GRAY));
	}
}
