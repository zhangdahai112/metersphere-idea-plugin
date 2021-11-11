package org.metersphere.exporter;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.metersphere.constants.PluginConstants;
import org.metersphere.constants.SpringMappingConstants;
import org.metersphere.model.PostmanModel;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PostmanExporter implements IExporter {
    @Override
    public boolean export(PsiElement psiElement) {
        try {
            List<PsiJavaFile> files = new LinkedList<>();
            getFile(psiElement, files);
            files = files.stream().filter(f ->
                    f instanceof PsiJavaFile
            ).collect(Collectors.toList());
            if (files.size() == 0) {
                Messages.showInfoMessage("No java file detected! please change your search root", infoTitle());
                return false;
            }
            List<PostmanModel> postmanModels = transform(files);
            if (postmanModels.size() == 0) {
                Messages.showInfoMessage("No java api was found! please change your search root", infoTitle());
                return false;
            }
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("C:\\Users\\admin\\Desktop\\pm.json"));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("item", postmanModels);
            JSONObject info = new JSONObject();
            info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
            info.put("name", psiElement.getProject().getName() + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            info.put("description", "exported at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            jsonObject.put("info", info);
            bufferedWriter.write(new Gson().toJson(jsonObject));
            bufferedWriter.flush();
            bufferedWriter.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @NotNull
    private String infoTitle() {
        return PluginConstants.MessageTitle.Info.name();
    }

    private List<PsiJavaFile> getFile(PsiElement psiElement, List<PsiJavaFile> files) {
        if (psiElement instanceof PsiDirectoryImpl) {
            Arrays.stream(psiElement.getChildren()).forEach(p -> {
                if (p instanceof PsiJavaFile) {
                    files.add((PsiJavaFile) p);
                } else if (p instanceof PsiDirectoryImpl && ((PsiDirectoryImpl) p).canNavigate()) {
                    getFile(p, files);
                }
            });
        } else {
            if (psiElement.getContainingFile() instanceof PsiJavaFile) {
                files.add((PsiJavaFile) psiElement.getContainingFile());
            }
        }
        return files;
    }

    private List<PostmanModel> transform(List<PsiJavaFile> files) {
        List<PostmanModel> models = new LinkedList<>();
        files.forEach(f -> {

            PsiClass controllerClass = PsiTreeUtil.findChildOfType(f, PsiClass.class);
            if (controllerClass != null) {
                PostmanModel model = new PostmanModel();
                if (!f.getName().endsWith(".java")) return;
                model.setName(f.getName().replace(".java", ""));
                model.setDescription(model.getName());
                List<PostmanModel.ItemBean> itemBeans = new LinkedList<>();
                boolean isRequest = false;
                boolean restController = false;
                String basePath = "";

                //从注解里面找 RestController 和 RequestMapping 来确定请求头和 basepath
                PsiModifierList controllerModi = PsiTreeUtil.findChildOfType(controllerClass, PsiModifierList.class);
                if (controllerModi != null) {
                    Collection<PsiAnnotation> annotations = PsiTreeUtil.findChildrenOfType(controllerModi, PsiAnnotation.class);
                    if (annotations.size() > 0) {
                        Map<String, Boolean> r = containsAnnotation(annotations);
                        if (r.get("rest") || r.get("general")) {
                            isRequest = true;
                        }
                        if (r.get("rest")) {
                            restController = true;
                        }
                    }
                }

                if (isRequest) {
                    Collection<PsiAnnotation> annotations = PsiTreeUtil.findChildrenOfType(controllerModi, PsiAnnotation.class);
                    PsiAnnotation requestMappingA = annotations.stream().filter(a -> a.getQualifiedName().contains("RequestMapping")).findAny().get();
                    if (requestMappingA != null) {
                        basePath = PsiAnnotationUtil.getAnnotationValue(requestMappingA, String.class);
                    }

                    Collection<PsiMethod> methodCollection = PsiTreeUtil.findChildrenOfType(controllerClass, PsiMethod.class);
                    Iterator<PsiMethod> methodIterator = methodCollection.iterator();
                    while (methodIterator.hasNext()) {
                        PsiMethod e1 = methodIterator.next();
                        //注解
                        if (e1.getFirstChild() instanceof PsiModifierList) {
                            PostmanModel.ItemBean itemBean = new PostmanModel.ItemBean();
                            //方法名称
                            itemBean.setName(((PsiMethod) e1).getName());
                            PostmanModel.ItemBean.RequestBean requestBean = new PostmanModel.ItemBean.RequestBean();
                            //请求类型
                            requestBean.setMethod(getMethod((PsiModifierList) e1.getFirstChild()));
                            //url
                            PostmanModel.ItemBean.RequestBean.UrlBean urlBean = new PostmanModel.ItemBean.RequestBean.UrlBean();
                            urlBean.setHost("{{" + e1.getProject().getName() + "}}");
                            String urlStr = Optional.ofNullable(getUrlFromAnnotation(e1.getModifierList())).orElse("");
                            urlBean.setPath(getPath(urlStr, basePath));
                            urlBean.setQuery(getQuery((PsiMethod) e1, requestBean));
                            urlBean.setRaw(urlBean.getHost() + (urlStr.startsWith("/") ? urlStr : "/" + urlStr));
                            requestBean.setUrl(urlBean);
                            //header
                            List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans = new ArrayList<>();
                            if (restController) {
                                addRestHeader(headerBeans);
                            } else {
                                addFormHeader(headerBeans);
                            }
                            PsiElement headAn = findModifierInList(e1.getModifierList(), "headers");
                            if (headAn != null) {
                                PostmanModel.ItemBean.RequestBean.HeaderBean headerBean = new PostmanModel.ItemBean.RequestBean.HeaderBean();
                                String headerStr = PsiAnnotationUtil.getAnnotationValue((PsiAnnotation) headAn, "headers", String.class);
                                if (StringUtils.isNotBlank(headerStr)) {
                                    headerBean.setKey(headerStr.split("=")[0]);
                                    headerBean.setValue(headerStr.split("=")[1]);
                                    headerBean.setType("text");
                                    headerBeans.add(headerBean);
                                } else {
                                    Collection<PsiNameValuePair> heaerNVP = PsiTreeUtil.findChildrenOfType(headAn, PsiNameValuePair.class);
                                    Iterator<PsiNameValuePair> psiNameValuePairIterator = heaerNVP.iterator();
                                    while (psiNameValuePairIterator.hasNext()) {
                                        PsiNameValuePair ep1 = psiNameValuePairIterator.next();
                                        if (ep1.getText().contains("headers")) {
                                            Collection<PsiLiteralExpression> pleC = PsiTreeUtil.findChildrenOfType(headAn, PsiLiteralExpression.class);
                                            Iterator<PsiLiteralExpression> expressionIterator = pleC.iterator();
                                            while (expressionIterator.hasNext()) {

                                                PsiLiteralExpression ple = expressionIterator.next();
                                                String heaerItem = ple.getValue().toString();
                                                headerBean = new PostmanModel.ItemBean.RequestBean.HeaderBean();
                                                headerBean.setKey(heaerItem.split("=")[0]);
                                                headerBean.setValue(heaerItem.split("=")[1]);
                                                headerBean.setType("text");
                                                headerBeans.add(headerBean);
                                            }
                                        }
                                    }

                                }
                                requestBean.setHeader(removeDuplicate(headerBeans));

                                //body
                                PsiParameterList parameterList = e1.getParameterList();
                                for (PsiParameter pe : parameterList.getParameters()) {
                                    PsiAnnotation[] pAt = pe.getAnnotations();
                                    if (pAt != null && pAt.length != 0) {
                                        if (pe.getAnnotation("org.springframework.web.bind.annotation.RequestBody") != null || (pe.getAnnotation("org.springframework.web.bind.annotation.RequestPart") == null && !PluginConstants.simpleJavaType.contains(pe.getType().getCanonicalText()))) {
                                            PostmanModel.ItemBean.RequestBean.BodyBean bodyBean = new PostmanModel.ItemBean.RequestBean.BodyBean();
                                            bodyBean.setMode("raw");
                                            //:todo
                                            bodyBean.setRaw("");
                                            PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean optionsBean = new PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean();
                                            PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean.RawBean rawBean = new PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean.RawBean();
                                            rawBean.setLanguage("json");
                                            optionsBean.setRaw(rawBean);
                                            bodyBean.setOptions(optionsBean);
                                            requestBean.setBody(bodyBean);
                                            //隐式
                                            addRestHeader(headerBeans);
                                        }
                                    } else {
                                        String javaType = pe.getType().getCanonicalText();
                                        if (!PluginConstants.simpleJavaType.contains(javaType)) {
                                            PostmanModel.ItemBean.RequestBean.BodyBean bodyBean = new PostmanModel.ItemBean.RequestBean.BodyBean();
                                            bodyBean.setMode("raw");
                                            //:todo
                                            bodyBean.setRaw("");
                                            PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean optionsBean = new PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean();
                                            PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean.RawBean rawBean = new PostmanModel.ItemBean.RequestBean.BodyBean.OptionsBean.RawBean();
                                            rawBean.setLanguage("json");
                                            optionsBean.setRaw(rawBean);
                                            bodyBean.setOptions(optionsBean);
                                            requestBean.setBody(bodyBean);
                                            //隐式
                                            addFormHeader(headerBeans);
                                        }
                                    }
                                }
                                itemBean.setRequest(requestBean);
                                itemBeans.add(itemBean);
                            }

                        }
                    }
                    model.setItem(itemBeans);
                    if (isRequest)
                        models.add(model);
                }
            }
        });
        return models;
    }

    private void addFormHeader(List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans) {
        for (PostmanModel.ItemBean.RequestBean.HeaderBean headerBean : headerBeans) {
            if (headerBean.getKey().equalsIgnoreCase("Content-Type")) {
                headerBean.setKey("Content-Type");
                headerBean.setValue("application/x-www-form-urlencoded");
                headerBean.setType("text");
                return;
            }
        }
        PostmanModel.ItemBean.RequestBean.HeaderBean headerBean = new PostmanModel.ItemBean.RequestBean.HeaderBean();
        headerBean.setKey("Content-Type");
        headerBean.setValue("application/x-www-form-urlencoded");
        headerBean.setType("text");
        headerBeans.add(headerBean);
    }

    private List<PostmanModel.ItemBean.RequestBean.HeaderBean> removeDuplicate
            (List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans) {
        if (headerBeans != null && headerBeans.size() > 1) {
            headerBeans = headerBeans.stream().distinct().collect(Collectors.toList());
        }
        return headerBeans;
    }

    private List<String> getPath(String urlStr, String basePath) {
        String[] urls = urlStr.split("/");
        if (StringUtils.isNotBlank(basePath))
            urls = (basePath + "/" + urlStr).split("/");
        Pattern p = Pattern.compile("\\{(\\w+)\\}");
        return Arrays.stream(urls).map(s -> {
            Matcher m = p.matcher(s);
            while (m.find()) {
                s = ":" + m.group(1);
            }
            return s;
        }).filter(s -> StringUtils.isNotBlank(s)).collect(Collectors.toList());
    }

    private void addRestHeader(List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans) {
        for (PostmanModel.ItemBean.RequestBean.HeaderBean headerBean : headerBeans) {
            if (headerBean.getKey().equalsIgnoreCase("Content-Type")) {
                headerBean.setKey("Content-Type");
                headerBean.setValue("application/json");
                headerBean.setType("text");
                return;
            }
        }
        PostmanModel.ItemBean.RequestBean.HeaderBean headerBean = new PostmanModel.ItemBean.RequestBean.HeaderBean();
        headerBean.setKey("Content-Type");
        headerBean.setValue("application/json");
        headerBean.setType("text");
        headerBeans.add(headerBean);
    }

    private List<?> getQuery(PsiMethod e1, PostmanModel.ItemBean.RequestBean requestBean) {
        List<JSONObject> r = new ArrayList<>();
        PsiParameterList parametersList = e1.getParameterList();
        PsiParameter[] parameter = parametersList.getParameters();
        for (PsiParameter psiParameter : parameter) {
            PsiAnnotation[] pAt = psiParameter.getAnnotations();
            if (pAt != null && pAt.length != 0) {
                if (psiParameter.getAnnotation("org.springframework.web.bind.annotation.RequestBody") == null && psiParameter.getAnnotation("org.springframework.web.bind.annotation.RequestPart") == null && psiParameter.getAnnotation("org.springframework.web.bind.annotation.PathVariable") == null) {
                    JSONObject stringParam = new JSONObject();
                    stringParam.put("key", psiParameter.getName());
                    stringParam.put("value", "");
                    stringParam.put("equals", true);
                    stringParam.put("description", "");
                    r.add(stringParam);
                } else {
                    if ("REQUEST".equalsIgnoreCase(requestBean.getMethod()))
                        requestBean.setMethod("POST");
                }
            } else {
                String javaType = psiParameter.getType().getCanonicalText();
                if (PluginConstants.simpleJavaType.contains(javaType)) {
                    JSONObject stringParam = new JSONObject();
                    stringParam.put("key", psiParameter.getName());
                    stringParam.put("value", "");
                    stringParam.put("equals", true);
                    stringParam.put("description", "");
                    r.add(stringParam);
                } else {
                    if ("REQUEST".equalsIgnoreCase(requestBean.getMethod()))
                        requestBean.setMethod("POST");
                }
            }
        }
        return r;
    }

    private String getMethod(PsiModifierList modifierList) {
        for (String s : SpringMappingConstants.mapList) {
            if (findModifierInList(modifierList, s) != null) {
                return s.replace("Mapping", "").toUpperCase();
            }
        }
        return "Unknown Method";
    }

    private PsiAnnotation findReqAnnotation(PsiModifierList modifierList) {
        for (String s : SpringMappingConstants.mapList) {
            PsiAnnotation annotation = (PsiAnnotation) findModifierInList(modifierList, s);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    public static PsiElement findModifierInList(@NotNull PsiModifierList modifierList, String modifier) {
        PsiElement[] children = modifierList.getChildren();
        for (PsiElement child : children) {
            if (child.getText().contains(modifier)) return child;
        }
        return null;
    }

    private String getUrlFromAnnotation(PsiModifierList modifierList) {
        PsiAnnotation annotation = findReqAnnotation(modifierList);
        if (annotation != null) {
            return PsiAnnotationUtil.getAnnotationValue(annotation, String.class);
        }
        return null;
    }

    private Map<String, Boolean> containsAnnotation(Collection<PsiAnnotation> annotations) {
        Map r = new HashMap();
        r.put("rest", false);
        r.put("general", false);
        Iterator<PsiAnnotation> it = annotations.iterator();
        while (it.hasNext()) {
            PsiAnnotation next = it.next();
            if (next.hasQualifiedName("org.springframework.web.bind.annotation.RestController"))
                r.put("rest", true);
            if (next.hasQualifiedName("org.springframework.stereotype.Controller"))
                r.put("general", true);
        }
        return r;
    }

}
