package com.akadi.doommc;

import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.item.RemoteItem;
import com.akadi.doommc.item.ScreenItem;
import com.akadi.doommc.net.DoomNet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DoomMC.MOD_ID)
public class DoomMC {
	public static final String MOD_ID = "doommc";

	/** The wall the Screen item builds: 25 wide, 16 tall (320x200 is 8:5, this is the closest whole block fit). */
	public static final int SCREEN_WIDTH = 25;
	public static final int SCREEN_HEIGHT = 16;

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

	public static final RegistryObject<Block> SCREEN_BLOCK = BLOCKS.register("doom_screen",
			() -> new ScreenBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(1.5f, 6.0f)
					.sound(SoundType.METAL)
					.noOcclusion()
					.noLootTable()));

	public static final RegistryObject<Item> SCREEN_ITEM = ITEMS.register("doom_screen",
			() -> new ScreenItem(new Item.Properties().stacksTo(16)));
	public static final RegistryObject<Item> REMOTE_ITEM = ITEMS.register("doom_remote",
			() -> new RemoteItem(new Item.Properties().stacksTo(1)));

	public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.doommc.main"))
					.icon(() -> new ItemStack(SCREEN_ITEM.get()))
					.displayItems((params, out) -> {
						out.accept(SCREEN_ITEM.get());
						out.accept(REMOTE_ITEM.get());
					})
					.build());

	public DoomMC() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(bus);
		ITEMS.register(bus);
		TABS.register(bus);
		DoomNet.init();
	}

	/** True when the state is one of our screen blocks. */
	public static boolean isScreen(BlockState state) {
		return state.is(SCREEN_BLOCK.get());
	}
}
