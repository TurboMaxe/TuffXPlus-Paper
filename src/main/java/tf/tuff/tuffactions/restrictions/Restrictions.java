package tf.tuff.tuffactions.restrictions;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tf.tuff.tuffactions.TuffActions;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Restrictions {
	private final TuffActions plugin;
    private RestrictionsCommand commandHandler;
	private Set<String> disallowed = ConcurrentHashMap.newKeySet();

	private static final List<String> example = List.of(
		"quickelytra", "fastcrystals", "hotbar_switcher", "fullbright", "tnt", "antipickup", "zoom", "glowingores", "rangecrosshair", "minimap"
	);

	public Restrictions(TuffActions plugin) {
		this.plugin = plugin;
        this.commandHandler = new RestrictionsCommand(this, plugin.plugin);
		validateConfig();
		loadConfig();
	}

	private void validateConfig() {
		boolean configUpdated = false;

		if (!plugin.plugin.getConfig().contains("restrictions.enabled", true)) {
			plugin.plugin.getConfig().set("restrictions.enabled", false);
			configUpdated = true;
		}
		if (!plugin.plugin.getConfig().contains("restrictions.disallow", true)) {
			plugin.plugin.getConfig().set("restrictions.disallow", example);
			configUpdated = true;
		}
		if (configUpdated) {
			plugin.plugin.saveConfig();
			plugin.info("Restrictions config updated");
		}
	} 
	public void loadConfig() {
		disallowed.clear();
		if (!TuffActions.restrictionsEnabled) return;
		plugin.info("Loading Restrictions config...");
		List<?> config = plugin.plugin.getConfig().getList("restrictions.disallow", example);
		for (Object val : config) {
			if (val instanceof String) disallowed.add((String)val);
		}
	}

	public void onTuffXReload() {
		validateConfig();
		loadConfig();
	}

	public void handleRestrictionsReady(Player player) {
		if (!TuffActions.restrictionsEnabled) return;
		plugin.info("Sending restrictions to %s".formatted(player.getName()));
		try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bout)) {
			out.writeUTF("client_features_all");
			out.writeInt(disallowed.size());

			for (String key : disallowed) {
				out.writeUTF(key);
				out.writeBoolean(false);
			}

			plugin.sendPluginMessage(player, bout.toByteArray());
		} catch (IOException e) {
			plugin.log(Level.WARNING, "Failed to send Restrictions to " + player.getName(), e);
		}
	}

	public void sendSingleUpdateToAll(String key) {
		for (UUID uuid : TuffActions.tuffPlayers) {
			Player player = Bukkit.getPlayer(uuid);
			handleSingleUpdate(player, key);
		}
	}

	private void handleSingleUpdate(Player player, String key) {
		if (!TuffActions.restrictionsEnabled) return;
		plugin.info("Sending restriction update for '%s' to %s".formatted(key, player.getName()));
		try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
		     DataOutputStream out = new DataOutputStream(bout)
		) {
			out.writeUTF("client_feature");
			out.writeUTF(key);
			out.writeBoolean(!disallowed.contains(key));
			plugin.sendPluginMessage(player, bout.toByteArray());
		} catch (IOException e) {
			plugin.log(Level.WARNING, "Failed to send Restriction "+key+" to " + player.getName(), e);
		}
	}

	public boolean onTuffXCommand(CommandSender sender, Command command, String label, String[] args) {
		return commandHandler.onCommand(sender, command, label, args);
	}

}