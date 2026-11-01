package com.akadi.doommc.net;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.client.DoomClient;
import com.akadi.doommc.server.ServerDoom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Forge 1.20.1 networking - one SimpleChannel carrying the same four messages. */
public final class DoomNet {
	private static final String PROTOCOL = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			DoomMC.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

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

	public static void init() {
		int idx = 0;
		CHANNEL.registerMessage(idx++, ControlMsg.class, ControlMsg::encode, ControlMsg::decode, ControlMsg::handle);
		CHANNEL.registerMessage(idx++, InputMsg.class, InputMsg::encode, InputMsg::decode, InputMsg::handle);
		CHANNEL.registerMessage(idx++, FrameMsg.class, FrameMsg::encode, FrameMsg::decode, FrameMsg::handle);
		CHANNEL.registerMessage(idx++, SessionMsg.class, SessionMsg::encode, SessionMsg::decode, SessionMsg::handle);
	}

	// ------------------------------------------------------------- messages

	public record ControlMsg(BlockPos screen, int action) {
		public static void encode(ControlMsg msg, FriendlyByteBuf buf) {
			buf.writeBlockPos(msg.screen);
			buf.writeByte(msg.action);
		}

		public static ControlMsg decode(FriendlyByteBuf buf) {
			return new ControlMsg(buf.readBlockPos(), buf.readByte());
		}

		public static void handle(ControlMsg msg, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				ServerPlayer sender = ctx.get().getSender();
				if (sender == null) return;
				switch (msg.action) {
					case CONTROL_REQUEST -> ServerDoom.requestControl(sender, msg.screen);
					case CONTROL_RELEASE -> ServerDoom.releaseControl(sender, msg.screen);
					case CONTROL_STOP -> ServerDoom.stop(sender, msg.screen);
					default -> {
					}
				}
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public record InputMsg(BlockPos screen, int keys, float turn) {
		public static void encode(InputMsg msg, FriendlyByteBuf buf) {
			buf.writeBlockPos(msg.screen);
			buf.writeVarInt(msg.keys);
			buf.writeFloat(msg.turn);
		}

		public static InputMsg decode(FriendlyByteBuf buf) {
			return new InputMsg(buf.readBlockPos(), buf.readVarInt(), buf.readFloat());
		}

		public static void handle(InputMsg msg, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				ServerPlayer sender = ctx.get().getSender();
				if (sender != null) ServerDoom.input(sender, msg.screen, msg.keys, msg.turn);
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public record FrameMsg(BlockPos screen, int facing, byte[] data) {
		public static void encode(FrameMsg msg, FriendlyByteBuf buf) {
			buf.writeBlockPos(msg.screen);
			buf.writeByte(msg.facing);
			buf.writeByteArray(msg.data);
		}

		public static FrameMsg decode(FriendlyByteBuf buf) {
			return new FrameMsg(buf.readBlockPos(), buf.readByte(), buf.readByteArray());
		}

		public static void handle(FrameMsg msg, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> DoomClient.onFrame(msg.screen, msg.facing, msg.data)));
			ctx.get().setPacketHandled(true);
		}
	}

	public record SessionMsg(BlockPos screen, int action) {
		public static void encode(SessionMsg msg, FriendlyByteBuf buf) {
			buf.writeBlockPos(msg.screen);
			buf.writeByte(msg.action);
		}

		public static SessionMsg decode(FriendlyByteBuf buf) {
			return new SessionMsg(buf.readBlockPos(), buf.readByte());
		}

		public static void handle(SessionMsg msg, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> DoomClient.onSession(msg.screen, msg.action)));
			ctx.get().setPacketHandled(true);
		}
	}

	// ---------------------------------------------------------------- sends

	public static void sendFrame(ServerPlayer to, BlockPos screen, int facing, byte[] data) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new FrameMsg(screen, facing, data));
	}

	public static void sendSession(ServerPlayer to, BlockPos screen, int action) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new SessionMsg(screen, action));
	}

	public static void sendControl(BlockPos screen, int action) {
		CHANNEL.sendToServer(new ControlMsg(screen, action));
	}

	public static void sendInput(BlockPos screen, int keys, float turn) {
		CHANNEL.sendToServer(new InputMsg(screen, keys, turn));
	}
}
