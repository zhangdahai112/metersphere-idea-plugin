package org.metersphere.exporter;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.gson.Gson;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
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
            List<PostmanModel> postmanModels = transform(files, true);
            if (postmanModels.size() == 0) {
                Messages.showInfoMessage("No java api was found! please change your search root", infoTitle());
                return false;
            }
            FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            fileChooserDescriptor.setDescription("Choose the location you want to export");
            FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(fileChooserDescriptor, null, null);
            VirtualFile file[] = fileChooserDialog.choose(psiElement.getProject(), new VirtualFile[]{});
            if (file.length == 0) {
                Messages.showInfoMessage("No directory selected", infoTitle());
                return false;
            } else {
                Messages.showInfoMessage(String.format("will be exported to %s", file[0].getCanonicalPath() + "/postman.json"), infoTitle());
            }

            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file[0].getCanonicalPath() + "/postman.json"));
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
            return true;
        } catch (Exception e) {
            logger.error("MeterSphere plugin export to postman error start......");
            logger.error(e);
            logger.error("MeterSphere plugin export to postman error end......");
            return false;
        }
    }

    @NotNull
    public String infoTitle() {
        return PluginConstants.MessageTitle.Info.name();
    }

    public List<PsiJavaFile> getFile(PsiElement psiElement, List<PsiJavaFile> files) {
        if (psiElement instanceof PsiDirectory) {
            Arrays.stream(psiElement.getChildren()).forEach(p -> {
                if (p instanceof PsiJavaFile) {
                    files.add((PsiJavaFile) p);
                } else if (p instanceof PsiDirectory) {
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

    Logger logger = Logger.getInstance(PostmanExporter.class);

    public List<PostmanModel> transform(List<PsiJavaFile> files, boolean withBasePath) {
        List<PostmanModel> models = new LinkedList<>();
        files.forEach(f -> {
            logger.info(f.getText() + "...........");
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
                    List<PsiAnnotation> annotations = PsiTreeUtil.findChildrenOfType(controllerModi, PsiAnnotation.class).stream().filter(a -> a.getQualifiedName().contains("RequestMapping")).collect(Collectors.toList());
                    PsiAnnotation requestMappingA = annotations.size() > 0 ? annotations.get(0) : null;
                    if (requestMappingA != null) {
                        basePath = PsiAnnotationUtil.getAnnotationValue(requestMappingA, String.class);
                        if (StringUtils.isNotBlank(basePath) && basePath.startsWith("/")) {
                            basePath = basePath.replaceFirst("/", "");
                        }
                    }

                    Collection<PsiMethod> methodCollection = PsiTreeUtil.findChildrenOfType(controllerClass, PsiMethod.class);
                    Iterator<PsiMethod> methodIterator = methodCollection.iterator();
                    while (methodIterator.hasNext()) {
                        PsiMethod e1 = methodIterator.next();
                        //注解
                        Optional<PsiAnnotation> mapO = findMappingAnn(e1, PsiAnnotation.class);

                        if (mapO.isPresent()) {
                            PostmanModel.ItemBean itemBean = new PostmanModel.ItemBean();
                            //方法名称
                            itemBean.setName(e1.getName());
                            PostmanModel.ItemBean.RequestBean requestBean = new PostmanModel.ItemBean.RequestBean();
                            //请求类型
                            requestBean.setMethod(getMethod(mapO.get()));
                            if (requestBean.getMethod().equalsIgnoreCase("Unknown Method")) {
                                //MessageMapping 等不是 rest 接口
                                isRequest = false;
                                continue;
                            }
                            //url
                            PostmanModel.ItemBean.RequestBean.UrlBean urlBean = new PostmanModel.ItemBean.RequestBean.UrlBean();
                            urlBean.setHost("{{" + e1.getProject().getName() + "}}");
                            String urlStr = Optional.ofNullable(getUrlFromAnnotation(e1)).orElse("");
                            urlBean.setPath(getPath(urlStr, basePath));
                            urlBean.setQuery(getQuery(e1, requestBean));
                            if (withBasePath) {
                                urlBean.setRaw(urlBean.getHost() + "/" + basePath + (urlStr.startsWith("/") ? urlStr : "/" + urlStr));
                            } else {
                                urlBean.setRaw("/" + basePath + (urlStr.startsWith("/") ? urlStr : "/" + urlStr));
                            }
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
                                                if (heaerItem.contains("=")) {
                                                    headerBean = new PostmanModel.ItemBean.RequestBean.HeaderBean();
                                                    headerBean.setKey(heaerItem.split("=")[0]);
                                                    headerBean.setValue(heaerItem.split("=")[1]);
                                                    headerBean.setType("text");
                                                    headerBeans.add(headerBean);
                                                }
                                            }
                                        }
                                    }

                                }
                                requestBean.setHeader(removeDuplicate(headerBeans));
                            }
                            //body
                            PsiParameterList parameterList = e1.getParameterList();
                            for (PsiParameter pe : parameterList.getParameters()) {
                                PsiAnnotation[] pAt = pe.getAnnotations();
                                if (pAt != null && pAt.length != 0) {
                                    if (pe.getAnnotation("org.springframework.web.bind.annotation.RequestBody") != null || (pe.getAnnotation("org.springframework.web.bind.annotation.RequestPart") == null && !PluginConstants.simpleJavaType.contains(pe.getType().getCanonicalText()))) {
                                        PostmanModel.ItemBean.RequestBean.BodyBean bodyBean = new PostmanModel.ItemBean.RequestBean.BodyBean();
                                        bodyBean.setMode("raw");
                                        //:todo
                                        bodyBean.setRaw(getRaw(pe));

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
                                        bodyBean.setRaw(getRaw(pe));
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
                    model.setItem(itemBeans);
                    if (isRequest)
                        models.add(model);
                }
            }
        });
        return models;
    }

    public Optional<PsiAnnotation> findMappingAnn(PsiMethod e1, Class<PsiAnnotation> psiAnnotationClass) {
        Collection<PsiAnnotation> annotations = PsiTreeUtil.findChildrenOfType(e1, PsiAnnotation.class);
        return annotations.stream().filter(a -> a.getQualifiedName().contains("Mapping")).findFirst();
    }

    public void addFormHeader(List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans) {
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

    public List<PostmanModel.ItemBean.RequestBean.HeaderBean> removeDuplicate
            (List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans) {
        if (headerBeans != null && headerBeans.size() > 1) {
            headerBeans = headerBeans.stream().distinct().collect(Collectors.toList());
        }
        return headerBeans;
    }

    public List<String> getPath(String urlStr, String basePath) {
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

    public void addRestHeader(List<PostmanModel.ItemBean.RequestBean.HeaderBean> headerBeans) {
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

    public List<?> getQuery(PsiMethod e1, PostmanModel.ItemBean.RequestBean requestBean) {
        List<JSONObject> r = new ArrayList<>();
        PsiParameterList parametersList = e1.getParameterList();
        PsiParameter[] parameter = parametersList.getParameters();
        if (requestBean.getMethod().equalsIgnoreCase("REQUEST") && parameter.length == 0) {
            requestBean.setMethod("GET");
        }
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

    public String getMethod(PsiAnnotation mapAnn) {
        for (String s : SpringMappingConstants.mapList) {
            if (mapAnn.getQualifiedName().equalsIgnoreCase(s)) {
                return s.replace("org.springframework.web.bind.annotation.", "").replace("Mapping", "").toUpperCase();
            }
        }
        return "Unknown Method";
    }

    public static PsiElement findModifierInList(@NotNull PsiModifierList modifierList, String modifier) {
        PsiElement[] children = modifierList.getChildren();
        for (PsiElement child : children) {
            if (child.getText().contains(modifier)) return child;
        }
        return null;
    }

    public String getUrlFromAnnotation(PsiMethod method) {
        Collection<PsiAnnotation> mappingAn = PsiTreeUtil.findChildrenOfType(method, PsiAnnotation.class);
        Iterator<PsiAnnotation> mi = mappingAn.iterator();
        while (mi.hasNext()) {
            PsiAnnotation annotation = mi.next();
            if (annotation.getQualifiedName().contains("Mapping")) {
                Collection<String> mapUrls = PsiAnnotationUtil.getAnnotationValues(annotation, "value", String.class);
                if (mapUrls.size() > 0) {
                    return mapUrls.iterator().next();
                }
            }
        }
        return null;
    }

    public Map<String, Boolean> containsAnnotation(Collection<PsiAnnotation> annotations) {
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

    public String getRaw(PsiParameter pe) {
        PsiClass psiClass = JavaPsiFacade.getInstance(pe.getProject()).findClass(pe.getType().getCanonicalText(), GlobalSearchScope.projectScope(pe.getProject()));
        LinkedHashMap param = new LinkedHashMap();
        if (psiClass != null) {
            PsiField[] fields = psiClass.getAllFields();
            for (PsiField field : fields) {
                if (PluginConstants.simpleJavaType.contains(field.getType().getCanonicalText()))
                    param.put(field.getName(), PluginConstants.simpleJavaTypeValue.get(field.getType().getCanonicalText()));
                    //这个判断对多层集合嵌套的数据类型
                else {
                    if (field.getType().getCanonicalText().contains("<")) {
                        param.put(field.getName(), new ArrayList<>() {{
                            add(getFields(JavaPsiFacade.getInstance(pe.getProject()).findClass(field.getType().getCanonicalText().split("<")[1].split(">")[0], GlobalSearchScope.projectScope(pe.getProject()))));
                        }});
                    } else if (field.getType().getCanonicalText().contains("[]"))
                        param.put(field.getName(), new JSONArray());
                    else
                        param.put(field.getName(), new JSONObject());
                }
            }
        }
        return JSONObject.toJSONString(param, SerializerFeature.PrettyFormat);
    }

    public Object getFields(PsiClass context) {
        if (context == null)
            return null;
        PsiField[] fields = context.getAllFields();
        if (fields == null)
            return null;
        LinkedHashMap param = new LinkedHashMap();
        for (PsiField field : fields) {
            if (PluginConstants.simpleJavaType.contains(field.getType().getCanonicalText()))
                param.put(field.getName(), PluginConstants.simpleJavaTypeValue.get(field.getType().getCanonicalText()));
            else if (field.getType().getCanonicalText().contains("[]") || field.getType().getCanonicalText().contains("<")) {
                if (!PluginConstants.simpleJavaType.contains(((PsiClassImpl) field.getContext()).getQualifiedName())) {
                    param.put(field.getName(), getFields(JavaPsiFacade.getInstance(context.getProject()).findClass(field.getType().getCanonicalText().split("<")[1].split(">")[0], GlobalSearchScope.projectScope(context.getProject()))));
                } else {
                    param.put(field.getName(), new JSONArray());
                }
            } else
                param.put(field.getName(), new JSONObject());
        }
        return param;
    }

}
