package gui;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.function.Consumer;

/**
 * Embedded JavaFX WebView panel (Swing host) using reflection.
 *
 * This keeps compilation independent from JavaFX jars.
 * If JavaFX runtime is missing, a hint panel is shown.
 */
public class JavaFxWebViewPanel extends JPanel {
    private final Consumer<String> statusSink;
    private final String initialUrl;

    private JComponent jfxPanel;
    private Object webEngine;
    private boolean webViewReady = false;

    public JavaFxWebViewPanel(String initialUrl, Consumer<String> statusSink) {
        this.initialUrl = initialUrl;
        this.statusSink = statusSink;
        setLayout(new BorderLayout());
        initWebViewOrHint();
    }

    public boolean isWebViewReady() {
        return webViewReady;
    }

    public void reload() {
        loadUrl(initialUrl);
    }

    public void loadUrl(String url) {
        if (!webViewReady || webEngine == null) {
            pushStatus("Status: WebView runtime unavailable");
            return;
        }

        runOnFxThread(() -> {
            try {
                Method load = webEngine.getClass().getMethod("load", String.class);
                load.invoke(webEngine, url);
                pushStatus("Status: WebView loaded URL");
            } catch (Exception ex) {
                pushStatus("Status: WebView load failed - " + ex.getMessage());
            }
        });
    }

    private void initWebViewOrHint() {
        try {
            Class<?> jfxPanelClass = Class.forName("javafx.embed.swing.JFXPanel");
            Constructor<?> jfxCtor = jfxPanelClass.getConstructor();
            Object panelObject = jfxCtor.newInstance();

            if (!(panelObject instanceof JComponent)) {
                throw new IllegalStateException("JFXPanel is not a Swing component");
            }

            jfxPanel = (JComponent) panelObject;
            add(jfxPanel, BorderLayout.CENTER);

            runOnFxThread(() -> {
                try {
                    Class<?> webViewClass = Class.forName("javafx.scene.web.WebView");
                    Object webView = webViewClass.getConstructor().newInstance();

                    Method getEngine = webViewClass.getMethod("getEngine");
                    webEngine = getEngine.invoke(webView);

                    Method load = webEngine.getClass().getMethod("load", String.class);
                    load.invoke(webEngine, initialUrl);

                    try {
                        Class<?> callbackClass = Class.forName("javafx.util.Callback");
                        Class<?> promptDataClass = Class.forName("javafx.scene.web.PromptData");
                        
                        Object promptHandler = Proxy.newProxyInstance(
                            callbackClass.getClassLoader(),
                            new Class<?>[] { callbackClass },
                            new InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                    if (method.getName().equals("call") && args.length > 0) {
                                        Object promptData = args[0];
                                        
                                        Method getMessage = promptDataClass.getMethod("getMessage");
                                        String message = (String) getMessage.invoke(promptData);
                                        
                                        Method getDefaultValue = promptDataClass.getMethod("getDefaultValue");
                                        String defaultValue = (String) getDefaultValue.invoke(promptData);
                                        
                                        final String[] result = new String[1];
                                        SwingUtilities.invokeAndWait(() -> {
                                            result[0] = (String) JOptionPane.showInputDialog(
                                                JavaFxWebViewPanel.this, 
                                                message, 
                                                "System Message", 
                                                JOptionPane.QUESTION_MESSAGE, 
                                                null, 
                                                null, 
                                                defaultValue
                                            );
                                        });
                                        
                                        return result[0]; 
                                    }
                                    return null;
                                }
                            }
                        );

                        Method setPromptHandler = webEngine.getClass().getMethod("setPromptHandler", callbackClass);
                        setPromptHandler.invoke(webEngine, promptHandler);

                    } catch (Exception e) {
                        System.out.println("Failed to attach PromptHandler: " + e.getMessage());
                    }

                    Class<?> parentClass = Class.forName("javafx.scene.Parent");
                    Class<?> sceneClass = Class.forName("javafx.scene.Scene");
                    Constructor<?> sceneCtor = sceneClass.getConstructor(parentClass);
                    Object scene = sceneCtor.newInstance(webView);

                    Method setScene = jfxPanel.getClass().getMethod("setScene", sceneClass);
                    setScene.invoke(jfxPanel, scene);

                    webViewReady = true;
                    pushStatus("Status: JavaFX WebView ready");
                } catch (Exception ex) {
                    showHint("JavaFX WebView init failed", ex);
                    pushStatus("Status: WebView init failed");
                }
            });
        } catch (Throwable ex) {
            webViewReady = false;
            add(buildHintPanel(ex), BorderLayout.CENTER);
            pushStatus("Status: JavaFX runtime not available");
        }
    }

    private void runOnFxThread(Runnable task) {
        try {
            Class<?> platformClass = Class.forName("javafx.application.Platform");
            Method runLater = platformClass.getMethod("runLater", Runnable.class);
            runLater.invoke(null, task);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
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
                "JavaFX WebView runtime is not loaded.\n\n" +
                "To enable embedded WebView:\n" +
                "1) Download OpenJFX SDK matching your JDK\n" +
                "2) Put required JavaFX jars into lib/ (at least javafx-base, javafx-graphics, javafx-controls, javafx-web, javafx-swing, javafx-media)\n" +
                "3) Start via run_switch_gui.bat (classpath already includes lib/*)\n\n" +
                "Current error: " + ex.toString() + (ex.getCause() != null ? " - Cause: " + ex.getCause().toString() : "")
        );

        panel.add(new JLabel("JavaFX WebView Setup Required"), BorderLayout.NORTH);
        panel.add(new JScrollPane(hint), BorderLayout.CENTER);
        return panel;
    }

    private void showHint(String title, Exception ex) {
        SwingUtilities.invokeLater(() -> {
            removeAll();
            add(buildHintPanel(ex), BorderLayout.CENTER);
            revalidate();
            repaint();
        });
    }

    private void pushStatus(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }
}