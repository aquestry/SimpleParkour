package de.voasis;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
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
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static InstanceContainer instanceContainer;
    private static ThreadLocalRandom random = ThreadLocalRandom.current();
    private static List<Pos> placed = new ArrayList<>();
    private static List<Pos> spawnedFrom = new ArrayList<>();
    private static final Pos startBlock = new Pos(0, 0, 0);
    private static final Pos startPos = new Pos(0.5, 1, 0.5);

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> {});
        var vsecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (vsecret != null) { VelocityProxy.enable(vsecret); }
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            instanceContainer.setBlock(startBlock, Block.GOLD_BLOCK);
            player.setRespawnPoint(startPos);
        });
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            int score = spawnedFrom.size() - 1;
            player.sendActionBar(Component.text("Score: " + score));
            Pos beneath = player.getPosition().withY(player.getPosition().blockY() - 1).withX(player.getPosition().blockX()).withZ(player.getPosition().blockZ());
            beneath = new Pos(beneath.x(), beneath.y(), beneath.z(), 0 ,0);
            if(player.getPosition().y() < -10) {
                String message = "lobby:" + player.getUsername();
                PluginMessagePacket packet = new PluginMessagePacket(
                        "nebula:main",
                        message.getBytes(StandardCharsets.UTF_8)
                );
                player.sendPacket(packet);
            }
            if(spawnedFrom.isEmpty()) {
                spawnNewBlock(beneath, player, false);
            }
            if(!instanceContainer.getBlock(beneath).isAir() && !spawnedFrom.contains(beneath)) {
                spawnedFrom.add(beneath);
                spawnNewBlock(placed.getLast(), player, true);
            }
        });
        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void spawnNewBlock(Pos basePos, Player player, boolean effect) {
        Pos newBlock = new Pos(
                basePos.blockX() + random.nextInt(-1, 2),
                random.nextInt(2),
                basePos.blockZ() + random.nextInt(3, 5)
        );
        if(effect) {
            player.sendPacket(new ParticlePacket(
                    Particle.POOF, false,
                    newBlock.x() + 0.5, newBlock.y() + 0.5, newBlock.z() + 0.5,
                    0.5f, 0.5f, 0.5f, 0.05f, 20
            ));
            player.playSound(Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_HIT, Sound.Source.MASTER, 999, 1));
        }
        instanceContainer.setBlock(newBlock, getABlock());
        placed.add(newBlock);
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