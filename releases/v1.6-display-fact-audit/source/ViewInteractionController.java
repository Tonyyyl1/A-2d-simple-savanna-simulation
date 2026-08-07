import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Independent controller for viewport pan/zoom input.
 */
public class ViewInteractionController
{
    private static final double INSPECT_ZOOM = 2.5;
    private static final double ZOOM_IN_FACTOR = 1.18;
    private static final double ZOOM_OUT_FACTOR = 1.0 / ZOOM_IN_FACTOR;
    private static final int KEY_PAN_PIXELS = 42;

    private final ViewportTransform transform;
    private final InteractionMode mode;
    private final InputBinding binding;
    private final List<ViewportChangeListener> listeners;
    private int lastDragX;
    private int lastDragY;

    public ViewInteractionController()
    {
        this(InteractionMode.HYBRID, InputBinding.defaultBinding());
    }

    public ViewInteractionController(InteractionMode mode, InputBinding binding)
    {
        this.mode = mode == null ? InteractionMode.HYBRID : mode;
        this.binding = binding == null ? InputBinding.defaultBinding() : binding;
        transform = new ViewportTransform();
        listeners = new ArrayList<>();
    }

    public ViewportTransform getTransform()
    {
        return transform;
    }

    public InteractionMode getMode()
    {
        return mode;
    }

    public boolean isInspectMode()
    {
        return transform.getZoom() >= INSPECT_ZOOM;
    }

    public void addViewportChangeListener(ViewportChangeListener listener)
    {
        if(listener != null) {
            listeners.add(listener);
        }
    }

    public void register(Component component, SizeProvider sizeProvider)
    {
        component.setFocusable(true);
        if(allowsMouse()) {
            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent event)
                {
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                    component.requestFocusInWindow();
                }

                public void mouseDragged(MouseEvent event)
                {
                    pan(event.getX() - lastDragX, event.getY() - lastDragY,
                        sizeProvider);
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                    component.repaint();
                }

                public void mouseWheelMoved(MouseWheelEvent event)
                {
                    double factor = event.getWheelRotation() < 0 ?
                        ZOOM_IN_FACTOR : ZOOM_OUT_FACTOR;
                    zoomBy(factor, event.getX(), event.getY(), sizeProvider);
                    component.repaint();
                }
            };
            component.addMouseListener(mouse);
            component.addMouseMotionListener(mouse);
            component.addMouseWheelListener(mouse);
        }
        if(allowsKeyboard()) {
            component.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent event)
                {
                    if(handleKey(event.getKeyCode(), component.getWidth(),
                                 component.getHeight(), sizeProvider)) {
                        component.repaint();
                    }
                }
            });
        }
    }

    public boolean handleKey(int keyCode, int viewWidth, int viewHeight,
                             SizeProvider sizeProvider)
    {
        if(!allowsKeyboard()) {
            return false;
        }
        if(keyCode == binding.getUpKey()) {
            pan(0, KEY_PAN_PIXELS, sizeProvider);
            return true;
        }
        if(keyCode == binding.getDownKey()) {
            pan(0, -KEY_PAN_PIXELS, sizeProvider);
            return true;
        }
        if(keyCode == binding.getLeftKey()) {
            pan(KEY_PAN_PIXELS, 0, sizeProvider);
            return true;
        }
        if(keyCode == binding.getRightKey()) {
            pan(-KEY_PAN_PIXELS, 0, sizeProvider);
            return true;
        }
        if(keyCode == binding.getZoomInKey()) {
            zoomBy(ZOOM_IN_FACTOR, viewWidth / 2, viewHeight / 2, sizeProvider);
            return true;
        }
        if(keyCode == binding.getZoomOutKey()) {
            zoomBy(ZOOM_OUT_FACTOR, viewWidth / 2, viewHeight / 2, sizeProvider);
            return true;
        }
        return false;
    }

    public void pan(double deltaX, double deltaY, SizeProvider sizeProvider)
    {
        double oldZoom = transform.getZoom();
        double oldOffsetX = transform.getOffsetX();
        double oldOffsetY = transform.getOffsetY();
        transform.pan(deltaX, deltaY, sizeProvider.viewWidth(),
                      sizeProvider.viewHeight(), sizeProvider.contentWidth(),
                      sizeProvider.contentHeight());
        notifyIfChanged(oldZoom, oldOffsetX, oldOffsetY);
    }

    public void zoomBy(double factor, int anchorX, int anchorY,
                       SizeProvider sizeProvider)
    {
        double oldZoom = transform.getZoom();
        double oldOffsetX = transform.getOffsetX();
        double oldOffsetY = transform.getOffsetY();
        transform.zoomBy(factor, anchorX, anchorY, sizeProvider.viewWidth(),
                         sizeProvider.viewHeight(), sizeProvider.contentWidth(),
                         sizeProvider.contentHeight());
        notifyIfChanged(oldZoom, oldOffsetX, oldOffsetY);
    }

    private void notifyIfChanged(double oldZoom, double oldOffsetX,
                                 double oldOffsetY)
    {
        if(Math.abs(oldZoom - transform.getZoom()) > 0.0001 ||
           Math.abs(oldOffsetX - transform.getOffsetX()) > 0.0001 ||
           Math.abs(oldOffsetY - transform.getOffsetY()) > 0.0001) {
            for(ViewportChangeListener listener : listeners) {
                listener.viewportChanged();
            }
        }
    }

    private boolean allowsMouse()
    {
        return mode == InteractionMode.MOUSE_ONLY || mode == InteractionMode.HYBRID;
    }

    private boolean allowsKeyboard()
    {
        return mode == InteractionMode.KEYBOARD_ONLY || mode == InteractionMode.HYBRID;
    }

    public interface SizeProvider
    {
        int viewWidth();

        int viewHeight();

        int contentWidth();

        int contentHeight();
    }

    public interface ViewportChangeListener
    {
        void viewportChanged();
    }
}
