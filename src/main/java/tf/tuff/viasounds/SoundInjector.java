package tf.tuff.viasounds;

import com.viaversion.viaversion.api.Via;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SoundInjector {

    private final ViaSoundsPlugin plugin;

    public SoundInjector(ViaSoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public void inject(Player player) {
        UUID uuid = player.getUniqueId();
        var viaConnection = Via.getAPI().getConnection(uuid);
        if (viaConnection == null) {
            return;
        }

        Channel channel = viaConnection.getChannel();
        if (channel == null) {
            return;
        }


        channel.eventLoop().submit(() -> {
            try {
                if (channel.pipeline().get("viasounds_handler") != null) {
                    channel.pipeline().remove("viasounds_handler");
                }

                String targetHandler = null;
                if (channel.pipeline().get("packet_handler") != null) {
                    targetHandler = "packet_handler";
                } else if (channel.pipeline().get("encoder") != null) {
                    targetHandler = "encoder";
                } else if (channel.pipeline().get("via-encoder") != null) {
                    targetHandler = "via-encoder";
                }

                if (targetHandler != null) {
                    channel.pipeline().addBefore(
                        targetHandler,
                        "viasounds_handler",
                        new SoundDataHandler(plugin, player)
                    );
                } else {
                    channel.pipeline().addFirst("viasounds_handler", new SoundDataHandler(plugin, player));
                }

            } catch (Exception e) {
            }
        });
    }

    public void eject(Player player) {
        UUID uuid = player.getUniqueId();
        var viaConnection = Via.getAPI().getConnection(uuid);
        if (viaConnection == null) return;

        Channel channel = viaConnection.getChannel();
        if (channel != null && channel.isOpen()) {
            channel.eventLoop().submit(() -> {
                try {
                    if (channel.pipeline().get("viasounds_handler") != null) {
                        channel.pipeline().remove("viasounds_handler");
                    }
                } catch (Exception e) {
                }
            });
        }
    }
}
