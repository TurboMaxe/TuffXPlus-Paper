package tf.tuff.netty;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class BaseInjector {

	private final String handlerName;

	protected BaseInjector(String handlerName) {
		this.handlerName = handlerName;
	}

	protected abstract ChannelHandler createHandler(Player player);

	protected void onPostInject(Player player) {
	}

	public void inject(Player player) {
		UUID uuid = player.getUniqueId();
		UserConnection viaConnection = Via.getAPI().getConnection(uuid);
		if (viaConnection == null) return;

		Channel channel = viaConnection.getChannel();
		if (channel == null) return;

		channel.eventLoop().submit(() -> {
			try {
				if (channel.pipeline().get(handlerName) != null) {
					channel.pipeline().remove(handlerName);
				}

				String targetHandler = null;
				String[] anchors = {"packet_handler", "encoder", "via-encoder"};
                for (String anchor : anchors) {
                    if (channel.pipeline().get(anchor) != null) {
                        targetHandler = anchor;
                        break;
                    }
        }

				ChannelHandler handler = createHandler(player);
				if (targetHandler != null) {
					channel.pipeline().addBefore(targetHandler, handlerName, handler);
				} else {
					channel.pipeline().addFirst(handlerName, handler);
				}

				onPostInject(player);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	public void eject(Player player) {
		UserConnection viaConnection = Via.getAPI().getConnection(player.getUniqueId());
		Channel channel = viaConnection != null ? viaConnection.getChannel() : null;
		if (channel != null && channel.isOpen()) {
			channel.eventLoop().submit(() -> {
				try {
					if (channel.pipeline().get(handlerName) != null) {
						channel.pipeline().remove(handlerName);
					}
				} catch (Exception ignored) {}
			});
		}
	}
}
