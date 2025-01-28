package de.voasis;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static InstanceContainer instanceContainer;
    private static ThreadLocalRandom random = ThreadLocalRandom.current();
    private static List<Pos> placed = Collections.synchronizedList(new ArrayList<>());
    private static List<Pos> spawnedFrom = Collections.synchronizedList(new ArrayList<>());
    private static final Pos startBlock = new Pos(0, 0, 0);
    private static final Pos startPos = new Pos(0.5, 1, 0.5);
    private static int score;
    public static GlobalEventHandler globalEventHandler;
    public static MiniMessage mm = MiniMessage.miniMessage();
    public static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static TimeState currentTimeState = TimeState.MIDNIGHT;
    private static ItemStack clockItem = createClockItem();

    private enum TimeState {
        SUNRISE, NOON, SUNSET, MIDNIGHT
    }

    private static final ItemStack leaveItem = ItemStack.builder(Material.BARRIER)
            .set(ItemComponent.ITEM_NAME, mm.deserialize("<red>Leave</red>"))
            .set(ItemComponent.LORE, List.of(mm.deserialize("<white>Click to leave.</white>").decoration(TextDecoration.ITALIC, false)))
            .build();

    private static ItemStack createClockItem() {
        return ItemStack.builder(Material.CLOCK)
                .set(ItemComponent.ITEM_NAME, mm.deserialize("<yellow>Time Control</yellow>"))
                .set(ItemComponent.LORE, List.of(
                        mm.deserialize("<white>Current time: <green>" + currentTimeState.name().toLowerCase() + "</green>").decoration(TextDecoration.ITALIC, false),
                        mm.deserialize("<white>Click to toggle time.").decoration(TextDecoration.ITALIC, false)
                ))
                .build();
    }

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setTimeRate(0);
        instanceContainer.setGenerator(unit -> {});
        instanceContainer.setChunkSupplier(LightingChunk::new);
        var vsecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (vsecret != null) {
            VelocityProxy.enable(vsecret);
        }
        globalEventHandler = MinecraftServer.getGlobalEventHandler();
        new NebulaAPI(globalEventHandler, logger);
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(startPos);
        });
        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            player.sendActionBar(Component.text("Use /lobby to leave."));
            player.getInventory().setItemStack(0, leaveItem);
            player.getInventory().setItemStack(8, clockItem);
            resetGame(player);
        });
        globalEventHandler.addListener(ItemDropEvent.class, e -> e.setCancelled(true));
        globalEventHandler.addListener(PlayerChatEvent.class, e -> e.setCancelled(true));
        globalEventHandler.addListener(PlayerMoveEvent.class, e -> update(e.getPlayer()));
        globalEventHandler.addListener(PlayerSpawnEvent.class, e -> update(e.getPlayer()));
        globalEventHandler.addListener(PlayerUseItemEvent.class, e -> itemClick(e.getPlayer(), e.getItemStack()));
        globalEventHandler.addListener(PlayerSwapItemEvent.class, e -> e.setCancelled(true));
        globalEventHandler.addListener(PlayerBlockBreakEvent.class, e -> e.setCancelled(true));
        globalEventHandler.addListener(PlayerBlockPlaceEvent.class, e -> e.setCancelled(true));
        globalEventHandler.addListener(InventoryPreClickEvent.class, e -> {
            e.setCancelled(true);
            itemClick(e.getPlayer(), e.getClickedItem());
        });
        globalEventHandler.addListener(PlayerHandAnimationEvent.class, e -> {
            Player player = e.getPlayer();
            itemClick(player, player.getItemInMainHand());
        });
        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void updateClockItem(Player player) {
        clockItem = createClockItem();
        player.getInventory().setItemStack(8, clockItem);
    }

    private static void itemClick(Player player, ItemStack item) {
        if (item.equals(leaveItem)) {
            String message = "lobby:" + player.getUsername();
            PluginMessagePacket packet = new PluginMessagePacket(
                    "nebula:main",
                    message.getBytes(StandardCharsets.UTF_8)
            );
            player.sendPacket(packet);
        }
        if (item.equals(clockItem)) {
            switch (currentTimeState) {
                case SUNRISE -> {
                    instanceContainer.setTime(6000);
                    currentTimeState = TimeState.NOON;
                }
                case NOON -> {
                    instanceContainer.setTime(12000);
                    currentTimeState = TimeState.SUNSET;
                }
                case SUNSET -> {
                    instanceContainer.setTime(17843);
                    currentTimeState = TimeState.MIDNIGHT;
                }
                case MIDNIGHT -> {
                    instanceContainer.setTime(23000);
                    currentTimeState = TimeState.SUNRISE;
                }
            }
            updateClockItem(player);
            player.sendActionBar(Component.text("Time set to " + currentTimeState.name().toLowerCase()));
        }
    }

    private static void update(Player player) {
        score = spawnedFrom.size();
        if (!spawnedFrom.isEmpty()) {
            player.sendActionBar(Component.text("Score: " + score));
        }
        Pos beneath = player.getPosition().withY(player.getPosition().blockY() - 1).withX(player.getPosition().blockX()).withZ(player.getPosition().blockZ()).withYaw(0).withPitch(0);
        if (player.getPosition().y() < -20) {
            resetGame(player);
            return;
        }
        if (beneath.blockZ() == placed.get(placed.size() - 2).blockZ() && !spawnedFrom.contains(beneath)) {
            spawnedFrom.add(beneath);
            spawnNewBlock(player);
        }
    }

    private static void spawnNewBlock(Player player) {
        boolean effect = placed.size() >= 2;
        Block block = getABlock();
        Pos np;
        Pos basePos = placed.getLast();
        do {
            int offX = random.nextInt(-1, 2);
            int offY = random.nextInt(-1, 2);
            int offZ = random.nextInt(3, 5);
            if (offZ >= 4 && offY == 1) {
                offZ--;
            }
            np = new Pos(basePos.x() + offX, basePos.y() + offY, basePos.z() + offZ, 0, 0);
        } while ((instanceContainer.isInVoid(np) || np.y() < -10 || placed.contains(np)));
        placed.add(np);
        if (!effect) {
            instanceContainer.setBlock(np, block);
            placed.add(np);
            return;
        }
        player.playSound(Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_HIT, Sound.Source.MASTER, 999, 1));
        Entity e = new Entity(EntityType.BLOCK_DISPLAY);
        Pos finalNp = np;
        e.setNoGravity(true);
        e.editEntityMeta(BlockDisplayMeta.class, m -> {
            m.setBlockState(block);
            m.setPosRotInterpolationDuration(5);
            m.setHasGlowingEffect(true);
        });
        e.setInstance(instanceContainer, basePos).thenRun(() -> {
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                if (e.isRemoved()) return TaskSchedule.stop();
                e.teleport(finalNp);
                return TaskSchedule.tick(1);
            }, TaskSchedule.tick(1));
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                if (e.isRemoved()) return TaskSchedule.stop();
                instanceContainer.setBlock(finalNp, block);
                player.sendPacket(new ParticlePacket(Particle.POOF, finalNp.add(new Pos(0.5, 0.5, 0.5)), new Pos(0.5, 0.5, 0.5), 0.1f, 20));
                e.remove();
                return TaskSchedule.stop();
            }, TaskSchedule.tick(10));
        });
    }

    private static void resetGame(Player player) {
        for (Entity entity : instanceContainer.getEntities()) {
            if (entity.getEntityType() == EntityType.BLOCK_DISPLAY) {
                entity.remove();
            }
        }
        for (Pos pos : placed) {
            instanceContainer.setBlock(pos, Block.AIR);
        }
        placed.clear();
        spawnedFrom.clear();
        instanceContainer.setBlock(startBlock, Block.GOLD_BLOCK);
        placed.add(startBlock);
        score = 0;
        player.teleport(startPos);
        spawnNewBlock(player);
        spawnNewBlock(player);
        spawnNewBlock(player);
        player.sendActionBar(Component.text("Use /lobby to leave."));
    }

    public static Block getABlock() {
        int r = random.nextInt(4);
        return switch (r) {
            case 1 -> Block.GRASS_BLOCK;
            case 2 -> Block.STONE_BRICKS;
            case 3 -> Block.RED_MUSHROOM_BLOCK;
            default -> Block.AZALEA_LEAVES;
        };
    }
}