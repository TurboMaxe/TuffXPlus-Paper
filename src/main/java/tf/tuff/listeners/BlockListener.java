package tf.tuff.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;

public class BlockListener extends ListenerBase implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent e) {
        viaBlocksPlugin.blockListener.handleBlockForm(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent e) {
        viaBlocksPlugin.blockListener.handleBlockFade(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent e) {
        viaBlocksPlugin.blockListener.handleBlockSpread(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        viaBlocksPlugin.blockListener.handleBlockBreak(e);
        y0Plugin.handleBlockBreak(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent e) {
        viaBlocksPlugin.blockListener.handleBlockGrow(e);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        viaBlocksPlugin.blockListener.handleBlockPlace(e);
        y0Plugin.handleBlockPlace(e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent e) {
        y0Plugin.handleBlockPhysics(e);
        viaBlocksPlugin.blockListener.handleBlockPhysics(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        viaBlocksPlugin.blockListener.handleBlockExplode(e);
        y0Plugin.handleBlockExplode(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        viaBlocksPlugin.blockListener.handleBlockFromTo(e);
        y0Plugin.handleBlockFromTo(e);
    }
}
