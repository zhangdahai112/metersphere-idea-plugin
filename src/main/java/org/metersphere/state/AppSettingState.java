package org.metersphere.state;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class AppSettingState {
    private String meterSphereAddress;
    private String accesskey;
    private String secretkey;
}
