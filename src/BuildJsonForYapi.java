import com.google.gson.JsonObject;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.yourkit.util.Strings;
import org.codehaus.jettison.json.JSONException;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.*;

/**
 * @description: 为了yapi 创建的
 * @author: chengsheng@qbb6.com
 * @date: 2018/10/27
 */ 
public class BuildJsonForYapi extends AnAction {
    private static NotificationGroup notificationGroup;

    @NonNls
    private static final Map<String, Object> normalTypes = new HashMap<>();

    static {
        notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);
        normalTypes.put("Boolean", false);
        normalTypes.put("Byte", 0);
        normalTypes.put("Short", Short.valueOf((short) 0));
        normalTypes.put("Integer", 0);
        normalTypes.put("Long", 0L);
        normalTypes.put("Float", 0.0F);
        normalTypes.put("Double", 0.0D);
        normalTypes.put("String", "");
        normalTypes.put("Date", null);
    }

    private static boolean isNormalType(String typeName) {
        return normalTypes.containsKey(typeName);
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = (Editor) e.getDataContext().getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = (PsiFile) e.getDataContext().getData(CommonDataKeys.PSI_FILE);
        Project project = editor.getProject();
        PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass selectedClass = (PsiClass) PsiTreeUtil.getContextOfType(referenceAt, new Class[]{PsiClass.class});
        try {
            KV result=new KV();
            KV kv = getFields(selectedClass);
            result.set("type","object");
            result.set("title",selectedClass.getName());
            result.set("description",selectedClass.getName());
            result.set("properties",kv);
            String json = result.toPrettyJson();
            StringSelection selection = new StringSelection(json);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            String message = "Convert " + selectedClass.getName() + " to JSON success, copied to clipboard.";
            Notification success = notificationGroup.createNotification(message, NotificationType.INFORMATION);
            Notifications.Bus.notify(success, project);
        } catch (Exception ex) {
            Notification error = notificationGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
        }
    }


    public static KV getFields(PsiClass psiClass) throws JSONException {
        KV kv = KV.create();

        if (psiClass != null) {
            for (PsiField field : psiClass.getAllFields()) {
                PsiType type = field.getType();
                String name = field.getName();
                String remark ="";
                if(field.getDocComment()!=null) {
                    remark=field.getDocComment().getText().replace("*", "").replace("/", "").replace(" ", "").replace("\n", ",");
                    remark=trimFirstAndLastChar(remark,',');
                }
                // 如果是基本类型
                if (type instanceof PsiPrimitiveType) {
                    kv.set(name, PsiTypesUtil.getDefaultValueOfType(type));
                } else {
                    //reference Type
                    String fieldTypeName = type.getPresentableText();
                    //normal Type
                    if (isNormalType(fieldTypeName)) {
                        JsonObject jsonObject=new JsonObject();
                        jsonObject.addProperty("type",fieldTypeName);
                        if(!Strings.isNullOrEmpty(remark)) {
                            jsonObject.addProperty("description", remark);
                        }
                        kv.set(name, jsonObject);
                    } else if (type instanceof PsiArrayType) {
                        //array type
                        PsiType deepType = type.getDeepComponentType();
                        KV kvlist = new KV();
                        String deepTypeName = deepType.getPresentableText();
                        if (deepType instanceof PsiPrimitiveType) {
                            kvlist.set("type",PsiTypesUtil.getDefaultValueOfType(deepType));
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set("description", remark);
                            }
                        } else if (isNormalType(deepTypeName)) {
                            kvlist.set("type",deepTypeName);
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set("description", remark);
                            }
                        } else {
                            kvlist.set(KV.by("type","object"));
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set(KV.by("description",remark));
                            }
                            kvlist.set("properties",getFields(PsiUtil.resolveClassInType(deepType)));
                        }
                        KV kv1=new KV();
                        kv1.set(KV.by("type","array"));
                        if(!Strings.isNullOrEmpty(remark)) {
                            kv1.set(KV.by("description",remark));
                        }
                        kv1.set("items",kvlist);
                        kv.set(name, kv1);
                    } else if (fieldTypeName.startsWith("List")||fieldTypeName.startsWith("Set") || fieldTypeName.startsWith("HashSet")) {
                        //list type
                        PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                        PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                        KV kvlist = new KV();
                        String classTypeName = iterableClass.getName();
                        if (isNormalType(classTypeName)) {
                            kvlist.set("type",classTypeName);
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set("description", remark);
                            }
                        } else {
                            kvlist.set(KV.by("type","object"));
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set(KV.by("description",remark));
                            }
                            kvlist.set("properties",getFields(iterableClass));
                        }
                        KV kv1=new KV();
                        kv1.set(KV.by("type","array"));
                        if(!Strings.isNullOrEmpty(remark)) {
                            kv1.set(KV.by("description",remark));
                        }
                        kv1.set("items",kvlist);
                        kv.set(name, kv1);
                    } else if(fieldTypeName.startsWith("HashMap") || fieldTypeName.startsWith("Map")){
                        //HashMap or Map

                    }else {
                        //class type
                        KV kv1=new KV();
                        kv1.set(KV.by("type","object"));
                        if(!Strings.isNullOrEmpty(remark)) {
                            kv1.set(KV.by("description",remark));
                        }
                        kv1.set(KV.by("properties",getFields(PsiUtil.resolveClassInType(type))));
                        kv.set(name,kv1);
                    }
                }
            }
        }

        return kv;
    }


    /**
     * 去除字符串首尾出现的某个字符.
     * @param source 源字符串.
     * @param element 需要去除的字符.
     * @return String.
     */
    public static String trimFirstAndLastChar(String source,char element) {
        boolean beginIndexFlag = true;
        boolean endIndexFlag = true;
        do {
            int beginIndex = source.indexOf(element) == 0 ? 1 : 0;
            int endIndex = source.lastIndexOf(element) + 1 == source.length() ? source.lastIndexOf(element) : source.length();
            source = source.substring(beginIndex, endIndex);
            beginIndexFlag = (source.indexOf(element) == 0);
            endIndexFlag = (source.lastIndexOf(element) + 1 == source.length());
        } while (beginIndexFlag || endIndexFlag);
        return source;
    }
}
