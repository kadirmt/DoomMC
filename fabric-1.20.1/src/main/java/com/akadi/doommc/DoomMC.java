package com.akadi.doommc;

import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.item.RemoteItem;
import com.akadi.doommc.item.ScreenItem;
import com.akadi.doommc.net.DoomNet;
import com.akadi.doommc.server.ServerDoom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class DoomMC implements ModInitializer {
	public static final String MOD_ID = "doommc";

	/** The wall the Screen item builds: 25 wide, 16 tall (320x200 is 8:5, this is the closest whole block fit). */
	public static final int SCREEN_WIDTH = 25;
	public static final int SCREEN_HEIGHT = 16;

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	public static final Block SCREEN_BLOCK = new ScreenBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.strength(1.5f, 6.0f)
			.sounds(BlockSoundGroup.METAL)
			.nonOpaque()
			.dropsNothing());

	public static final Item SCREEN_ITEM = new ScreenItem(new Item.Settings().maxCount(16));
	public static final Item REMOTE_ITEM = new RemoteItem(new Item.Settings().maxCount(1));

	public static final ItemGroup GROUP = FabricItemGroup.builder()
			.icon(() -> new ItemStack(SCREEN_ITEM))
			.displayName(Text.translatable("itemGroup.doommc.main"))
			.entries((ctx, entries) -> {
				entries.add(SCREEN_ITEM);
				entries.add(REMOTE_ITEM);
			})
			.build();

	@Override
	public void onInitialize() {
		Registry.register(Registries.BLOCK, id("doom_screen"), SCREEN_BLOCK);
		Registry.register(Registries.ITEM, id("doom_screen"), SCREEN_ITEM);
		Registry.register(Registries.ITEM, id("doom_remote"), REMOTE_ITEM);
		Registry.register(Registries.ITEM_GROUP, id("main"), GROUP);

		DoomNet.registerServer();

		ServerTickEvents.END_SERVER_TICK.register(ServerDoom::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ServerDoom.onDisconnect(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerDoom.onServerStopping());
	}

	/** True when the state is one of our screen blocks. */
	public static boolean isScreen(BlockState state) {
		return state.isOf(SCREEN_BLOCK);
	}
}
