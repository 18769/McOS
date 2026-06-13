package gui;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Chromium browser host using JCEF via reflection.
 *
 * This class compiles without JCEF jars. If JCEF is unavailable at runtime,
 * it shows an installation hint in the panel.
 */
public class ChromiumBrowserPanel extends JPanel {
    private final Consumer<String> statusSink;
    private final String initialUrl;

    private Object cefApp;
    private Object cefClient;
    private Object cefBrowser;

    private boolean chromiumReady = false;

    public ChromiumBrowserPanel(String initialUrl, Consumer<String> statusSink) {
        this.initialUrl = initialUrl;
        this.statusSink = statusSink;
        setLayout(new BorderLayout());
        initChromiumOrHint();
    }

    public boolean isChromiumReady() {
        return chromiumReady;
    }

    public void loadUrl(String url) {
        if (!chromiumReady || cefBrowser == null) {
            pushStatus("Status: Chromium runtime unavailable");
            return;
        }

        try {
            Method loadUrl = cefBrowser.getClass().getMethod("loadURL", String.class);
            loadUrl.invoke(cefBrowser, url);
            pushStatus("Status: Chromium loaded URL");
        } catch (Exception ex) {
            pushStatus("Status: Chromium load failed - " + ex.getMessage());
        }
    }

    public void reload() {
        loadUrl(initialUrl);
    }

    private void initChromiumOrHint() {
        try {
            Class<?> cefAppClass = Class.forName("org.cef.CefApp");
            Method getInstance = cefAppClass.getMethod("getInstance");
            cefApp = getInstance.invoke(null);

            Method createClient = cefApp.getClass().getMethod("createClient");
            cefClient = createClient.invoke(cefApp);

            Method createBrowser = cefClient.getClass().getMethod("createBrowser", String.class, boolean.class, boolean.class);
            cefBrowser = createBrowser.invoke(cefClient, initialUrl, false, false);

            Method getUiComponent = cefBrowser.getClass().getMethod("getUIComponent");
            Object ui = getUiComponent.invoke(cefBrowser);

            if (!(ui instanceof Component)) {
                throw new IllegalStateException("JCEF UI component type is invalid");
            }

            add((Component) ui, BorderLayout.CENTER);
            chromiumReady = true;
            pushStatus("Status: Chromium embedded browser ready");
        } catch (Throwable ex) {
            chromiumReady = false;
            add(buildHintPanel(ex), BorderLayout.CENTER);
            pushStatus("Status: Chromium not available (JCEF missing)");
        }
    }

    private JPanel buildHintPanel(Throwable ex) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JTextArea hint = new JTextArea();
        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        hint.setText(
                "Chromium runtime is not loaded.\n\n" +
                "To enable embedded Chromium (JCEF):\n" +
                "1) Put JCEF jars into lib/\n" +
                "2) Put JCEF native files next to the jars as required by your JCEF build\n" +
                "3) Start GUI via run_switch_gui.bat (classpath already supports lib/*)\n\n" +
                "Current error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()
        );

        panel.add(new JLabel("Chromium (JCEF) Setup Required"), BorderLayout.NORTH);
        panel.add(new JScrollPane(hint), BorderLayout.CENTER);
        return panel;
    }

    private void pushStatus(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }
}
