import java.awt.event.KeyEvent;
import java.util.Properties;

/**
 * Keyboard bindings for viewport controls.
 */
public class InputBinding
{
    private final int upKey;
    private final int downKey;
    private final int leftKey;
    private final int rightKey;
    private final int zoomInKey;
    private final int zoomOutKey;

    public InputBinding(int upKey, int downKey, int leftKey, int rightKey,
                        int zoomInKey, int zoomOutKey)
    {
        this.upKey = upKey;
        this.downKey = downKey;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.zoomInKey = zoomInKey;
        this.zoomOutKey = zoomOutKey;
    }

    public static InputBinding defaultBinding()
    {
        return new InputBinding(KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_A,
                                KeyEvent.VK_D, KeyEvent.VK_Q, KeyEvent.VK_E);
    }

    public static InputBinding fromProperties(Properties properties)
    {
        InputBinding defaults = defaultBinding();
        if(properties == null) {
            return defaults;
        }
        return new InputBinding(
            key(properties, "up", defaults.upKey),
            key(properties, "down", defaults.downKey),
            key(properties, "left", defaults.leftKey),
            key(properties, "right", defaults.rightKey),
            key(properties, "zoomIn", defaults.zoomInKey),
            key(properties, "zoomOut", defaults.zoomOutKey));
    }

    public int getUpKey()
    {
        return upKey;
    }

    public int getDownKey()
    {
        return downKey;
    }

    public int getLeftKey()
    {
        return leftKey;
    }

    public int getRightKey()
    {
        return rightKey;
    }

    public int getZoomInKey()
    {
        return zoomInKey;
    }

    public int getZoomOutKey()
    {
        return zoomOutKey;
    }

    private static int key(Properties properties, String name, int fallback)
    {
        String value = properties.getProperty(name);
        if(value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase();
        if(normalized.length() == 1) {
            return KeyEvent.getExtendedKeyCodeForChar(normalized.charAt(0));
        }
        try {
            java.lang.reflect.Field field =
                KeyEvent.class.getField("VK_" + normalized);
            return field.getInt(null);
        }
        catch(ReflectiveOperationException e) {
            return fallback;
        }
    }
}
