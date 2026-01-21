package tf.tuff.viasounds;

import tf.tuff.TuffX;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ViaSoundsPlugin {

    public static final String CLIENTBOUND_CHANNEL = "viasounds:data";
    public static final String SERVERBOUND_CHANNEL = "viasounds:handshake";

    public final Set<UUID> viaSoundsEnabledPlayers = new HashSet<>();

    static ViaSoundsPlugin instance;

    public SoundFileManager soundFileManager;
    private SoundInjector soundInjector;
    private boolean enabled = true;
    private boolean debug = false;
    private int maxDistance = -1;

    public TuffX plugin;

    public ViaSoundsPlugin(TuffX plugin){
         this.plugin = plugin;
    }

    public void onTuffXLoad() {

    }

    public void onTuffXReload() {
        loadConfig();
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("viasounds.viasounds-enabled", true);
        debug = plugin.getConfig().getBoolean("viasounds.debug", false);
        maxDistance = plugin.getConfig().getInt("viasounds.max-distance", -1);
    }

    public void onTuffXEnable() {
        instance = this;

        loadConfig();

        this.soundFileManager = new SoundFileManager();
        this.soundInjector = new SoundInjector(this);

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CLIENTBOUND_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SERVERBOUND_CHANNEL, plugin);

        if (enabled) {
            plugin.getLogger().info("ViaSounds enabled with " + soundFileManager.getAllSoundPaths().size() + " sounds");
        } else {
            plugin.getLogger().info("ViaSounds disabled in config");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebug() {
        return debug;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void handlePacket(Player player, byte[] message) {
        if (!enabled) return;
        if (!isPlayerEnabled(player.getUniqueId())) {
            setPlayerEnabled(player.getUniqueId(), true);
            soundInjector.inject(player);
            sendPaletteToClient(player);
        }
    }

    private void sendPaletteToClient(Player player) {
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("INIT_PALETTE");

        java.util.List<String> palette = soundFileManager.getAllSoundPaths();
        out.writeInt(palette.size());

        for (String soundId : palette) {
            out.writeUTF(soundId);
        }

        player.sendPluginMessage(plugin, CLIENTBOUND_CHANNEL, out.toByteArray());
    }

    public void handlePlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        soundInjector.eject(event.getPlayer());
        viaSoundsEnabledPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void onTuffXDisable(){
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CLIENTBOUND_CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, SERVERBOUND_CHANNEL);

    }

    public boolean isPlayerEnabled(UUID playerId) {
        return viaSoundsEnabledPlayers.contains(playerId);
    }

    public void setPlayerEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            viaSoundsEnabledPlayers.add(playerId);
        } else {
            viaSoundsEnabledPlayers.remove(playerId);
        }
    }
}
