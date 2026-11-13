package com.querydesigner;

import com.querydesigner.ui.MainFrame;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point. Pass {@code --demo} to open the built-in H2 demo database on start. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep the default look and feel
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            if (Arrays.asList(args).contains("--demo")) {
                frame.openDemo();
            }
        });
    }
}
