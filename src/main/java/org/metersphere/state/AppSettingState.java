package org.metersphere.state;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 存储映射类
 */
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

    public String getMeterSphereAddress() {
        if (StringUtils.isNotBlank(this.meterSphereAddress)) {
            if (this.meterSphereAddress.endsWith("/")) {
                return this.meterSphereAddress.substring(0, this.meterSphereAddress.length() - 1);
            }
        }
        return this.meterSphereAddress;
    }
}
