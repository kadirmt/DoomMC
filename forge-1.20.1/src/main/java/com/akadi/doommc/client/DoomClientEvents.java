package com.akadi.doommc.client;

import com.akadi.doommc.DoomMC;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client hooks: redraw every live game each frame and paint it onto its wall.
 * Done from the world render event so a 25 block wide picture never gets culled.
 */
@Mod.EventBusSubscriber(modid = DoomMC.MOD_ID, value = Dist.CLIENT)
public final class DoomClientEvents {

	/** Fully lit, so the picture glows in a dark room the way a real screen would. */
	private static final int FULLBRIGHT = 0xF000F0;

	/** Nudge the quad off the block face so it does not fight with the block texture. */
	private static final float LIFT = 0.01f;

	private DoomClientEvents() {
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
		DoomClient.redrawAll();
		render(event.getPoseStack(), event.getCamera());
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		DoomClient.closeAll();
	}

	private static void render(PoseStack poseStack, Camera camera) {
		if (DoomClient.isEmpty()) return;

		Vec3 cam = camera.getPosition();
		MultiBufferSource.BufferSource buffers =
				Minecraft.getInstance().renderBuffers().bufferSource();

		for (DoomSession session : DoomClient.sessions()) {
			if (session.isClosed() || !session.hasPicture()) continue;

			var layout = session.layout();
			Direction normal = session.facing;
			Direction right = layout.right();
			int width = layout.width();
			int height = layout.height();

			// corner of the anchor block that is the bottom left of the picture
			float ax = 0f, ay = 0f, az = 0f;
			if (normal.getStepX() > 0) ax += 1f;
			if (normal.getStepZ() > 0) az += 1f;
			if (right.getStepX() < 0) ax += 1f;
			if (right.getStepZ() < 0) az += 1f;
			ax += normal.getStepX() * LIFT;
			az += normal.getStepZ() * LIFT;

			float wx = right.getStepX() * width;
			float wz = right.getStepZ() * width;

			RenderType layer = RenderType.entitySolid(session.textureId);
			VertexConsumer vc = buffers.getBuffer(layer);

			poseStack.pushPose();
			poseStack.translate(session.origin.getX() - cam.x,
					session.origin.getY() - cam.y,
					session.origin.getZ() - cam.z);
			PoseStack.Pose pose = poseStack.last();

			// front face, then the same quad wound the other way so culling can never hide it
			vertex(vc, pose, normal, ax, ay, az, 0f, 1f);
			vertex(vc, pose, normal, ax + wx, ay, az + wz, 1f, 1f);
			vertex(vc, pose, normal, ax + wx, ay + height, az + wz, 1f, 0f);
			vertex(vc, pose, normal, ax, ay + height, az, 0f, 0f);

			vertex(vc, pose, normal, ax, ay + height, az, 0f, 0f);
			vertex(vc, pose, normal, ax + wx, ay + height, az + wz, 1f, 0f);
			vertex(vc, pose, normal, ax + wx, ay, az + wz, 1f, 1f);
			vertex(vc, pose, normal, ax, ay, az, 0f, 1f);

			poseStack.popPose();
			buffers.endBatch(layer);
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Direction normal,
			float x, float y, float z, float u, float v) {
		vc.vertex(pose.pose(), x, y, z)
				.color(255, 255, 255, 255)
				.uv(u, v)
				.overlayCoords(OverlayTexture.NO_OVERLAY)
				.uv2(FULLBRIGHT)
				.normal(pose.normal(), normal.getStepX(), normal.getStepY(), normal.getStepZ())
				.endVertex();
	}
}
