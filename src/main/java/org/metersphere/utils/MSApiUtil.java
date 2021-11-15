package org.metersphere.utils;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.application.ApplicationManager;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.metersphere.gui.AppSettingComponent;
import org.metersphere.state.AppSettingState;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class MSApiUtil {

    static AppSettingComponent appSettingComponent = ApplicationManager.getApplication().getService(AppSettingComponent.class);

    public static boolean test(AppSettingState appSettingState) {
        try {
            URL url = new URL(String.format("%s/license/valid", appSettingState.getMeterSphereAddress()));
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Accept", "application/json;charset=UTF-8");
            urlConnection.setRequestProperty("accessKey", appSettingState.getAccesskey());
            urlConnection.setRequestProperty("signature", getSinature(appSettingState));
            InputStream is = urlConnection.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while (-1 != (len = is.read(buffer))) {
                baos.write(buffer, 0, len);
                baos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static String getSinature(AppSettingState appSettingState) {
        return CodingUtil.aesEncrypt(appSettingState.getAccesskey() + "|" + UUID.randomUUID().toString() + "|" + System.currentTimeMillis(), appSettingState.getSecretkey(), appSettingState.getAccesskey());
    }

    public static JSONObject getProjectList(AppSettingState appSettingState) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(appSettingState.getMeterSphereAddress() + "/project/listAll");
        httpGet.addHeader("accessKey", appSettingState.getAccesskey());
        httpGet.addHeader("signature", getSinature(appSettingState));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() == 200) {
                return JSONObject.parseObject(EntityUtils.toString(response.getEntity()));
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * @param appSettingState
     * @param projectId       项目id
     * @param protocol        协议类型 http,tcp,dubbo,sql
     * @return
     */
    public static JSONObject getModuleList(AppSettingState appSettingState, String projectId, String protocol) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(appSettingState.getMeterSphereAddress() + "/api/module/list/" + projectId + "/" + protocol);
        httpGet.addHeader("accessKey", appSettingState.getAccesskey());
        httpGet.addHeader("signature", getSinature(appSettingState));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() == 200) {
                return JSONObject.parseObject(EntityUtils.toString(response.getEntity()));
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
