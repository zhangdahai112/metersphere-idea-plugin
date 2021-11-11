package org.metersphere.constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginConstants {
    public static final String DISPLAY_NAME = "MeterSphere";

    public static final List<String> simpleJavaType = new ArrayList<>() {{
        add("int");
        add("java.lang.Integer");
        add("short");
        add("java.lang.Short");
        add("byte");
        add("java.lang.Byte");
        add("long");
        add("java.lang.Long");
        add("char");
        add("java.lang.Character");
        add("float");
        add("java.lang.Float");
        add("double");
        add("java.lang.Double");
        add("boolean");
        add("java.lang.Boolean");
        add("java.lang.String");
    }};

    public static final Map<String, Object> simpleJavaTypeValue = new HashMap<>() {{
        put("int", 0);
        put("Integer", 0);
        put("short", 0);
        put("Short", 0);
        put("byte", 0);
        put("Byte", 0);
        put("long", 0);
        put("Long", 0);
        put("char", "");
        put("Character", "");
        put("float", 0.0f);
        put("Float", 0.0f);
        put("double", 0.0d);
        put("Double", 0.0d);
        put("boolean", false);
        put("Boolean", false);
        put("java.lang.String", "");
    }};

    public enum MessageTitle {
        Info, Warning
    }
}
