package org.metersphere.state;

import lombok.Data;

import java.util.List;

@Data
public class AppSettingState {
    private String meterSphereAddress;
    private String accesskey;
    private String secretkey;
    private List<String> apiTypeList;
    private String apiType = "http";

    private List<String> projectNameList;
    private List<MSProject> projectList;
    private String projectId;
    private String projectName;

    private List<MSModule> moduleList;
    private List<String> moduleNameList;
    private String moduleId;
    private String moduleName;

    private String modeId = "http";
}
