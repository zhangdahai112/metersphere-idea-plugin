package org.metersphere.exporter;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.metersphere.AppSettingService;
import org.metersphere.constants.PluginConstants;
import org.metersphere.model.PostmanModel;
import org.metersphere.state.AppSettingState;
import org.metersphere.utils.MSApiUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MeterSphereExporter implements IExporter {
    private Logger logger = Logger.getInstance(MeterSphereExporter.class);
    private PostmanExporter postmanExporter = new PostmanExporter();
    private AppSettingService appSettingService = ApplicationManager.getApplication().getService(AppSettingService.class);

    @Override
    public boolean export(PsiElement psiElement) {
        try {

            if (!MSApiUtil.test(appSettingService.getState())) {
                Messages.showInfoMessage("please input correct ak sk!", PluginConstants.MessageTitle.Info.name());
                return false;
            }

            List<PsiJavaFile> files = new LinkedList<>();
            postmanExporter.getFile(psiElement, files);
            files = files.stream().filter(f ->
                    f instanceof PsiJavaFile
            ).collect(Collectors.toList());
            if (files.size() == 0) {
                Messages.showInfoMessage("No java file detected! please change your search root", PluginConstants.MessageTitle.Info.name());
                return false;
            }
            List<PostmanModel> postmanModels = postmanExporter.transform(files, false);
            if (postmanModels.size() == 0) {
                Messages.showInfoMessage("No java api was found! please change your search root", PluginConstants.MessageTitle.Info.name());
                return false;
            }
            File temp = File.createTempFile(UUID.randomUUID().toString(), null);
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(temp));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("item", postmanModels);
            JSONObject info = new JSONObject();
            info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            info.put("name", psiElement.getProject().getName() + dateTime);
            info.put("description", "exported at " + dateTime);
            jsonObject.put("info", info);
            bufferedWriter.write(new Gson().toJson(jsonObject));
            bufferedWriter.flush();
            bufferedWriter.close();

            boolean r = uploadToServer(temp);
            if (r) {
                Messages.showInfoMessage("export to MeterSphere success!", PluginConstants.MessageTitle.Info.name());
            } else {
                Messages.showInfoMessage("export to MeterSphere fail!", PluginConstants.MessageTitle.Info.name());
            }
            if (temp.exists()) {
                temp.delete();
            }
            return true;
        } catch (Exception e) {
            logger.error("MeterSphere plugin export to metersphere error start......");
            logger.error(e);
            logger.error("MeterSphere plugin export to metersphere error end......");
            return false;
        }
    }

    private boolean uploadToServer(File file) {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        AppSettingState state = appSettingService.getState();
        String url = state.getMeterSphereAddress() + "/api/definition/import";
        HttpPost httpPost = new HttpPost(url);// 创建httpPost
        httpPost.setHeader("Accept", "application/json, text/plain, */*");
        httpPost.setHeader("accesskey", appSettingService.getState().getAccesskey());
        httpPost.setHeader("signature", MSApiUtil.getSinature(appSettingService.getState()));
        CloseableHttpResponse response = null;

        JSONObject param = new JSONObject();
        param.put("modeId", state.getModeId());
        param.put("moduleId", state.getModuleList().stream().filter(p -> p.getName().equalsIgnoreCase(state.getModuleName())).findFirst().get().getId());
        param.put("platform", "Postman");
        param.put("model", "definition");
        param.put("projectId", state.getProjectList().stream().filter(p -> p.getName().equalsIgnoreCase(state.getProjectName())).findFirst().get().getId());
        HttpEntity formEntity = MultipartEntityBuilder.create().addBinaryBody("file", file, ContentType.APPLICATION_JSON, null)
                .addBinaryBody("request", param.toJSONString().getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON, null).build();

        httpPost.setEntity(formEntity);
        try {
            response = httpclient.execute(httpPost);
            StatusLine status = response.getStatusLine();
            int statusCode = status.getStatusCode();
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            logger.error("上传至 MS 失败！", e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error("关闭 response 失败！", e);
                }
            }
            try {
                httpclient.close();
            } catch (IOException e) {
                logger.error("关闭 httpclient 失败！", e);
            }
        }
        return false;
    }
}
