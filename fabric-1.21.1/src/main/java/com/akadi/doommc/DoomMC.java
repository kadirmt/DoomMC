package com.akadi.doommc;

import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.item.RemoteItem;
import com.akadi.doommc.item.ScreenItem;
import com.akadi.doommc.net.DoomPayloads;
import com.akadi.doommc.server.ServerDoom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class DoomMC implements ModInitializer {
	public static final String MOD_ID = "doommc";

	/** The wall the Screen item builds: 25 wide, 16 tall (320x200 is 8:5, this is the closest whole block fit). */
	public static final int SCREEN_WIDTH = 25;
	public static final int SCREEN_HEIGHT = 16;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static final Block SCREEN_BLOCK = new ScreenBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK)
			.strength(1.5f, 6.0f)
			.sounds(BlockSoundGroup.METAL)
			.nonOpaque()
			.dropsNothing());

	public static final Item SCREEN_ITEM = new ScreenItem(new Item.Settings().maxCount(16));
	public static final Item REMOTE_ITEM = new RemoteItem(new Item.Settings().maxCount(1));

	/** Which screen a remote is bound to. Stored on the stack, so it survives inventories and chests. */
	public static final ComponentType<BlockPos> LINKED_SCREEN = ComponentType.<BlockPos>builder()
			.codec(BlockPos.CODEC)
			.packetCodec(BlockPos.PACKET_CODEC)
			.build();

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
		Registry.register(Registries.DATA_COMPONENT_TYPE, id("linked_screen"), LINKED_SCREEN);
		Registry.register(Registries.BLOCK, id("doom_screen"), SCREEN_BLOCK);
		Registry.register(Registries.ITEM, id("doom_screen"), SCREEN_ITEM);
		Registry.register(Registries.ITEM, id("doom_remote"), REMOTE_ITEM);
		Registry.register(Registries.ITEM_GROUP, id("main"), GROUP);

		PayloadTypeRegistry.playC2S().register(DoomPayloads.Control.ID, DoomPayloads.Control.CODEC);
		PayloadTypeRegistry.playC2S().register(DoomPayloads.Input.ID, DoomPayloads.Input.CODEC);
		PayloadTypeRegistry.playS2C().register(DoomPayloads.Frame.ID, DoomPayloads.Frame.CODEC);
		PayloadTypeRegistry.playS2C().register(DoomPayloads.Session.ID, DoomPayloads.Session.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DoomPayloads.Control.ID, (payload, context) ->
				context.player().server.execute(() -> {
					switch (payload.action()) {
						case DoomPayloads.CONTROL_REQUEST ->
								ServerDoom.requestControl(context.player(), payload.screen());
						case DoomPayloads.CONTROL_RELEASE ->
								ServerDoom.releaseControl(context.player(), payload.screen());
						case DoomPayloads.CONTROL_STOP ->
								ServerDoom.stop(context.player(), payload.screen());
						default -> {
						}
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(DoomPayloads.Input.ID, (payload, context) ->
				context.player().server.execute(() ->
						ServerDoom.input(context.player(), payload.screen(), payload.keys(), payload.turn())));

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
