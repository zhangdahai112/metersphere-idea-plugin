package org.metersphere.gui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.codec.binary.Base64;
import org.metersphere.AppSettingService;
import org.metersphere.state.AppSettingState;
import org.metersphere.utils.CodingUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.event.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class AppSettingComponent {

    private JPanel mainSettingPanel;
    private JTextField meterSphereAddress;
    private JTextField accesskey;
    private JPasswordField secretkey;
    private JButton testCon;
    private JTabbedPane settingPanel;
    private AppSettingService appSettingService = ApplicationManager.getApplication().getService(AppSettingService.class);

    public AppSettingComponent() {
        meterSphereAddress.setText(appSettingService.getState().getMeterSphereAddress());
        accesskey.setText(appSettingService.getState().getAccesskey());
        secretkey.setText(appSettingService.getState().getSecretkey());
        testCon.addActionListener(actionEvent -> {
            if (test()) {
                Messages.showInfoMessage("Connect success!", "Info");
            } else {
                Messages.showInfoMessage("Connect fail!", "Info");
            }
        });
        meterSphereAddress.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                appSettingService.getState().setMeterSphereAddress(meterSphereAddress.getText());
            }
        });
        accesskey.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                appSettingService.getState().setAccesskey(accesskey.getText());
            }
        });
        secretkey.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                appSettingService.getState().setSecretkey(secretkey.getText());
            }
        });
    }

    public JPanel getSettingPanel() {
        return this.mainSettingPanel;
    }

    public boolean test() {
        try {
            URL url = new URL(String.format("%s/license/valid", meterSphereAddress.getText()));
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Accept", "application/json;charset=UTF-8");
            urlConnection.setRequestProperty("accessKey", accesskey.getText());
            String signature = aesEncrypt(accesskey.getText() + "|" + UUID.randomUUID().toString() + "|" + System.currentTimeMillis(), secretkey.getText(), accesskey.getText());
            urlConnection.setRequestProperty("signature", signature);
            InputStream is = urlConnection.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while (-1 != (len = is.read(buffer))) {
                baos.write(buffer, 0, len);
                baos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static String aesEncrypt(String src, String secretKey, String iv) throws Exception {
        byte[] raw = secretKey.getBytes("UTF-8");
        SecretKeySpec secretKeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec iv1 = new IvParameterSpec(iv.getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, iv1);
        byte[] encrypted = cipher.doFinal(src.getBytes("UTF-8"));
        return Base64.encodeBase64String(encrypted);
    }
}
