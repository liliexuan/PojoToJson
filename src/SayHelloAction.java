import com.intellij.openapi.actionSystem.AnAction;

import com.intellij.openapi.actionSystem.AnActionEvent;

import com.intellij.openapi.application.Application;

import com.intellij.openapi.application.ApplicationManager;



public class SayHelloAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Application application=ApplicationManager.getApplication();
        PojoToJson pojoToJson=application.getComponent(PojoToJson.class);
        pojoToJson.sayHello();

    }
}
