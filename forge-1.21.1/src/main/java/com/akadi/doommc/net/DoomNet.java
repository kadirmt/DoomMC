package com.akadi.doommc.net;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.client.DoomClient;
import com.akadi.doommc.server.ServerDoom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Forge 1.21.1 networking - the rebuilt ChannelBuilder API, same four messages. */
public final class DoomNet {

	public static final SimpleChannel CHANNEL = ChannelBuilder
			.named(DoomMC.id("main"))
			.networkProtocolVersion(1)
			.simpleChannel();

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
		CHANNEL.messageBuilder(ControlMsg.class, NetworkDirection.PLAY_TO_SERVER)
				.encoder(ControlMsg::encode)
				.decoder(ControlMsg::decode)
				.consumerMainThread(ControlMsg::handle)
				.add();
		CHANNEL.messageBuilder(InputMsg.class, NetworkDirection.PLAY_TO_SERVER)
				.encoder(InputMsg::encode)
				.decoder(InputMsg::decode)
				.consumerMainThread(InputMsg::handle)
				.add();
		CHANNEL.messageBuilder(FrameMsg.class, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(FrameMsg::encode)
				.decoder(FrameMsg::decode)
				.consumerMainThread(FrameMsg::handle)
				.add();
		CHANNEL.messageBuilder(SessionMsg.class, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(SessionMsg::encode)
				.decoder(SessionMsg::decode)
				.consumerMainThread(SessionMsg::handle)
				.add();
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

		public static void handle(ControlMsg msg, CustomPayloadEvent.Context ctx) {
			ServerPlayer sender = ctx.getSender();
			if (sender == null) return;
			switch (msg.action) {
				case CONTROL_REQUEST -> ServerDoom.requestControl(sender, msg.screen);
				case CONTROL_RELEASE -> ServerDoom.releaseControl(sender, msg.screen);
				case CONTROL_STOP -> ServerDoom.stop(sender, msg.screen);
				default -> {
				}
			}
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

		public static void handle(InputMsg msg, CustomPayloadEvent.Context ctx) {
			ServerPlayer sender = ctx.getSender();
			if (sender != null) ServerDoom.input(sender, msg.screen, msg.keys, msg.turn);
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

		public static void handle(FrameMsg msg, CustomPayloadEvent.Context ctx) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> DoomClient.onFrame(msg.screen, msg.facing, msg.data));
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

		public static void handle(SessionMsg msg, CustomPayloadEvent.Context ctx) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> DoomClient.onSession(msg.screen, msg.action));
		}
	}

	// ---------------------------------------------------------------- sends

	public static void sendFrame(ServerPlayer to, BlockPos screen, int facing, byte[] data) {
		CHANNEL.send(new FrameMsg(screen, facing, data), PacketDistributor.PLAYER.with(to));
	}

	public static void sendSession(ServerPlayer to, BlockPos screen, int action) {
		CHANNEL.send(new SessionMsg(screen, action), PacketDistributor.PLAYER.with(to));
	}

	public static void sendControl(BlockPos screen, int action) {
		CHANNEL.send(new ControlMsg(screen, action), PacketDistributor.SERVER.noArg());
	}

	public static void sendInput(BlockPos screen, int keys, float turn) {
		CHANNEL.send(new InputMsg(screen, keys, turn), PacketDistributor.SERVER.noArg());
	}
}
