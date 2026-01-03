package tf.tuff.viablocks;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.io.ByteStreams;
import tf.tuff.viablocks.version.VersionAdapter;
import tf.tuff.viablocks.version.legacy.LegacyAdapter;
import tf.tuff.viablocks.version.modern.ModernAdapter;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ViaBlocksPlugin extends JavaPlugin implements CommandExecutor, PluginMessageListener {

    public static final String CLIENTBOUND_CHANNEL = "viablocks:data";
    public static final String SERVERBOUND_CHANNEL = "viablocks:handshake";

    private final Set<UUID> viaBlocksEnabledPlayers = new HashSet<>();
    private CustomBlockListener blockListener;
    private ChunkSenderManager chunkSenderManager; 
    static ViaBlocksPlugin instance;

    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    private boolean sendWelcomeBook;
    private boolean showStartupLogo;
    public VersionAdapter versionAdapter;

    private PaletteManager paletteManager;
    private long updateBatchDelayTicks = 1L;
    private long chunkSendIntervalTicks = 1L;
    private int chunksPerTick = 1;

    public boolean isPaper = false;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!setupVersionAdapter()) {
            getLogger().severe("Could not detect server version. This plugin may not work correctly.");
            this.versionAdapter = new LegacyAdapter();
        }

        this.paletteManager = new PaletteManager(this.versionAdapter, getLogger());

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            this.isPaper = true;
            getLogger().info("Paper detected. Enabling optimized asynchronous scheduling.");
        } catch (ClassNotFoundException e) {
            this.isPaper = false;
            getLogger().info("Running on Spigot/Bukkit. Using standard scheduler.");
        }

        saveDefaultConfig();
        this.showStartupLogo = getConfig().getBoolean("show-startup-logo", true);
        this.sendWelcomeBook = getConfig().getBoolean("send-welcome-book", true);
        loadSyncSettings();
        setupPlayerData();

        if (this.showStartupLogo){
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "$$\\    $$\\ $$\\           $$$$$$$\\  $$\\                     $$\\                 ");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "$$ |   $$ |\\__|          $$  __$$\\ $$ |                    $$ |                ");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "$$ |   $$ |$$\\  $$$$$$\\  $$ |  $$ |$$ | $$$$$$\\   $$$$$$$\\ $$ |  $$\\  $$$$$$$\\ ");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "\\$$\\  $$  |$$ | \\____$$\\ $$$$$$$\\ |$$ |$$  __$$\\ $$  _____|$$ | $$  |$$  _____|");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + " \\$$\\$$  / $$ | $$$$$$$ |$$  __$$\\ $$ |$$ /  $$ |$$ /      $$$$$$  / \\$$$$$$\\  ");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "  \\$$$  /  $$ |$$  __$$ |$$ |  $$ |$$ |$$ |  $$ |$$ |      $$  _$$<   \\____$$\\ ");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "   \\$  /   $$ |\\$$$$$$$ |$$$$$$$  |$$ |\\$$$$$$  |\\$$$$$$$\\ $$ | \\$$\\ $$$$$$$  |");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "    \\_/    \\__| \\_______|\\_______/ \\__| \\______/  \\_______|\\__|  \\__|\\_______/ ");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "+ llucasandersen (PacketEvents migration and async safety fixes)");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "+ Potato (optimizations)");
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "- coleis1op, if ts is driving me crazy, im taking credit");
            getServer().getConsoleSender().sendMessage("");
        }

        PacketEvents.getAPI().init();

        getServer().getMessenger().registerOutgoingPluginChannel(this, CLIENTBOUND_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, SERVERBOUND_CHANNEL, this);

        this.chunkSenderManager = new ChunkSenderManager(this, this.chunkSendIntervalTicks, this.chunksPerTick);

        this.blockListener = new CustomBlockListener(this, this.versionAdapter, this.paletteManager, this.chunkSenderManager);
        getServer().getPluginManager().registerEvents(this.blockListener, this);
        ChunkPacketListener.initialize(this);

        getCommand("viablocks").setExecutor(this);
        getLogger().info("ViaBlocks has been enabled successfully and is listening for client handshakes.");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(SERVERBOUND_CHANNEL)) {
            if (!isPlayerEnabled(player)) {
                getLogger().info("Received ViaBlocks handshake from player: " + player.getName() + ". Enabling custom blocks.");
                setPlayerEnabled(player, true);

                blockListener.onViaBlocksPlayerJoin(player);
            }
        }
    }

    public PaletteManager getPaletteManager() {
        return this.paletteManager;
    }

    public long getUpdateBatchDelayTicks() {
        return this.updateBatchDelayTicks;
    }

    private void loadSyncSettings() {
        String mode = getConfig().getString("sync-mode", "normal");
        if (mode == null) {
            mode = "normal";
        }
        if (mode.equalsIgnoreCase("reduced")) {
            this.updateBatchDelayTicks = 10L;
            this.chunkSendIntervalTicks = 10L;
            this.chunksPerTick = 1;
        } else {
            this.updateBatchDelayTicks = 1L;
            this.chunkSendIntervalTicks = 1L;
            this.chunksPerTick = 1;
        }
    }

    private boolean setupVersionAdapter() {
        try { Pattern pattern = Pattern.compile("1\\.(\\d{1,2})"); Matcher matcher = pattern.matcher(Bukkit.getBukkitVersion()); if (matcher.find()) { int minorVersion = Integer.parseInt(matcher.group(1)); if (minorVersion >= 13) { this.versionAdapter = new ModernAdapter(); } else { this.versionAdapter = new LegacyAdapter(); } return true; } } catch (Exception e) { e.printStackTrace(); } return false;
    }
    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate(); getServer().getMessenger().unregisterOutgoingPluginChannel(this, CLIENTBOUND_CHANNEL); getServer().getMessenger().unregisterIncomingPluginChannel(this, SERVERBOUND_CHANNEL); getLogger().info("ViaBlocks has been disabled.");
    }
    private void setupPlayerData() {
        playerDataFile = new File(getDataFolder(), "players.yml"); if (!playerDataFile.exists()) { try { playerDataFile.createNewFile(); } catch (IOException e) { getLogger().severe("Could not create players.yml!"); e.printStackTrace(); } } playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }
    public boolean hasPlayerJoinedBefore(Player player) {
        return playerDataConfig.getStringList("joined-players").contains(player.getUniqueId().toString());
    }
    public boolean isFirstJoin(Player player) {
        return !hasPlayerJoinedBefore(player);
    }
    public void markPlayerAsJoined(Player player) {
        List<String> joinedPlayers = playerDataConfig.getStringList("joined-players"); if (!joinedPlayers.contains(player.getUniqueId().toString())) { joinedPlayers.add(player.getUniqueId().toString()); playerDataConfig.set("joined-players", joinedPlayers); try { playerDataConfig.save(playerDataFile); } catch (IOException e) { getLogger().severe("Could not save to players.yml!"); e.printStackTrace(); } }
    }
    public void sendWelcomeGui(Player player) {
        if (!this.sendWelcomeBook) return;
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK); BookMeta meta = (BookMeta) book.getItemMeta(); if (meta == null) return; meta.setTitle("ViaBlocks Information"); meta.setAuthor("ViaBlocks"); TextComponent welcome = new TextComponent("Welcome to ViaBlocks!"); welcome.setColor(ChatColor.DARK_AQUA); welcome.setBold(true); TextComponent body = new TextComponent("\n\nThis feature is in active development!\n\nIf you find any visual bugs or issues, please report them on our "); body.setColor(ChatColor.BLACK); TextComponent link = new TextComponent("bug tracker"); link.setColor(ChatColor.BLUE); link.setUnderlined(true); link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/TuffNetwork/ViaIssuesBlocks/issues")); link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to open the bug tracker!").color(ChatColor.GRAY).create())); TextComponent disclaimer = new TextComponent("\n\n(Bamboo and kelp are noted.)"); disclaimer.setColor(ChatColor.DARK_GRAY); disclaimer.setItalic(true); meta.spigot().addPage(new ComponentBuilder("").append(welcome).append(body).append(link).append(new TextComponent(".")).append(disclaimer).create()); book.setItemMeta(meta); getServer().getScheduler().runTask(this, () -> player.openBook(book));
    }
    public boolean isPlayerEnabled(Player player) {
        if (player == null) return false; return viaBlocksEnabledPlayers.contains(player.getUniqueId());
    }
    public void setPlayerEnabled(Player player, boolean enabled) {
        if (enabled) { viaBlocksEnabledPlayers.add(player.getUniqueId()); } else { viaBlocksEnabledPlayers.remove(player.getUniqueId()); }
    }
    public CustomBlockListener getBlockListener() { return this.blockListener; }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("This command can only be executed by a player."); return true; } Player player = (Player) sender; if (args.length > 0) { if (args[0].equalsIgnoreCase("get")) { if (!player.hasPermission("viablocks.command.get")) { player.sendMessage("§cYou do not have permission to use this command."); return true; } this.versionAdapter.giveCustomBlocks(player); player.sendMessage("§aYou have been given a set of custom blocks."); return true; } else if (args[0].equalsIgnoreCase("refresh")) { if (!player.hasPermission("viablocks.command.refresh")) { player.sendMessage("§cYou do not have permission to use this command."); return true; } player.sendMessage("§aRefreshing modern blocks in your view distance..."); World world = player.getWorld(); int viewDistance = this.versionAdapter.getClientViewDistance(player); int playerChunkX = player.getLocation().getChunk().getX(); int playerChunkZ = player.getLocation().getChunk().getZ(); for (int x = -viewDistance; x <= viewDistance; x++) { for (int z = -viewDistance; z <= viewDistance; z++) { int chunkX = playerChunkX + x; int chunkZ = playerChunkZ + z; if (world.isChunkLoaded(chunkX, chunkZ)) { blockListener.processChunkForSinglePlayer(world.getChunkAt(chunkX, chunkZ), player); } } } player.sendMessage("§aRefresh complete!"); return true; } } player.sendMessage("§cInvalid usage. Use: /viablocks <get|refresh>"); return true;
    }
}
