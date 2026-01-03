package tf.tuff.viablocks.version.legacy;

import tf.tuff.viablocks.version.VersionAdapter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.EnumSet;

public class LegacyAdapter implements VersionAdapter {

    @Override
    public EnumSet<Material> getModernMaterials() {
        return EnumSet.noneOf(Material.class);
    }

    @Override
    public String getBlockDataString(Block block) {
        return "minecraft:" + block.getType().name().toLowerCase();
    }

    @Override
    public String getMaterialKey(Material material) {
        return "minecraft:" + material.name().toLowerCase();
    }

    @Override
    public int getClientViewDistance(Player player) {
        return player.getServer().getViewDistance();
    }

    @Override
    public void giveCustomBlocks(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE));
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE));
    }
}
