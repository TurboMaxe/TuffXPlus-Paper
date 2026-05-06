package tf.tuff.services.viablocks;

import com.github.puregero.multilib.MultiLib;
import lombok.Getter;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tf.tuff.TuffX;
import tf.tuff.services.ServiceBase;
import tf.tuff.services.viablocks.version.VersionAdapter;
import tf.tuff.services.viablocks.version.modern.ModernAdapter;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ViaBlocksService implements ServiceBase {

    public static final String CLIENTBOUND_CHANNEL = "viablocks:data";
    public static final String SERVERBOUND_CHANNEL = "viablocks:handshake";

    public final Set<UUID> viaBlocksEnabledPlayers = new HashSet<>();
    @Getter
    public CustomBlockListener blockListener;
    static ViaBlocksService instance;

    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    private final Set<UUID> joinedPlayersCache = new HashSet<>();

    @Getter
    private boolean enabled;
    @Getter
    private boolean debug;
    private boolean sendWelcomeBook;

    public VersionAdapter versionAdapter;

    @Getter
    public PaletteManager paletteManager;
    @Getter
    private long updateBatchDelayTicks = 1L;
    public ExecutorService chunkExecutor;
    public boolean isPaper = false;

    public TuffX plugin;

    ViaBlocksService(TuffX plugin){
        this.plugin = plugin;
    }   

    public void onTuffXReload() {
        loadSyncSettings();

        if (chunkExecutor != null) {
            chunkExecutor.shutdownNow();
        }
        this.chunkExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));

        if (playerDataFile == null) {
            playerDataFile = new File(plugin.getDataFolder(), "players.yml");
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        
        if (blockListener != null) {
            blockListener.clearCache();
        }
        
        info("ViaBlocks reloaded.");
    }

    public static ViaBlocksService invoke() {
        return new ViaBlocksService(TuffX.getInstance());
    }

    public void onTuffXEnable() {
        instance = this;
        this.chunkExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        this.versionAdapter = new ModernAdapter();
        this.paletteManager = new PaletteManager(this.versionAdapter);

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            this.isPaper = true;
            info("Paper detected. Enabling optimized asynchronous scheduling.");
        } catch (ClassNotFoundException e) {
            this.isPaper = false;
            info("Running on Spigot/Bukkit. Using standard scheduler.");
        }

        plugin.saveDefaultConfig();
        loadSyncSettings();
        setupPlayerData();
           

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CLIENTBOUND_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SERVERBOUND_CHANNEL, plugin);

        this.blockListener = new CustomBlockListener(this, this.versionAdapter, this.paletteManager);

        plugin.getCommand("viablocks").setExecutor(plugin);
        info("ViaBlocks has been enabled successfully and is listening for client handshakes.");
    }

    public void handlePacket(Player player, byte[] message) {
        if (!isPlayerEnabled(player) && isEnabled()) {
            debug("Received ViaBlocks handshake from player: %s. Enabling custom blocks.".formatted(player.getName()));
            setPlayerEnabled(player, true);

            blockListener.onViaBlocksPlayerJoin(player);
        }
    }

    private void loadSyncSettings() {
        enabled = plugin.getConfig().getBoolean("viablocks.viablocks-enabled", false);
        debug = plugin.getConfig().getBoolean("viablocks.debug", false);
        sendWelcomeBook = plugin.getConfig().getBoolean("viablocks.send-welcome-book", true);

        String mode = plugin.getConfig().getString("viablocks.sync-mode", "normal");
        this.updateBatchDelayTicks = mode.equalsIgnoreCase("reduced") ? 10L : 1L;
    }

    public void onTuffXDisable(){
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CLIENTBOUND_CHANNEL); 
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, SERVERBOUND_CHANNEL); 

        if (chunkExecutor != null) {
            chunkExecutor.shutdownNow();
            chunkExecutor = null;
        }

        info("ViaBlocks has been disabled.");
    }
    private void setupPlayerData() {
        playerDataFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playerDataFile.exists()) {
            try {
                if (!playerDataFile.createNewFile()) severe("Could not create players.yml!");
            } catch (IOException e) {
                severe("Could not create players.yml!");
                severe(e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        joinedPlayersCache.clear();
        for (String uuidStr : playerDataConfig.getStringList("joined-players")) {
            try {
                joinedPlayersCache.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
    public boolean hasPlayerJoinedBefore(Player player) {
        return joinedPlayersCache.contains(player.getUniqueId());
    }
    public boolean isFirstJoin(Player player) {
        return !hasPlayerJoinedBefore(player);
    }
    public void markPlayerAsJoined(Player player) {
        UUID uuid = player.getUniqueId();
        if (joinedPlayersCache.add(uuid)) {
            List<String> joinedPlayers = playerDataConfig.getStringList("joined-players");
            joinedPlayers.add(uuid.toString());
            playerDataConfig.set("joined-players", joinedPlayers);
            try {
                playerDataConfig.save(playerDataFile);
            } catch (IOException e) {
                severe("Could not save to players.yml!");
                log(Level.SEVERE, e.getMessage());
            }
        }
    }
    public void sendWelcomeGui(Player player) {
        if (!this.sendWelcomeBook) return;
        Book book = Book.book(Component.text("ViaBlocks Information"),
                              Component.text("ViaBlocks"));
        book.pages(
            List.of(
                Component.text("Welcome to ViaBlocks!").color(NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD),
                Component.text("\n\nThis feature is in active development!\n\n If you find any bugs or issues, please report them on our")
                    .append(Component.text("bug .").color(NamedTextColor.BLUE))
                    .clickEvent(ClickEvent.openUrl("https://github.com/TuffNetwork/ViaIssuesBlocks/issues\""))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("Click to open the bug tracker!", NamedTextColor.GRAY))
                    ),
                Component.text("Bamboo and kelp are noted.", NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC)
            )
        );
        MultiLib.getEntityScheduler(player).run(plugin, t -> player.openBook(book), null);
    }

    public boolean isPlayerEnabled(Player player) {
        if (player == null) return false;
        return viaBlocksEnabledPlayers.contains(player.getUniqueId());
    }

    public void setPlayerEnabled(Player player, boolean enabled) {
        if (enabled && isEnabled()) {
            viaBlocksEnabledPlayers.add(player.getUniqueId());
        } else {
            viaBlocksEnabledPlayers.remove(player.getUniqueId());
        }
    }

    public boolean onTuffXCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("get")) {
                if (!player.hasPermission("tuffx.viablocks.command.get")) {
                    player.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                this.versionAdapter.giveCustomBlocks(player);
                player.sendMessage("§aYou have been given a set of custom blocks.");
                return true;
            } else if (args[0].equalsIgnoreCase("refresh")) {
                if (!player.hasPermission("tuffx.viablocks.command.refresh")) {
                    player.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                player.sendMessage("§aRefreshing modern blocks in your view distance...");
                World world = player.getWorld();
                int viewDistance = this.versionAdapter.getClientViewDistance(player);
                for (int x = -viewDistance; x <= viewDistance; x++) {
                    for (int z = -viewDistance; z <= viewDistance; z++) {
                        int chunkX = player.getLocation().getChunk().getX() + x;
                        int chunkZ = player.getLocation().getChunk().getZ() + z;
                        if (world.isChunkLoaded(chunkX, chunkZ)) {
                            blockListener.processChunkForSinglePlayer(world.getChunkAt(chunkX, chunkZ), player);
                        }
                    }
                }
                player.sendMessage("§aRefresh complete!");
                return true;
            }
        }
        player.sendMessage("§cInvalid usage. Use: /viablocks <get|refresh>");
        return false;
    }

    private final Logger LOG = Logger.getLogger("TuffX");

    private void debug(@NotNull String message) {
        if (isDebug()) LOG.info(message);
    }

    private void log(Level level, String msg) {
        LOG.log(level, "[ViaBlocks] %s".formatted(msg));
    }

    public void info(@NotNull String msg) {
        log(Level.INFO, msg);
    }
    private void severe(@NotNull String msg) {
        log(Level.SEVERE, msg);
    }
}
