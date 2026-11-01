package com.akadi.doommc.client;

import com.akadi.doommc.net.DoomNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.util.math.BlockPos;

public class DoomMCClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(DoomNet.FRAME, (client, handler, buf, sender) -> {
			BlockPos origin = buf.readBlockPos();
			int facing = buf.readByte();
			byte[] data = buf.readByteArray();
			client.execute(() -> DoomClient.onFrame(origin, facing, data));
		});

		ClientPlayNetworking.registerGlobalReceiver(DoomNet.SESSION, (client, handler, buf, sender) -> {
			BlockPos origin = buf.readBlockPos();
			int action = buf.readByte();
			client.execute(() -> DoomClient.onSession(origin, action));
		});

		// redraw from the latest snapshot, then paint it onto the wall
		WorldRenderEvents.START.register(ctx -> DoomClient.redrawAll());
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DoomWorldRenderer::render);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DoomClient.closeAll());
	}
}
