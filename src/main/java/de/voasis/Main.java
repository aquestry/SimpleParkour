package de.voasis;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;
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

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> {});
        instanceContainer.setChunkSupplier(LightingChunk::new);
        var vsecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (vsecret != null) { VelocityProxy.enable(vsecret); }
        globalEventHandler = MinecraftServer.getGlobalEventHandler();
        new NebulaAPI();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            instanceContainer.setBlock(startBlock, Block.GOLD_BLOCK);
            player.setRespawnPoint(startPos);

        });
        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> event.getPlayer().sendActionBar(Component.text("Use /lobby to leave.")));
        globalEventHandler.addListener(PlayerMoveEvent.class, event -> update(event.getPlayer()));
        globalEventHandler.addListener(PlayerChatEvent.class, event -> event.setCancelled(true));
        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> update(event.getPlayer()));
        minecraftServer.start("0.0.0.0", 25565);
    }

    private static void update(Player player){
        score = spawnedFrom.size();
        if(!spawnedFrom.isEmpty()) {
            player.sendActionBar(Component.text("Score: " + score));
        }
        Pos beneath=player.getPosition().withY(player.getPosition().blockY()-1).withX(player.getPosition().blockX()).withZ(player.getPosition().blockZ());
        beneath=new Pos(beneath.x(),beneath.y(),beneath.z(),0,0);
        if(player.getPosition().y()<-20){
            resetGame(player);
            return;
        }
        if(spawnedFrom.isEmpty())spawnNewBlock(beneath ,player);
        if(!instanceContainer.getBlock(beneath).isAir() && !spawnedFrom.contains(beneath)){
            spawnedFrom.add(beneath);
            if(!placed.isEmpty()){
                Pos last=placed.getLast();
                spawnNewBlock(last,player);
            }else{
                spawnNewBlock(beneath,player);
            }
        }
    }

    private static void spawnNewBlock(Pos basePos,Player player){
        if(spawnedFrom.contains(basePos)) return;
        boolean effect = placed.size() >= 2;
        Block block = getABlock();
        Pos np;
        do{
            int offX = random.nextInt(-1, 2);
            int offY = random.nextInt(-1, 2);
            int offZ = random.nextInt(3, 5);
            if(offZ >= 4 && offY == 1) { offZ--; }
            np = new Pos(basePos.x() + offX, basePos.y() + offY, basePos.z() + offZ, 0, 0);
        } while((instanceContainer.isInVoid(np) || np.y() < -10 || placed.contains(np)));
        placed.add(np);
        if(!effect) { instanceContainer.setBlock(np, block); placed.add(np); return; }
        player.playSound(Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_HIT,Sound.Source.MASTER,999,1));
        Entity e = new Entity(EntityType.BLOCK_DISPLAY);
        Pos finalNp = np;
        instanceContainer.setBlock(finalNp, Block.BARRIER);
        e.setNoGravity(true);
        e.editEntityMeta(BlockDisplayMeta.class, m->{
            m.setBlockState(block);
            m.setPosRotInterpolationDuration(5);
            m.setHasGlowingEffect(true);
        });
        e.setInstance(instanceContainer,basePos).thenRun(()->{
            MinecraftServer.getSchedulerManager().scheduleTask(()->{
                if(e.isRemoved())return TaskSchedule.stop();
                e.teleport(finalNp);
                return TaskSchedule.tick(1);
            },TaskSchedule.tick(1));
            MinecraftServer.getSchedulerManager().scheduleTask(()->{
                instanceContainer.setBlock(finalNp, block);
                player.sendPacket(new ParticlePacket(Particle.POOF, finalNp, new Vec(0.5, 0.5, 0.5), 2, 15));
                e.remove();
                return null;
            },TaskSchedule.tick(10));
        });
    }

    private static void resetGame(Player player){
        for (Entity entity : instanceContainer.getEntities()) {
            if (entity.getEntityType() == EntityType.BLOCK_DISPLAY) {
                entity.remove();
            }
        }
        for(Pos p : placed){
            instanceContainer.setBlock(p, Block.AIR);
        }
        placed.clear();
        spawnedFrom.clear();
        instanceContainer.setBlock(startBlock, Block.GOLD_BLOCK);
        score = 0;
        player.teleport(startPos);
        player.sendActionBar(Component.text("Score: " + score));
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