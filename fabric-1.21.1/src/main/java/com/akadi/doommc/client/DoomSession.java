package com.akadi.doommc.client;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenLayout;
import com.akadi.doommc.engine.DoomGame;
import com.akadi.doommc.engine.DoomSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * A client's view of one screen. The DoomGame here is only ever a canvas: it is fed
 * snapshots from the server and asked to draw, and never runs a single game tick.
 */
public class DoomSession {
	private static int nextId;

	/** Server sends one snapshot per tick, so two are 50ms apart. */
	private static final double SNAPSHOT_NANOS = 50_000_000.0;

	public final BlockPos origin;
	public Direction facing;
	public final Identifier textureId;

	private final DoomGame canvas = new DoomGame();
	private final NativeImageBackedTexture texture;

	private DoomSnapshot previous;
	private DoomSnapshot current;
	private long currentAt;
	private long lastPacketAt;
	private boolean closed;

	public DoomSession(BlockPos origin, Direction facing) {
		this.origin = origin.toImmutable();
		this.facing = facing;
		this.textureId = DoomMC.id("screen_" + (nextId++));

		NativeImage image = new NativeImage(NativeImage.Format.RGBA, DoomGame.W, DoomGame.H, false);
		this.texture = new NativeImageBackedTexture(image);
		this.texture.setFilter(false, false); // crisp pixels, no smoothing
		MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
	}

	public ScreenLayout layout() {
		return ScreenLayout.of(origin, facing);
	}

	public boolean hasPicture() {
		return current != null;
	}

	public long lastPacketAt() {
		return lastPacketAt;
	}

	public void accept(DoomSnapshot snapshot, Direction newFacing) {
		this.facing = newFacing;
		this.previous = this.current;
		this.current = snapshot;
		this.currentAt = System.nanoTime();
		this.lastPacketAt = this.currentAt;
	}

	/**
	 * Draw at the client's own framerate by blending the last two snapshots. Without
	 * this the wall would visibly run at the server's twenty ticks a second.
	 */
	public void redraw() {
		if (closed || current == null) return;
		float t = (float) Math.min(1.0, (System.nanoTime() - currentAt) / SNAPSHOT_NANOS);
		DoomSnapshot shown = DoomSnapshot.interpolate(previous, current, t);
		canvas.applySnapshot(shown);
		canvas.renderFrame();
		upload();
	}

	private void upload() {
		NativeImage image = texture.getImage();
		if (image == null) return;
		int[] fb = canvas.fb;
		for (int y = 0; y < DoomGame.H; y++) {
			int row = y * DoomGame.W;
			for (int x = 0; x < DoomGame.W; x++) {
				image.setColor(x, y, toAbgr(fb[row + x]));
			}
		}
		texture.upload();
	}

	/** NativeImage in RGBA order reads its ints little endian, so ARGB is byte swapped. */
	private static int toAbgr(int argb) {
		return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
	}

	/** Middle of the wall, so effects sound like they come from the screen. */
	public Vec3d center() {
		ScreenLayout layout = layout();
		BlockPos far = layout.posAt(layout.width() - 1, layout.height() - 1);
		return new Vec3d(
				(origin.getX() + far.getX()) / 2.0 + 0.5,
				(origin.getY() + far.getY()) / 2.0 + 0.5,
				(origin.getZ() + far.getZ()) / 2.0 + 0.5);
	}

	public void close() {
		if (closed) return;
		closed = true;
		MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
		texture.close();
	}

	public boolean isClosed() {
		return closed;
	}
}
