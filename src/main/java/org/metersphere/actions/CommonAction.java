package org.metersphere.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.metersphere.exporter.ExporterFactory;

public abstract class CommonAction extends AnAction {
    protected boolean export(String source, AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return false;
        }

        return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                ProgressManager.getGlobalProgressIndicator().setText("begin...");
                ExporterFactory.export(source, event);
            });
        }, "Exporting api to MeterSphere please wait...", true, event.getProject());
    }

}
