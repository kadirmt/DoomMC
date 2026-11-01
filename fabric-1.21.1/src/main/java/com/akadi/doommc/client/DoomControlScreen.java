package com.akadi.doommc.client;

import com.akadi.doommc.engine.DoomGame;
import com.akadi.doommc.net.DoomPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Draws almost nothing on purpose. You keep looking at the Minecraft world and play
 * on the wall in front of you; this exists to grab the keyboard and the mouse, so
 * WASD drives the marine rather than your player and E does not open a chest.
 */
public class DoomControlScreen extends Screen {
	public static final int EXIT_KEY = GLFW.GLFW_KEY_F10;

	/** Radians of turn per pixel of mouse travel, before the sensitivity option. */
	private static final double TURN_PER_PIXEL = 0.0025;
	/** Do not flood the server: at most one input packet per Minecraft tick. */
	private static final long MIN_SEND_NANOS = 45_000_000L;

	private final BlockPos screenPos;

	private int keyMask;
	private int sentMask = -1;
	private double pendingTurn;
	private long lastSend;

	private boolean haveMouseOrigin;
	private double lastMouseX;

	public DoomControlScreen(BlockPos screenPos) {
		super(Text.translatable("screen.doommc.title"));
		this.screenPos = screenPos.toImmutable();
	}

	public BlockPos screenPos() {
		return screenPos;
	}

	@Override
	public boolean shouldPause() {
		return false; // the world keeps running - creepers do not wait for you to finish
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// no dim, no blur, no panorama - we want to see straight through to the wall
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		grabMouse();
		pollMouse();
		sendInput(false);

		int barHeight = 13;
		int top = this.height - barHeight;
		context.fill(0, top, this.width, this.height, 0xB0000000);
		context.fill(0, top, this.width, top + 1, 0x60FF4020);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("hud.doommc.playing_hint"), this.width / 2, top + 3, 0xFFC8CDD4);
	}

	// --------------------------------------------------------------- mouse

	/**
	 * Minecraft frees the cursor whenever a screen opens, which makes aiming
	 * impossible. Take it back, and keep taking it back after alt-tab.
	 */
	private void grabMouse() {
		MinecraftClient mc = MinecraftClient.getInstance();
		long handle = mc.getWindow().getHandle();
		if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) {
			GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
			haveMouseOrigin = false;
		}
	}

	private void releaseMouse() {
		MinecraftClient mc = MinecraftClient.getInstance();
		GLFW.glfwSetInputMode(mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
	}

	/**
	 * With the cursor disabled GLFW reports unbounded virtual coordinates, so the
	 * difference between frames is raw mouse travel in pixels.
	 */
	private void pollMouse() {
		MinecraftClient mc = MinecraftClient.getInstance();
		double[] x = new double[1];
		double[] y = new double[1];
		GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), x, y);

		if (!haveMouseOrigin) {
			lastMouseX = x[0];
			haveMouseOrigin = true;
			return;
		}
		double dx = x[0] - lastMouseX;
		lastMouseX = x[0];
		if (dx == 0) return;

		double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
		double scale = sensitivity * sensitivity * sensitivity * 8.0;
		pendingTurn += dx * TURN_PER_PIXEL * scale;
	}

	// --------------------------------------------------------------- input

	private void sendInput(boolean force) {
		long now = System.nanoTime();
		boolean keysChanged = keyMask != sentMask;
		boolean turned = pendingTurn != 0;
		if (!force && !keysChanged && !turned) return;
		if (!force && !keysChanged && now - lastSend < MIN_SEND_NANOS) return;

		ClientPlayNetworking.send(new DoomPayloads.Input(screenPos, keyMask, (float) pendingTurn));
		sentMask = keyMask;
		pendingTurn = 0;
		lastSend = now;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == EXIT_KEY) {
			this.close();
			return true;
		}
		int action = mapKey(keyCode);
		if (action >= 0) {
			keyMask |= 1 << action;
			sendInput(false);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		int action = mapKey(keyCode);
		if (action >= 0) {
			keyMask &= ~(1 << action);
			sendInput(false);
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			keyMask |= 1 << DoomGame.K_FIRE;
			sendInput(false);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			keyMask &= ~(1 << DoomGame.K_FIRE);
			sendInput(false);
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void removed() {
		releaseMouse();
		keyMask = 0;
		pendingTurn = 0;
		sendInput(true); // release every key before we let go
		ClientPlayNetworking.send(
				new DoomPayloads.Control(screenPos, DoomPayloads.CONTROL_RELEASE));
		super.removed();
	}

	private static int mapKey(int key) {
		return switch (key) {
			case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> DoomGame.K_FWD;
			case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> DoomGame.K_BACK;
			case GLFW.GLFW_KEY_LEFT -> DoomGame.K_TURN_L;
			case GLFW.GLFW_KEY_RIGHT -> DoomGame.K_TURN_R;
			// with mouse look on, A and D are far more useful as strafe
			case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_Q -> DoomGame.K_STRAFE_L;
			case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_E -> DoomGame.K_STRAFE_R;
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> DoomGame.K_FIRE;
			case GLFW.GLFW_KEY_SPACE -> DoomGame.K_USE;
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> DoomGame.K_RUN;
			case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> DoomGame.K_W1;
			case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> DoomGame.K_W2;
			case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> DoomGame.K_W3;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> DoomGame.K_ENTER;
			default -> -1;
		};
	}
}
