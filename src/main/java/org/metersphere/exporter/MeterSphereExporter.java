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
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.metersphere.AppSettingService;
import org.metersphere.constants.PluginConstants;
import org.metersphere.gui.AppSettingComponent;
import org.metersphere.model.PostmanModel;
import org.metersphere.state.AppSettingState;
import org.metersphere.utils.MSApiUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MeterSphereExporter implements IExporter {
    private Logger logger = Logger.getInstance(MeterSphereExporter.class);
    private PostmanExporter postmanExporter = new PostmanExporter();
    private AppSettingComponent appSettingComponent = ApplicationManager.getApplication().getService(AppSettingComponent.class);
    private AppSettingService appSettingService = ApplicationManager.getApplication().getService(AppSettingService.class);

    @Override
    public boolean export(PsiElement psiElement) {
        try {

            if (!MSApiUtil.test(appSettingService.getState())) {
                Messages.showInfoMessage("please input corrent ak sk!", PluginConstants.MessageTitle.Info.name());
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

    private boolean uploadToServer(File file) throws UnsupportedEncodingException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        AppSettingState state = appSettingService.getState();
        String url = state.getMeterSphereAddress() + "/api/definition/import";
        HttpPost httpPost = new HttpPost(url);// 创建httpPost
        httpPost.setHeader("Accept", "application/json, text/plain, */*");
//        httpPost.setHeader("Content-Type", "multipart/form-data");
        httpPost.setHeader("accesskey", appSettingService.getState().getAccesskey());
        httpPost.setHeader("signature", MSApiUtil.getSinature(appSettingService.getState()));
        CloseableHttpResponse response = null;

        JSONObject param = new JSONObject();
        param.put("modeId", state.getModeId());
        param.put("moduleId", ((JSONObject) state.getModuleList().stream().filter(p -> ((JSONObject) p).getString("name").equalsIgnoreCase(state.getModuleId())).findFirst().get()).getString("id"));
        param.put("platform", "Postman");
        param.put("model", "definition");
        param.put("projectId", ((JSONObject) state.getProjectList().stream().filter(p -> ((JSONObject) p).getString("name").equalsIgnoreCase(state.getProjectId())).findFirst().get()).getString("id"));
        JSONObject uid = new JSONObject();
        uid.put("uid", "sadasdasd");
        param.put("file", uid);
        HttpEntity formEntity = MultipartEntityBuilder.create().addBinaryBody("file", file,  ContentType.APPLICATION_JSON, null)
                .addBinaryBody("request", param.toJSONString().getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON, "pm.json").build();

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
            System.out.println(e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                httpclient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
