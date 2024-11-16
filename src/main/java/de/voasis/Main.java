package de.voasis;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
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
        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.AIR));
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 60, 0));
            initializeParkour(player);
        });
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Pos position = player.getPosition();
            if (position.y() < 0) {
                player.teleport(new Pos(0, 60, 0));
                initializeParkour(player);
            }
            generateNextParkourBlock(player, position);
        });
        globalEventHandler.addListener(PlayerCommandEvent.class, event -> {
            Player player = event.getPlayer();
            if (event.getCommand().equalsIgnoreCase("leave")) {
                player.kick("You have left the game.");
            }
        });
        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void initializeParkour(Player player) {
        Pos startBlock = new Pos(0, 59, 0);
        instanceContainer.setBlock(startBlock.blockX(), startBlock.blockY(), startBlock.blockZ(), Block.STONE);
        parkourPositions.put(player, startBlock);
    }

    private static void generateNextParkourBlock(Player player, Pos position) {
        Pos lastBlock = parkourPositions.get(player);
        if (lastBlock != null && position.distanceSquared(lastBlock) > 4) {
            int offsetX = ThreadLocalRandom.current().nextInt(-1, 2);
            int offsetZ = ThreadLocalRandom.current().nextInt(-1, 2);
            int offsetY = ThreadLocalRandom.current().nextBoolean() ? 1 : 0;
            int nextX = lastBlock.blockX() + offsetX;
            int nextY = lastBlock.blockY() + offsetY;
            int nextZ = lastBlock.blockZ() + offsetZ;
            Pos nextBlock = new Pos(nextX, nextY, nextZ);
            instanceContainer.setBlock(nextBlock.blockX(), nextBlock.blockY(), nextBlock.blockZ(), Block.STONE);
            parkourPositions.put(player, nextBlock);
        }
    }
}
