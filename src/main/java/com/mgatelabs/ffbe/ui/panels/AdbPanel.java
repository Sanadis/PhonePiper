package com.mgatelabs.ffbe.ui.panels;

import com.mgatelabs.ffbe.shared.util.AdbShell;

import javax.swing.*;

/**
 * Created by @mgatelabs (Michael Fuller) on 9/21/2017.
 */
public class AdbPanel extends JInternalFrame {

    private final AdbShell adbShell;

    public AdbPanel(AdbShell adbShell) {
        super("ADB Status", true, false, false, false);
        this.adbShell = adbShell;
    }

    private void build() {


        pack();

        setVisible(true);
    }
}
