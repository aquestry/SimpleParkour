package de.voasis;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.*;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import java.nio.charset.StandardCharsets;

public class Main {

    private static InstanceContainer instanceContainer;

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE_BRICKS));
        String vsecret = System.getenv("PAPER_VELOCITY_SECRET");
        if (vsecret != null) {
            VelocityProxy.enable(vsecret);
            System.out.println("secret: " + vsecret);
        }
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 1, 0));
        });
        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> event.getPlayer().getInventory().addItemStack(ItemStack.builder(Material.IRON_AXE).build()));
        globalEventHandler.addListener(PlayerDeathEvent.class, event -> {
            event.setChatMessage(null);
            for (Player p : instanceContainer.getPlayers()) {
                sendToLobby(p);
            }
        });
        globalEventHandler.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(true));
        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            for (Player p : instanceContainer.getPlayers()) {
                if (!p.equals(event.getPlayer())) {
                    sendToLobby(p);
                }
            }
        });
        instanceContainer.setChunkSupplier(LightingChunk::new);
        minecraftServer.start("0.0.0.0", 25565);
    }

    public static void sendToLobby(Player player) {
        String message = "lobby:" + player.getUsername();
        PluginMessagePacket packet = new PluginMessagePacket(
                "nebula:main",
                message.getBytes(StandardCharsets.UTF_8)
        );
        player.sendPacket(packet);
    }
}
