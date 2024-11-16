package de.voasis;

import net.minestom.server.MinecraftServer;
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
        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.AIR));
        String velocitySecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (velocitySecret != null) VelocityProxy.enable(velocitySecret);
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            Pos spawnPos = new Pos(0, 60, 0);
            instanceContainer.setBlock(0, 59, 0, Block.STONE);
            player.setRespawnPoint(spawnPos);
            player.teleport(spawnPos);
            parkourPositions.put(player, spawnPos);
        });
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Pos position = player.getPosition();
            if (position.y() < 0) {
                Pos resetPos = new Pos(0, 60, 0);
                player.teleport(resetPos);
                parkourPositions.put(player, resetPos);
            } else {
                generateNextParkourBlock(player, position);
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

    private static void generateNextParkourBlock(Player player, Pos position) {
        Pos lastBlock = parkourPositions.get(player);
        if (lastBlock != null && position.distanceSquared(lastBlock) > 4) {
            int forward = ThreadLocalRandom.current().nextInt(2, 5);
            int side = ThreadLocalRandom.current().nextInt(-1, 2);
            int upDown = ThreadLocalRandom.current().nextInt(-1, 2);
            int nextX = lastBlock.blockX() + forward;
            int nextY = Math.min(62, Math.max(59, lastBlock.blockY() + upDown));
            int nextZ = lastBlock.blockZ() + side;
            Pos nextBlock = new Pos(nextX, nextY, nextZ);
            instanceContainer.setBlock(nextBlock.blockX(), nextBlock.blockY(), nextBlock.blockZ(), Block.STONE);
            parkourPositions.put(player, nextBlock);
        }
    }

    private static void sendToLobby(Player player) {
        String message = "lobby:" + player.getUsername();
        PluginMessagePacket packet = new PluginMessagePacket(
                "nebula:main",
                message.getBytes(StandardCharsets.UTF_8)
        );
        player.sendPacket(packet);
    }
}
