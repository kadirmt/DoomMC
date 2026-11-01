package com.akadi.doommc.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Paints each running game onto its wall. Done from the world render event rather
 * than a block entity renderer so a 25 block wide picture never gets culled.
 */
public final class DoomWorldRenderer {

	/** Fully lit, so the picture glows in a dark room the way a real screen would. */
	private static final int FULLBRIGHT = 0xF000F0;

	/** Nudge the quad off the block face so it does not fight with the block texture. */
	private static final float LIFT = 0.01f;

	private DoomWorldRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		if (DoomClient.isEmpty()) return;
		MatrixStack matrices = ctx.matrixStack();
		if (matrices == null || ctx.camera() == null) return;

		Vec3d cam = ctx.camera().getPos();
		VertexConsumerProvider.Immediate buffers =
				MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

		for (DoomSession session : DoomClient.sessions()) {
			if (session.isClosed() || !session.hasPicture()) continue;

			var layout = session.layout();
			Direction normal = session.facing;
			Direction right = layout.right();
			int width = layout.width();
			int height = layout.height();

			// corner of the anchor block that is the bottom left of the picture
			float ax = 0f, ay = 0f, az = 0f;
			if (normal.getOffsetX() > 0) ax += 1f;
			if (normal.getOffsetZ() > 0) az += 1f;
			if (right.getOffsetX() < 0) ax += 1f;
			if (right.getOffsetZ() < 0) az += 1f;
			ax += normal.getOffsetX() * LIFT;
			az += normal.getOffsetZ() * LIFT;

			float wx = right.getOffsetX() * width;
			float wz = right.getOffsetZ() * width;

			RenderLayer layer = RenderLayer.getEntitySolid(session.textureId);
			VertexConsumer vc = buffers.getBuffer(layer);

			matrices.push();
			matrices.translate(session.origin.getX() - cam.x,
					session.origin.getY() - cam.y,
					session.origin.getZ() - cam.z);
			MatrixStack.Entry entry = matrices.peek();

			// front face, then the same quad wound the other way so culling can never hide it
			vertex(vc, entry, normal, ax, ay, az, 0f, 1f);
			vertex(vc, entry, normal, ax + wx, ay, az + wz, 1f, 1f);
			vertex(vc, entry, normal, ax + wx, ay + height, az + wz, 1f, 0f);
			vertex(vc, entry, normal, ax, ay + height, az, 0f, 0f);

			vertex(vc, entry, normal, ax, ay + height, az, 0f, 0f);
			vertex(vc, entry, normal, ax + wx, ay + height, az + wz, 1f, 0f);
			vertex(vc, entry, normal, ax + wx, ay, az + wz, 1f, 1f);
			vertex(vc, entry, normal, ax, ay, az, 0f, 1f);

			matrices.pop();
			buffers.draw(layer);
		}
	}

	private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, Direction normal,
			float x, float y, float z, float u, float v) {
		vc.vertex(entry.getPositionMatrix(), x, y, z)
				.color(255, 255, 255, 255)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(FULLBRIGHT)
				.normal(entry.getNormalMatrix(), normal.getOffsetX(), normal.getOffsetY(), normal.getOffsetZ())
				.next();
	}
}
