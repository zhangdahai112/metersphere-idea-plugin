package org.metersphere.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.metersphere.exporter.ExporterFactory;

import java.util.LinkedList;
import java.util.List;

public class ProjectViewAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        String source = "";

//        List<PsiJavaFile> fileList = new LinkedList<>();
//        event.getData(CommonDataKeys.PSI_ELEMENT).getNavigationElement().accept(new PsiElementVisitor() {
//            @Override
//            public void visitFile(@NotNull PsiFile file) {
//                if(file instanceof PsiJavaFile){
//                    fileList.add((PsiJavaFile) file);
//                }
//            }
//        });
//        System.out.println(fileList);
        ExporterFactory.export(source, event.getData(CommonDataKeys.PSI_ELEMENT).getOriginalElement());
        //        for (VirtualFile file : vFiles) {
//            sourceRootsList.append(file.getUrl()).append("\n");
//        }
//        Messages.showInfoMessage(
//                "Source roots for the " + projectName + " plugin:\n" + sourceRootsList.toString(),
//                "Project Properties"
//        );
    }
}
