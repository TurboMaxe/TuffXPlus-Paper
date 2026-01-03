package tf.tuff.tuffactions.network;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import tf.tuff.tuffactions.TuffActions;
import org.bukkit.entity.Player;

public class TuffPacketListener extends PacketListenerAbstract {
    private final TuffActions plugin;

    public TuffPacketListener(TuffActions plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) {
            return;
        }

        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player)) {
            return;
        }

        WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
        wrapper.read();

        if (!TuffActions.CHANNEL.equals(wrapper.getChannelName())) {
            return;
        }

        byte[] data = wrapper.getData();
        if (data == null || data.length == 0) {
            return;
        }

        plugin.handlePluginMessage((Player) rawPlayer, data);
    }
}
