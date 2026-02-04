package tf.tuff.viasounds;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SoundDataHandler extends ChannelOutboundHandlerAdapter {

    private static volatile boolean fieldsInitialized = false;
    private static volatile boolean fieldsAvailable = false;
    private static Class<?> soundPacketClass;
    private static Field soundHolderField;
    private static Field categoryField;
    private static Field xField;
    private static Field yField;
    private static Field zField;
    private static Field volumeField;
    private static Field pitchField;
    private static Method ordinalMethod;
    private static Field locationField;

    private final ViaSoundsPlugin plugin;
    private final Player player;
    private final SoundFileManager soundFileManager;

    public SoundDataHandler(ViaSoundsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.soundFileManager = plugin.soundFileManager;
    }

    private static synchronized void initFields(Class<?> packetClass) {
        if (fieldsInitialized) return;
        fieldsInitialized = true;

        try {
            soundPacketClass = packetClass;

            soundHolderField = packetClass.getDeclaredField("c");
            soundHolderField.setAccessible(true);

            categoryField = packetClass.getDeclaredField("d");
            categoryField.setAccessible(true);

            xField = packetClass.getDeclaredField("e");
            xField.setAccessible(true);

            yField = packetClass.getDeclaredField("f");
            yField.setAccessible(true);

            zField = packetClass.getDeclaredField("g");
            zField.setAccessible(true);

            volumeField = packetClass.getDeclaredField("h");
            volumeField.setAccessible(true);

            pitchField = packetClass.getDeclaredField("i");
            pitchField.setAccessible(true);

            try {
                Object testHolder = soundHolderField.get(null);
            } catch (Exception ignored) {}

            fieldsAvailable = true;
        } catch (Exception e) {
            fieldsAvailable = false;
        }
    }

    private static String extractSoundName(Object soundHolder) {
        if (soundHolder == null) return null;

        try {
            if (locationField == null) {
                try {
                    locationField = soundHolder.getClass().getDeclaredField("b");
                    locationField.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    try {
                        locationField = soundHolder.getClass().getDeclaredField("location");
                        locationField.setAccessible(true);
                    } catch (NoSuchFieldException e2) {
                        locationField = null;
                    }
                }
            }

            if (locationField != null) {
                Object location = locationField.get(soundHolder);
                if (location != null) {
                    return location.toString();
                }
            }
        } catch (Exception ignored) {}

        String str = soundHolder.toString();
        int start = str.indexOf("location=");
        if (start == -1) return null;
        start += 9;
        int end = str.indexOf(",", start);
        if (end == -1) end = str.indexOf("]", start);
        if (end == -1) end = str.length();
        return str.substring(start, end);
    }

    private static int getOrdinal(Object enumValue) {
        if (enumValue == null) return 0;
        try {
            if (ordinalMethod == null) {
                ordinalMethod = enumValue.getClass().getMethod("ordinal");
            }
            return (int) ordinalMethod.invoke(enumValue);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        Class<?> msgClass = msg.getClass();

        if (msgClass.getName().equals("net.minecraft.network.protocol.game.PacketPlayOutNamedSoundEffect")) {
            if (!fieldsInitialized) {
                initFields(msgClass);
            }

            if (fieldsAvailable && msgClass == soundPacketClass) {
                try {
                    Object soundHolder = soundHolderField.get(msg);
                    String soundName = extractSoundName(soundHolder);

                    if (soundName != null && soundFileManager.isModernSound(soundName)) {
                        Object categoryEnum = categoryField.get(msg);
                        int category = getOrdinal(categoryEnum);

                        int fixedX = (int) xField.get(msg);
                        int fixedY = (int) yField.get(msg);
                        int fixedZ = (int) zField.get(msg);
                        float volume = (float) volumeField.get(msg);
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
        }
        super.write(ctx, msg, promise);
    }
}
