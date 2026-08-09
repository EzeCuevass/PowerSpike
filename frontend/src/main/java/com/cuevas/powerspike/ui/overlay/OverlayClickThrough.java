package com.cuevas.powerspike.ui.overlay;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilidad JNA para aplicar click-through a una ventana JavaFX en Windows.
 *
 * Aplica los estilos extendidos WS_EX_TRANSPARENT y WS_EX_LAYERED al HWND
 * de la ventana, haciendo que los clicks pasen a través de ella.
 */
public final class OverlayClickThrough {

    private static final Logger log = LoggerFactory.getLogger(OverlayClickThrough.class);

    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TRANSPARENT = 0x00000020;
    private static final int WS_EX_LAYERED = 0x00080000;

    private OverlayClickThrough() {}

    /**
     * Hace que la ventana sea click-through.
     * Debe llamarse DESPUÉS de stage.show() para que el HWND exista.
     */
    public static void apply(Stage stage) {
        try {
            HWND hwnd = findWindowByTitle(stage.getTitle());
            if (hwnd == null) {
                log.warn("No se encontró HWND para el overlay '{}'", stage.getTitle());
                return;
            }
            int style = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE);
            User32.INSTANCE.SetWindowLong(hwnd, GWL_EXSTYLE,
                    style | WS_EX_TRANSPARENT | WS_EX_LAYERED);
            log.debug("Click-through aplicado al overlay '{}'", stage.getTitle());
        } catch (Exception e) {
            log.error("Error aplicando click-through: {}", e.getMessage());
        }
    }

    private static HWND findWindowByTitle(String title) {
        if (title == null || title.isEmpty()) return null;
        return User32.INSTANCE.FindWindow(null, title);
    }
}
