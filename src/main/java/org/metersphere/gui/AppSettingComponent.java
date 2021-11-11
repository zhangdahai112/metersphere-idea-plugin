package org.metersphere.gui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AppSettingComponent {

    private JPanel mainSettingPanel;
    private JTextField meterSphereAddress;
    private JTextField accesskey;
    private JPasswordField secretkey;
    private JButton testCon;
    private JTabbedPane settingPanel;

    public AppSettingComponent() {
        testCon.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                Messages.showInfoMessage("hello", "MeterSphere");
            }
        });
    }

    public JPanel getSettingPanel() {
        return this.mainSettingPanel;
    }
}
