package org.metersphere.constants;

import java.util.ArrayList;
import java.util.List;

public class PluginConstants {
    public static final String DISPLAY_NAME = "MeterSphere";

    public static final List<String> simpleJavaType = new ArrayList<>() {{
        add("int");
        add("short");
        add("byte");
        add("long");
        add("char");
        add("float");
        add("double");
        add("boolean");
        add("java.lang.String");
    }};

    public enum MessageTitle {
        Info, Warning
    }
}
