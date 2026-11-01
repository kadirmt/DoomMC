package com.akadi.doommc.client;

import com.akadi.doommc.net.DoomPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class DoomMCClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(DoomPayloads.Frame.ID, (payload, context) ->
				context.client().execute(() -> DoomClient.onFrame(payload)));
		ClientPlayNetworking.registerGlobalReceiver(DoomPayloads.Session.ID, (payload, context) ->
				context.client().execute(() -> DoomClient.onSession(payload)));

		// redraw from the latest snapshot, then paint it onto the wall
		WorldRenderEvents.START.register(ctx -> DoomClient.redrawAll());
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DoomWorldRenderer::render);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DoomClient.closeAll());
	}
}
