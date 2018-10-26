import com.google.gson.Gson;
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
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class BuildJson extends AnAction {
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
            KV kv = getFields(selectedClass);
            String json = kv.toPrettyJson();
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


    public static KV getFields(PsiClass psiClass) {
        KV kv = KV.create();

        if (psiClass != null) {
            for (PsiField field : psiClass.getAllFields()) {
                PsiType type = field.getType();
                String name = field.getName();
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
                       jsonObject.addProperty("description","");
                        kv.set(name, jsonObject);
                    } else if (type instanceof PsiArrayType) {
                        //array type
                        PsiType deepType = type.getDeepComponentType();
                        ArrayList list = new ArrayList<>();
                        String deepTypeName = deepType.getPresentableText();
                        if (deepType instanceof PsiPrimitiveType) {
                            list.add(PsiTypesUtil.getDefaultValueOfType(deepType));
                        } else if (isNormalType(deepTypeName)) {
                            JsonObject jsonObject=new JsonObject();
                            jsonObject.addProperty("type",deepTypeName);
                            jsonObject.addProperty("description","");
                            list.add(jsonObject);
                        } else {
                            list.add(getFields(PsiUtil.resolveClassInType(deepType)));
                        }
                        kv.set(name, list);
                    } else if (fieldTypeName.startsWith("List")) {
                        //list type
                        PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                        PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                        ArrayList list = new ArrayList<>();
                        String classTypeName = iterableClass.getName();
                        if (isNormalType(classTypeName)) {
                            JsonObject jsonObject=new JsonObject();
                            jsonObject.addProperty("type",classTypeName);
                            jsonObject.addProperty("description","");
                            list.add(jsonObject);
                        } else {
                            list.add(getFields(iterableClass));
                        }
                        kv.set(name, list);
                    } else if(fieldTypeName.startsWith("HashMap") || fieldTypeName.startsWith("Map")){
                        //HashMap or Map

                    }else {
                        //class type
                        kv.set(name, getFields(PsiUtil.resolveClassInType(type)));
                    }
                }
            }
        }

        return kv;
    }
}
