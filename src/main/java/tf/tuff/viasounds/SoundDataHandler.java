package tf.tuff.viasounds;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class SoundDataHandler extends ChannelOutboundHandlerAdapter {

    private final ViaSoundsPlugin plugin;
    private final Player player;
    private final SoundFileManager soundFileManager;

    public SoundDataHandler(ViaSoundsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.soundFileManager = plugin.soundFileManager;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        String className = msg.getClass().getName();

        if (className.equals("net.minecraft.network.protocol.game.PacketPlayOutNamedSoundEffect")) {
            try {
                java.lang.reflect.Field soundHolderField = msg.getClass().getDeclaredField("c");
                soundHolderField.setAccessible(true);
                Object soundHolder = soundHolderField.get(msg);

                String soundHolderStr = soundHolder.toString();
                int locationStart = soundHolderStr.indexOf("location=") + 9;
                int locationEnd = soundHolderStr.indexOf(",", locationStart);
                if (locationEnd == -1) locationEnd = soundHolderStr.indexOf("]", locationStart);
                String soundName = soundHolderStr.substring(locationStart, locationEnd);

                if (soundFileManager.isModernSound(soundName)) {
                    java.lang.reflect.Field categoryField = msg.getClass().getDeclaredField("d");
                    categoryField.setAccessible(true);
                    Object categoryEnum = categoryField.get(msg);

                    int category = 0;
                    try {
                        java.lang.reflect.Method ordinalMethod = categoryEnum.getClass().getMethod("ordinal");
                        category = (int) ordinalMethod.invoke(categoryEnum);
                    } catch (Exception e) {
                        category = 0;
                    }

                    java.lang.reflect.Field xField = msg.getClass().getDeclaredField("e");
                    xField.setAccessible(true);
                    int fixedX = (int) xField.get(msg);

                    java.lang.reflect.Field yField = msg.getClass().getDeclaredField("f");
                    yField.setAccessible(true);
                    int fixedY = (int) yField.get(msg);

                    java.lang.reflect.Field zField = msg.getClass().getDeclaredField("g");
                    zField.setAccessible(true);
                    int fixedZ = (int) zField.get(msg);

                    java.lang.reflect.Field volumeField = msg.getClass().getDeclaredField("h");
                    volumeField.setAccessible(true);
                    float volume = (float) volumeField.get(msg);

                    java.lang.reflect.Field pitchField = msg.getClass().getDeclaredField("i");
                    pitchField.setAccessible(true);
                    float pitch = (float) pitchField.get(msg);

                    double x = fixedX / 8.0;
                    double y = fixedY / 8.0;
                    double z = fixedZ / 8.0;

                    String filePath = soundFileManager.getRandomFilePath(soundName);

                    if (filePath != null) {
                        int paletteIndex = soundFileManager.getPathIndex(filePath);

                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("PLAY_SOUND_INDEX");
                        out.writeShort(paletteIndex);
                        out.writeDouble(x);
                        out.writeDouble(y);
                        out.writeDouble(z);
                        out.writeFloat(volume);
                        out.writeFloat(pitch);
                        out.writeByte(category);

                        byte[] data = out.toByteArray();
                        player.sendPluginMessage(plugin.plugin, ViaSoundsPlugin.CLIENTBOUND_CHANNEL, data);

                        promise.setSuccess();
                        return;
                    }
                }
            } catch (Exception e) {
            }
        }
        super.write(ctx, msg, promise);
    }
}
