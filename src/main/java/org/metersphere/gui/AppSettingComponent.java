package org.metersphere.gui;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.metersphere.AppSettingService;
import org.metersphere.state.AppSettingState;
import org.metersphere.utils.MSApiUtil;

import javax.swing.*;
import java.awt.event.*;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.metersphere.utils.MSApiUtil.test;

@Data
public class AppSettingComponent {

    private JPanel mainSettingPanel;
    private JTextField meterSphereAddress;
    private JTextField accesskey;
    private JPasswordField secretkey;
    private JButton testCon;
    private JTabbedPane settingPanel;
    private JComboBox apiType;
    private JComboBox projectId;
    private JComboBox moduleId;
    private JComboBox modeId;
    private AppSettingService appSettingService = ApplicationManager.getApplication().getService(AppSettingService.class);

    public AppSettingComponent() {
        AppSettingState appSettingState = appSettingService.getState();
        meterSphereAddress.setText(appSettingState.getMeterSphereAddress());
        accesskey.setText(appSettingState.getAccesskey());
        secretkey.setText(appSettingState.getSecretkey());
        modeId.setSelectedItem(appSettingState.getModeId());
        apiType.setSelectedItem(appSettingState.getApiType());
        if (appSettingState.getProjectNameList() != null) {
            appSettingState.getProjectNameList().forEach(p -> projectId.addItem(p));
        }
        if (StringUtils.isNotBlank(appSettingState.getProjectId())) {
            projectId.setSelectedItem(appSettingState.getProjectId());
        }
        if (appSettingState.getModuleNameList() != null) {
            appSettingState.getModuleNameList().forEach(p -> moduleId.addItem(p));
        }
        if (StringUtils.isNotBlank(appSettingState.getModuleId())) {
            moduleId.setSelectedItem(appSettingState.getModuleId());
        }
        testCon.addActionListener(actionEvent -> {
            if (test(appSettingState)) {
                init();
                Messages.showInfoMessage("Connect success!", "Info");
            } else {
                Messages.showInfoMessage("Connect fail!", "Info");
            }
        });
        meterSphereAddress.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                appSettingState.setMeterSphereAddress(meterSphereAddress.getText());
            }
        });
        accesskey.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                appSettingState.setAccesskey(accesskey.getText());
            }
        });
        secretkey.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                appSettingState.setSecretkey(secretkey.getText());
            }
        });
        projectId.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                if (projectId.getSelectedItem() != null && StringUtils.isNotBlank(projectId.getSelectedItem().toString())) {
                    if (appSettingState.getProjectList().size() > 0) {
                        String pId = ((JSONObject) appSettingState.getProjectList().stream().filter(p -> ((JSONObject) p).getString("name").equalsIgnoreCase(itemEvent.getItem().toString())).findFirst().get()).getString("id");
                        initModule(pId);
                    }
                }
            }
        });
        projectId.addActionListener(actionEvent -> {
            if (projectId.getItemCount() > 0)
                appSettingState.setProjectId(projectId.getSelectedItem().toString());
        });
        moduleId.addActionListener(actionEvent -> {
            if (moduleId.getItemCount() > 0)
                appSettingState.setModuleId(moduleId.getSelectedItem().toString());
        });
        modeId.addActionListener(actionEvent -> {
            appSettingState.setModeId(modeId.getSelectedItem().toString());
        });
    }

    private void init() {
        AppSettingState appSettingState = appSettingService.getState();

        //初始化项目
        JSONObject project = MSApiUtil.getProjectList(appSettingState);
        if (project != null && project.getBoolean("success")) {
            appSettingState.setProjectList(project.getJSONArray("data"));
            appSettingState.setProjectNameList(appSettingState.getProjectList().stream().map(p -> ((JSONObject) p).getString("name")).collect(Collectors.toList()));
            appSettingState.setProjectId(null);
            appSettingState.setProjectIdList(null);
        }
        //设置下拉选择框
        projectId.removeAllItems();
        for (String s : appSettingState.getProjectNameList()) {
            projectId.addItem(s);
        }

        //初始化模块
//        initModule(project.getJSONArray("data").getJSONObject(0).getString("id"));
    }

    /**
     * ms 项目id
     *
     * @param msProjectId
     */
    private void initModule(String msProjectId) {
        AppSettingState appSettingState = appSettingService.getState();

        //初始化模块
        JSONObject module = MSApiUtil.getModuleList(appSettingState, msProjectId, appSettingState.getApiType());

        if (module != null && module.getBoolean("success")) {
            appSettingState.setModuleList(module.getJSONArray("data"));
            appSettingState.setModuleNameList(appSettingState.getModuleList().stream().map(p -> ((JSONObject) p).getString("name")).collect(Collectors.toList()));
            appSettingState.setModuleId(null);
            appSettingState.setModuleIdList(null);
        }

        moduleId.removeAllItems();
        for (String s : appSettingState.getModuleNameList()) {
            moduleId.addItem(s);
        }
    }

    public JPanel getSettingPanel() {
        return this.mainSettingPanel;
    }
}
