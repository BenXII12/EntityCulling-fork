package dev.tr7zw.entityculling;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.logisticscraft.occlusionculling.OcclusionCullingInstance;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

public abstract class EntityCullingModBase {

    public static EntityCullingModBase instance;
    public OcclusionCullingInstance culling;
    public Set<BlockEntityType<?>> unCullable = new HashSet<>();
    public Set<EntityType<?>> tickCullWhistelist = new HashSet<>();
    public boolean debugHitboxes = false;
    public static boolean enabled = true; // public static to make it faster for the jvm
    public CullTask cullTask;
    private Thread cullThread;
    protected KeyMapping keybind = new KeyMapping("key.entityculling.toggle", -1, "EntityCulling");
    protected boolean pressed = false;
    private boolean configKeysLoaded = false;
	
    public Config config;
    private final File settingsFile = new File("config", "entityculling.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	//stats
	public int renderedBlockEntities = 0;
	public int skippedBlockEntities = 0;
	public int renderedEntities = 0;
	public int skippedEntities = 0;
	public int tickedEntities = 0;
	public int skippedEntityTicks = 0;

	public void onInitialize() {
		instance = this;
        if (settingsFile.exists()) {
            try {
                config = gson.fromJson(new String(Files.readAllBytes(settingsFile.toPath()), StandardCharsets.UTF_8),
                        Config.class);
            } catch (Exception ex) {
                System.out.println("Error while loading config! Creating a new one!");
                ex.printStackTrace();
            }
        }
        if (config == null) {
            config = new Config();
            writeConfig();
        } else {
            if(ConfigUpgrader.upgradeConfig(config)) {
                writeConfig(); // Config got modified
            }
        }
        culling = new OcclusionCullingInstance(config.tracingDistance, new Provider());
        cullTask = new CullTask(culling, unCullable);

		cullThread = new Thread(cullTask, "CullThread");
		cullThread.setUncaughtExceptionHandler((thread, ex) -> {
			System.out.println("The CullingThread has crashed! Please report the following stacktrace!");
			ex.printStackTrace();
		});
		cullThread.start();
		initModloader();
	}
	
    public void writeConfig() {
        if (settingsFile.exists())
            settingsFile.delete();
        try {
            Files.write(settingsFile.toPath(), gson.toJson(config).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
    
    public void worldTick() {
        cullTask.requestCull = true;
    }
    
    @SuppressWarnings("resource")
    public void clientTick() {
        if(!configKeysLoaded) {
            for(String blockId : config.blockEntityWhitelist) {
                Optional<BlockEntityType<?>> block = Registry.BLOCK_ENTITY_TYPE.getOptional(new ResourceLocation(blockId));
                block.ifPresent(b -> {
                    unCullable.add(b);
                });
            }
            for(String entityType : config.tickCullingWhitelist) {
                    Optional<EntityType<?>> entity = Registry.ENTITY_TYPE.getOptional(new ResourceLocation(entityType));
                    entity.ifPresent(e -> {
                        tickCullWhistelist.add(e);
                    });
            }
        }
        if (keybind.isDown()) {
            if (pressed)
                return;
            pressed = true;
            enabled = !enabled;
            LocalPlayer player = Minecraft.getInstance().player;
            if(enabled) {
                if (player != null) {
                    player.sendMessage(new TextComponent("Culling on").withStyle(ChatFormatting.GREEN),
                            Util.NIL_UUID);
                }
            } else {
                if (player != null) {
                    player.sendMessage(new TextComponent("Culling off").withStyle(ChatFormatting.RED),
                            Util.NIL_UUID);
                }
            }
        } else {
            pressed = false;
        }
        cullTask.requestCull = true;
    }

    public abstract void initModloader();
    
    public abstract AABB setupAABB(BlockEntity entity, BlockPos pos);
	
}
