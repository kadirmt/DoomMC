package com.akadi.doommc.net;

import com.akadi.doommc.DoomMC;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Every packet the mod sends. Four small ones - no pixels ever cross the wire. */
public final class DoomPayloads {

	// control actions, client to server
	public static final int CONTROL_REQUEST = 0;
	public static final int CONTROL_RELEASE = 1;
	public static final int CONTROL_STOP = 2;

	// session actions, server to client
	public static final int SESSION_GRANTED = 0;
	public static final int SESSION_ENDED = 1;
	public static final int SESSION_BUSY = 2;

	private DoomPayloads() {
	}

	/** "I want to drive this screen" / "I am done" / "shut it down". */
	public record Control(BlockPos screen, int action) implements CustomPayload {
		public static final CustomPayload.Id<Control> ID =
				new CustomPayload.Id<>(DoomMC.id("control"));

		public static final PacketCodec<PacketByteBuf, Control> CODEC = PacketCodec.of(
				(value, buf) -> {
					buf.writeBlockPos(value.screen());
					buf.writeByte(value.action());
				},
				buf -> new Control(buf.readBlockPos(), buf.readByte()));

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The controller's key bitmask plus however far the mouse moved since last time. */
	public record Input(BlockPos screen, int keys, float turn) implements CustomPayload {
		public static final CustomPayload.Id<Input> ID =
				new CustomPayload.Id<>(DoomMC.id("input"));

		public static final PacketCodec<PacketByteBuf, Input> CODEC = PacketCodec.of(
				(value, buf) -> {
					buf.writeBlockPos(value.screen());
					buf.writeVarInt(value.keys());
					buf.writeFloat(value.turn());
				},
				buf -> new Input(buf.readBlockPos(), buf.readVarInt(), buf.readFloat()));

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** One tick of drawable state, broadcast to everyone who can see the wall. */
	public record Frame(BlockPos screen, int facing, byte[] data) implements CustomPayload {
		public static final CustomPayload.Id<Frame> ID =
				new CustomPayload.Id<>(DoomMC.id("frame"));

		public static final PacketCodec<PacketByteBuf, Frame> CODEC = PacketCodec.of(
				(value, buf) -> {
					buf.writeBlockPos(value.screen());
					buf.writeByte(value.facing());
					buf.writeByteArray(value.data());
				},
				buf -> new Frame(buf.readBlockPos(), buf.readByte(), buf.readByteArray()));

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Server telling one client whether it got the controls, or that the game is over. */
	public record Session(BlockPos screen, int action) implements CustomPayload {
		public static final CustomPayload.Id<Session> ID =
				new CustomPayload.Id<>(DoomMC.id("session"));

		public static final PacketCodec<PacketByteBuf, Session> CODEC = PacketCodec.of(
				(value, buf) -> {
					buf.writeBlockPos(value.screen());
					buf.writeByte(value.action());
				},
				buf -> new Session(buf.readBlockPos(), buf.readByte()));

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
