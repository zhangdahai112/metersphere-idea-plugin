package org.metersphere.state;

import com.alibaba.fastjson.JSONArray;
import lombok.Data;

import java.util.List;

@Data
public class AppSettingState {
    private String meterSphereAddress;
    private String accesskey;
    private String secretkey;
    private List<String> apiTypeList;
    private String apiType = "http";

    private List<String> projectIdList;
    private List<String> projectNameList;
    private JSONArray projectList;
    private String projectId;

    private List<String> moduleIdList;
    private JSONArray moduleList;
    private List<String> moduleNameList;
    private String moduleId;

    private String modeId = "http";
}
