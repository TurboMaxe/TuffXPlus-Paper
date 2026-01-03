package tf.tuff.viablocks;

import tf.tuff.viablocks.version.VersionAdapter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PaletteManager {

    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> stateToIdMap = new HashMap<>();

    public PaletteManager(VersionAdapter versionAdapter, Logger logger) {
        generate(versionAdapter, logger);
    }

    private void generate(VersionAdapter versionAdapter, Logger logger) {
        logger.info("Generating ViaBlocks Pre-Defined Palette...");

        for (Material material : versionAdapter.getModernMaterials()) {
            if (material.isBlock()) {
                addEntry(versionAdapter.getMaterialKey(material));
            }
        }

        String[] thickness = {"tip", "frustum", "middle", "base"};
        String[] direction = {"up", "down"};
        String[] bools = {"true", "false"};
        String[] facings = {"north", "south", "east", "west"};
        String[] tilts = {"none", "unbalanced", "partial", "full"};

        for (String d : direction) {
            for (String t : thickness) {
                for (String w : bools) {
                    addEntry("minecraft:pointed_dripstone[thickness=" + t + ",vertical_direction=" + d + ",waterlogged=" + w + "]");
                }
            }
        }

        for (String f : facings) {
            for (String t : tilts) {
                for (String w : bools) {
                    addEntry("minecraft:big_dripleaf[facing=" + f + ",tilt=" + t + ",waterlogged=" + w + "]");
                }
            }
        }

        for (String f : facings) {
            for (String h : new String[]{"lower", "upper"}) {
                addEntry("minecraft:small_dripleaf[facing=" + f + ",half=" + h + ",waterlogged=false]");
            }
        }
        
        for (String b : bools) {
            addEntry("minecraft:cave_vines[age=0,berries=" + b + "]");
            addEntry("minecraft:cave_vines_plant[berries=" + b + "]");
        }

        logger.info("Palette initialized with " + palette.size() + " entries.");
    }

    private void addEntry(String state) {
        if (!stateToIdMap.containsKey(state)) {
            stateToIdMap.put(state, palette.size());
            palette.add(state);
        }
    }

    public synchronized int getOrCreateId(String state) {
        if (stateToIdMap.containsKey(state)) {
            return stateToIdMap.get(state);
        }

        int newId = palette.size();
        palette.add(state);
        stateToIdMap.put(state, newId);
        
        broadcastNewPaletteEntry(state);
        
        return newId;
    }

    private void broadcastNewPaletteEntry(String state) {
        ViaBlocksPlugin plugin = ViaBlocksPlugin.instance;
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("NEW_PALETTE_ENTRY");
        out.writeUTF(state);
        byte[] data = out.toByteArray();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.isPlayerEnabled(player)) {
                    player.sendPluginMessage(plugin, ViaBlocksPlugin.CLIENTBOUND_CHANNEL, data);
                }
            }
        });
    }

    public synchronized int getId(String state) {
        return stateToIdMap.getOrDefault(state, -1);
    }

    public synchronized List<String> getPalette() {
        return new ArrayList<>(palette);
    }
}
