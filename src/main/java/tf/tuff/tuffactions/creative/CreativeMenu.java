package tf.tuff.tuffactions.creative;

import tf.tuff.tuffactions.TuffActions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CreativeMenu {
    private final TuffActions plugin; 
    private final Set<String> itemMapping = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack> playerHoldingPlaceholder = new ConcurrentHashMap<>();
    
    private final TabUtil tabUtil;

    public CreativeMenu(TuffActions plugin) {
        this.plugin = plugin;
        this.tabUtil = new TabUtil(plugin);
        
        if (TuffActions.creativeEnabled) {
            initializeMappings();
        }
    }

    public void initializeMappings() {
        for (Material m : Material.values()){
            if (m.isItem()){
                itemMapping.add(m.name());
            }
        }
    }

    public void handleCreativeReady(Player player) {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bout)) {
            out.writeUTF("creative_items");
            out.writeInt(itemMapping.size());

            for (String item : itemMapping) {
                out.writeUTF(item);
                String category = this.tabUtil.getCreativeCategory(item);
                out.writeUTF(category != null ? category : "");
            }
            
            plugin.sendPluginMessage(player, bout.toByteArray());
        } catch (IOException e) {
            plugin.plugin.getLogger().log(Level.WARNING, "Failed to send creative items to " + player.getName(), e);
        }
    }

    public void handlePlaceholderTaken(Player player, String realItemName, int amount) {
        Material realMaterial = Material.getMaterial(realItemName.toUpperCase(Locale.ROOT));
        if (realMaterial != null) {
            ItemStack realItemStack = new ItemStack(realMaterial, amount);
            playerHoldingPlaceholder.put(player.getUniqueId(), realItemStack);
        }
    }

    public void onPlayerInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (playerHoldingPlaceholder.containsKey(playerUUID)) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.SWAP_WITH_CURSOR) {

                Bukkit.getScheduler().runTaskLater(this.plugin.plugin, () -> {
                    ItemStack realItemStack = playerHoldingPlaceholder.get(playerUUID);
                    
                    if (realItemStack != null && event.getClickedInventory() != null) {
                        event.getClickedInventory().setItem(event.getSlot(), realItemStack);
                    }
                    
                    playerHoldingPlaceholder.remove(playerUUID);
                }, 1L); 
            }
        }
    }
}
