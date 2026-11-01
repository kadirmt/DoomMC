package com.akadi.doommc.net;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.server.ServerDoom;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 1.20.1 networking - no payload objects yet, just channels and raw buffers.
 * The wire layout mirrors the 1.21.1 payloads so the two feel identical to debug.
 */
public final class DoomNet {
	public static final Identifier CONTROL = DoomMC.id("control");
	public static final Identifier INPUT = DoomMC.id("input");
	public static final Identifier FRAME = DoomMC.id("frame");
	public static final Identifier SESSION = DoomMC.id("session");

	// control actions, client to server
	public static final int CONTROL_REQUEST = 0;
	public static final int CONTROL_RELEASE = 1;
	public static final int CONTROL_STOP = 2;

	// session actions, server to client
	public static final int SESSION_GRANTED = 0;
	public static final int SESSION_ENDED = 1;
	public static final int SESSION_BUSY = 2;

	private DoomNet() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(CONTROL, (server, player, handler, buf, sender) -> {
			BlockPos screen = buf.readBlockPos();
			int action = buf.readByte();
			server.execute(() -> {
				switch (action) {
					case CONTROL_REQUEST -> ServerDoom.requestControl(player, screen);
					case CONTROL_RELEASE -> ServerDoom.releaseControl(player, screen);
					case CONTROL_STOP -> ServerDoom.stop(player, screen);
					default -> {
					}
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(INPUT, (server, player, handler, buf, sender) -> {
			BlockPos screen = buf.readBlockPos();
			int keys = buf.readVarInt();
			float turn = buf.readFloat();
			server.execute(() -> ServerDoom.input(player, screen, keys, turn));
		});
	}

	// ------------------------------------------------------- server to client

	public static void sendFrame(ServerPlayerEntity to, BlockPos screen, int facing, byte[] data) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(screen);
		buf.writeByte(facing);
		buf.writeByteArray(data);
		ServerPlayNetworking.send(to, FRAME, buf);
	}

	public static void sendSession(ServerPlayerEntity to, BlockPos screen, int action) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(screen);
		buf.writeByte(action);
		ServerPlayNetworking.send(to, SESSION, buf);
	}
}
