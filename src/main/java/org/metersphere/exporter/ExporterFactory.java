package org.metersphere.exporter;

import com.intellij.psi.PsiElement;

import java.util.HashMap;
import java.util.Map;

public class ExporterFactory {
    private static Map<String, IExporter> exporterMap = new HashMap<>() {{
        put("", new PostmanExporter());
    }};

    public static boolean export(String source, PsiElement psiElement) {
        try {
            exporterMap.get(source).export(psiElement);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
