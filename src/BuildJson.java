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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @description: 基本生成json
 * @author: chengsheng@qbb6.com
 * @date: 2018/10/27
 */ 
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
        normalTypes.put("String", "String");
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        normalTypes.put("Date", simpleDateFormat.format(new Date()));
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
            KV kv = getFields(selectedClass,project);
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


    public static KV getFields(PsiClass psiClass,Project project) {
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
                        kv.set(name, normalTypes.get(fieldTypeName));
                    } else if (type instanceof PsiArrayType) {
                        //array type
                        PsiType deepType = type.getDeepComponentType();
                        ArrayList list = new ArrayList<>();
                        String deepTypeName = deepType.getPresentableText();
                        if (deepType instanceof PsiPrimitiveType) {
                            list.add(PsiTypesUtil.getDefaultValueOfType(deepType));
                        } else if (isNormalType(deepTypeName)) {
                            list.add(normalTypes.get(deepTypeName));
                        } else {
                            list.add(getFields(PsiUtil.resolveClassInType(deepType),project));
                        }
                        kv.set(name, list);
                    } else if (fieldTypeName.startsWith("List")) {
                        //list type
                        PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                        PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                        ArrayList list = new ArrayList<>();
                        String classTypeName = iterableClass.getName();
                        if (isNormalType(classTypeName)) {
                            list.add(normalTypes.get(classTypeName));
                        } else {
                            list.add(getFields(iterableClass,project));
                        }
                        kv.set(name, list);
                    } else if(fieldTypeName.startsWith("HashMap") || fieldTypeName.startsWith("Map")){
                        //HashMap or Map
                     //   PsiType mapType = PsiUtil.extractIterableTypeParameter(type, false);
                        CompletableFuture.runAsync(()-> {
                            try {
                                TimeUnit.MILLISECONDS.sleep(700);
                                Notification warning = notificationGroup.createNotification("Map Type Can not Change,So pass", NotificationType.WARNING);
                                Notifications.Bus.notify(warning, project);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
                    }else if (fieldTypeName.startsWith("Set") || fieldTypeName.startsWith("HashSet")){
                        //set hashset type
                        PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                        PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                        Set set = new HashSet();
                        String classTypeName = iterableClass.getName();
                        if (isNormalType(classTypeName)) {
                            set.add(normalTypes.get(classTypeName));
                        } else {
                            set.add(getFields(iterableClass,project));
                        }
                        kv.set(name, set);
                    }else {
                        //class type
                        kv.set(name, getFields(PsiUtil.resolveClassInType(type),project));
                    }
                }
            }
        }

        return kv;
    }



}
