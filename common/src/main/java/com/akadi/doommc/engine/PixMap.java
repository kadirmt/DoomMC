package com.akadi.doommc.engine;

/**
 * A tiny ARGB image with the handful of drawing primitives the engine needs.
 * Alpha is all-or-nothing: 0x00 means "skip this pixel", anything else is opaque.
 */
public final class PixMap {
	public final int w;
	public final int h;
	public final int[] px;

	public PixMap(int w, int h) {
		this.w = w;
		this.h = h;
		this.px = new int[w * h];
	}

	public void clear(int argb) {
		java.util.Arrays.fill(px, argb);
	}

	public void set(int x, int y, int argb) {
		if (x < 0 || y < 0 || x >= w || y >= h) return;
		px[y * w + x] = argb;
	}

	public int get(int x, int y) {
		if (x < 0 || y < 0 || x >= w || y >= h) return 0;
		return px[y * w + x];
	}

	/** Sample with wrapping - used for tiling wall textures. */
	public int wrap(int x, int y) {
		return px[Math.floorMod(y, h) * w + Math.floorMod(x, w)];
	}

	public void fillRect(int x0, int y0, int rw, int rh, int c) {
		for (int y = y0; y < y0 + rh; y++) {
			if (y < 0 || y >= h) continue;
			for (int x = x0; x < x0 + rw; x++) {
				if (x < 0 || x >= w) continue;
				px[y * w + x] = c;
			}
		}
	}

	public void rectOutline(int x0, int y0, int rw, int rh, int c) {
		for (int x = x0; x < x0 + rw; x++) {
			set(x, y0, c);
			set(x, y0 + rh - 1, c);
		}
		for (int y = y0; y < y0 + rh; y++) {
			set(x0, y, c);
			set(x0 + rw - 1, y, c);
		}
	}

	public void fillEllipse(double cx, double cy, double rx, double ry, int c) {
		if (rx <= 0 || ry <= 0) return;
		int x0 = (int) Math.floor(cx - rx);
		int x1 = (int) Math.ceil(cx + rx);
		int y0 = (int) Math.floor(cy - ry);
		int y1 = (int) Math.ceil(cy + ry);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				double dx = (x - cx) / rx;
				double dy = (y - cy) / ry;
				if (dx * dx + dy * dy <= 1.0) set(x, y, c);
			}
		}
	}

	/** Ellipse with a darker rim, which is enough to read as volume at these sizes. */
	public void shadedEllipse(double cx, double cy, double rx, double ry, int c) {
		int dark = Art.mul(c, 0.55f);
		int lit = Art.mul(c, 1.25f);
		int x0 = (int) Math.floor(cx - rx);
		int x1 = (int) Math.ceil(cx + rx);
		int y0 = (int) Math.floor(cy - ry);
		int y1 = (int) Math.ceil(cy + ry);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				double dx = (x - cx) / rx;
				double dy = (y - cy) / ry;
				double d = dx * dx + dy * dy;
				if (d > 1.0) continue;
				// light comes from the upper left
				double l = 1.0 - (dx * 0.45 + dy * 0.45);
				int col = d > 0.78 ? dark : (l > 1.25 ? lit : c);
				set(x, y, col);
			}
		}
	}

	public void fillTri(int ax, int ay, int bx, int by, int cx, int cy, int c) {
		int minX = Math.min(ax, Math.min(bx, cx));
		int maxX = Math.max(ax, Math.max(bx, cx));
		int minY = Math.min(ay, Math.min(by, cy));
		int maxY = Math.max(ay, Math.max(by, cy));
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				int d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by);
				int d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy);
				int d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
				boolean neg = (d1 < 0) || (d2 < 0) || (d3 < 0);
				boolean pos = (d1 > 0) || (d2 > 0) || (d3 > 0);
				if (!(neg && pos)) set(x, y, c);
			}
		}
	}

	public void line(int x0, int y0, int x1, int y1, int c) {
		int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
		int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		while (true) {
			set(x0, y0, c);
			if (x0 == x1 && y0 == y1) break;
			int e2 = 2 * err;
			if (e2 >= dy) { err += dy; x0 += sx; }
			if (e2 <= dx) { err += dx; y0 += sy; }
		}
	}

	/** Blit onto a raw framebuffer, skipping transparent pixels. */
	public void blit(int[] dst, int dstW, int dstH, int dx, int dy) {
		for (int y = 0; y < h; y++) {
			int ty = dy + y;
			if (ty < 0 || ty >= dstH) continue;
			for (int x = 0; x < w; x++) {
				int tx = dx + x;
				if (tx < 0 || tx >= dstW) continue;
				int c = px[y * w + x];
				if ((c & 0xFF000000) == 0) continue;
				dst[ty * dstW + tx] = c;
			}
		}
	}

	/** Nearest-neighbour resize. Used once at load time to shrink the weapon sprites. */
	public PixMap scaled(double s) {
		PixMap out = new PixMap(Math.max(1, (int) (w * s)), Math.max(1, (int) (h * s)));
		for (int y = 0; y < out.h; y++) {
			int sy = Math.min(h - 1, (int) (y / s));
			for (int x = 0; x < out.w; x++) {
				int sx = Math.min(w - 1, (int) (x / s));
				out.px[y * out.w + x] = px[sy * w + sx];
			}
		}
		return out;
	}

	/** Blit with a uniform brightness multiplier - used for the muzzle-flash lit weapon. */
	public void blitShaded(int[] dst, int dstW, int dstH, int dx, int dy, float light) {
		for (int y = 0; y < h; y++) {
			int ty = dy + y;
			if (ty < 0 || ty >= dstH) continue;
			for (int x = 0; x < w; x++) {
				int tx = dx + x;
				if (tx < 0 || tx >= dstW) continue;
				int c = px[y * w + x];
				if ((c & 0xFF000000) == 0) continue;
				dst[ty * dstW + tx] = Art.mul(c, light);
			}
		}
	}
}
