package tf.tuff.listeners;

import tf.tuff.TuffX;
import tf.tuff.tuffactions.TuffActions;
import tf.tuff.viablocks.ViaBlocksPlugin;
import tf.tuff.viaentities.ViaEntitiesPlugin;
import tf.tuff.y0.Y0Plugin;

public abstract class ListenerBase {
    protected final ViaBlocksPlugin viaBlocksPlugin;
    protected final TuffActions tuffActions;
    protected final ViaEntitiesPlugin viaEntitiesPlugin;
    protected final Y0Plugin y0Plugin;

    public ListenerBase() {
        viaBlocksPlugin = TuffX.getInstance().getViaBlocksPlugin();
        tuffActions = TuffX.getInstance().getTuffActions();
        viaEntitiesPlugin = TuffX.getInstance().getViaEntitiesPlugin();
        y0Plugin = TuffX.getInstance().getY0Plugin();
    }
}
