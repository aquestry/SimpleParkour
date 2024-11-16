package de.voasis;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import org.jetbrains.annotations.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static InstanceContainer instanceContainer;
    private static final Map<Player, Pos> parkourPositions = new HashMap<>();

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> {
            for (int y = 50; y < 60; y++) {
                unit.modifier().fillHeight(y, y + 1, Block.GRASS_BLOCK);
            }
            unit.modifier().fillHeight(0, 50, Block.DIRT);
        });
        String velocitySecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (velocitySecret != null) VelocityProxy.enable(velocitySecret);
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            Pos spawnPos = new Pos(0.5, 60, 0.5);
            player.setRespawnPoint(spawnPos);
            player.teleport(spawnPos);
            parkourPositions.put(player, spawnPos);
            generateNextParkourBlocks(player);
        });
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Pos position = player.getPosition();
            if (position.y() < 0) {
                Pos resetPos = new Pos(0.5, 60, 0.5);
                player.teleport(resetPos);
                parkourPositions.put(player, resetPos);
                generateNextParkourBlocks(player);
            } else {
                Pos lastBlock = parkourPositions.get(player);
                if (lastBlock != null && position.distanceSquared(lastBlock) > 4) {
                    generateNextParkourBlocks(player);
                }
            }
        });
        globalEventHandler.addListener(PlayerCommandEvent.class, event -> {
            Player player = event.getPlayer();
            if (event.getCommand().equalsIgnoreCase("leave")) {
                sendToLobby(player);
                player.kick("You have left the game.");
            }
        });
        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void generateNextParkourBlocks(Player player) {
        Pos current = parkourPositions.get(player);
        for (int i = 0; i < 2; i++) {
            Point nextPoint = DefaultGenerator.getNextPoint(current, 60);
            instanceContainer.setBlock(nextPoint.blockX(), nextPoint.blockY(), nextPoint.blockZ(), Block.STONE);
            current = new Pos(nextPoint.x(), nextPoint.y(), nextPoint.z());
        }
        parkourPositions.put(player, current);
    }

    private static void sendToLobby(Player player) {
        String message = "lobby:" + player.getUsername();
        PluginMessagePacket packet = new PluginMessagePacket(
                "nebula:main",
                message.getBytes(StandardCharsets.UTF_8)
        );
        player.sendPacket(packet);
    }

    public static class DefaultGenerator {
        public static @NotNull Point getNextPoint(@NotNull Point current, int targetY) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int y = -1;
            if (targetY == 0) y = random.nextInt(-1, 2);
            if (targetY > current.blockY()) y = 1;

            int z = switch (y) {
                case 1 -> random.nextInt(1, 3);
                case -1 -> random.nextInt(2, 5);
                default -> random.nextInt(1, 4);
            };

            int x = random.nextInt(-3, 4);
            return current.add(x, y, z);
        }
    }
}
