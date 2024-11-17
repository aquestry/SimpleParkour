package de.voasis;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static InstanceContainer instanceContainer;
    private static List<Pos> placed = new ArrayList<>();
    private static List<Pos> spawnedFrom = new ArrayList<>();
    private static final Pos startBlock = new Pos(0, 59, 0);
    private static final Pos startPos = new Pos(0.5, 60, 0.5);

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        MinecraftServer.getCommandManager().register(new LeaveCommand());
        instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> {});
        var vsecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (vsecret != null) { VelocityProxy.enable(vsecret); }
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            instanceContainer.setBlock(startBlock, Block.STONE);
            player.setRespawnPoint(startPos);
        });
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Pos beneath = player.getPosition().withY(player.getPosition().blockY() - 1).withX(player.getPosition().blockX()).withZ(player.getPosition().blockZ());
            beneath = new Pos(beneath.x(), beneath.y(), beneath.z(), 0 ,0);
            if(player.getPosition().y() < 0) {
                reset(player);
            }
            if(!instanceContainer.getBlock(beneath).isAir() && !spawnedFrom.contains(beneath) || spawnedFrom.isEmpty()) {
                spawnNewBlock(beneath, player);
            }
        });
        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void reset(Player player) {
        player.teleport(new Pos(0.5, 60, 0.5));
        for(Pos block : placed) {
            instanceContainer.setBlock(block, Block.AIR);
        }
        placed.clear();
        spawnedFrom.clear();
    }

    private static void spawnNewBlock(Pos basePos, Player player) {
        spawnedFrom.add(basePos);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Pos newBlock = new Pos(
                basePos.blockX() + random.nextInt(-1, 2),
                basePos.blockY() + random.nextInt(-1, 2),
                basePos.blockZ() + random.nextInt(2, 4)
        );
        player.sendPacket(new ParticlePacket(
                Particle.POOF, false,
                newBlock.x() + 0.5, newBlock.y() + 0.5, newBlock.z() + 0.5,
                0.5f, 0.5f, 0.5f, 0.05f, 20
        ));
        instanceContainer.setBlock(newBlock.blockX(), newBlock.blockY(), newBlock.blockZ(), Block.STONE);
        placed.add(newBlock);
    }
}

