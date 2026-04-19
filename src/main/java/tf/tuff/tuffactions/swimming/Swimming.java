package tf.tuff.tuffactions.swimming;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import tf.tuff.tuffactions.TuffActions;

public class Swimming {

    private final TuffActions plugin;
    private final Set<UUID> swimmingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> glidingPlayers = ConcurrentHashMap.newKeySet();

    public Swimming(TuffActions plugin) {
        this.plugin = plugin;
    }

    public void handleSwimState(Player player, boolean isSwimming) {
        if (isSwimming) {
            swimmingPlayers.add(player.getUniqueId());
        } else {
            swimmingPlayers.remove(player.getUniqueId());
        }
        player.setSwimming(isSwimming);
        broadcastSwimState(player, isSwimming);
    }

    public void handleElytraState(Player player, boolean isGliding) {
        if (isGliding) {
            glidingPlayers.add(player.getUniqueId());
        } else {
            glidingPlayers.remove(player.getUniqueId());
        }
        player.setGliding(isGliding);
    }

    public void handleToggleSwim(EntityToggleSwimEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isSwimming() && swimmingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public void handleToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding() && glidingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public void handleSwimQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (swimmingPlayers.remove(player.getUniqueId())) {
            broadcastSwimState(player, false);
        }
        glidingPlayers.remove(player.getUniqueId());
        TuffActions.tuffPlayers.remove(player.getUniqueId());
    }

    private void broadcastSwimState(Player subject, boolean isSwimming) {
        for (UUID otherUUID : TuffActions.tuffPlayers) {
            if (!otherUUID.equals(subject.getUniqueId())) {
                Player recipient = Bukkit.getPlayer(otherUUID);
                if (recipient != null && recipient.isOnline()) {
                    sendSwimState(recipient, subject, isSwimming);
                }
            }
        }
    }

    private void sendSwimState(Player recipient, Player subject, boolean isSwimming) {
        if (recipient == null || !recipient.isOnline()) return;
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bout)) {
            out.writeUTF("update_other_swim");

            out.writeLong(subject.getUniqueId().getMostSignificantBits());
            out.writeLong(subject.getUniqueId().getLeastSignificantBits());
            out.writeBoolean(isSwimming);

            plugin.sendPluginMessage(recipient, bout.toByteArray());
        } catch (IOException e) {
            plugin.log(Level.WARNING, "Failed to send swim state to " + recipient.getName(), e);
        }
    }

    public void handleSwimReady(Player player) {
        plugin.plugin.getServer().getScheduler().runTaskLater(plugin.plugin, () -> {
            for (UUID swimmingPlayerId : swimmingPlayers) {
                Player swimmingPlayer = Bukkit.getPlayer(swimmingPlayerId);
                if (swimmingPlayer != null && swimmingPlayer.isOnline()) {
                    sendSwimState(player, swimmingPlayer, true);
                }
            }
        }, 20L);
    }
}
