package abz.lsp.signature;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookLoadPackage {
    private static String appPkgName;
    private static Object base;
    private static byte[][] sign;

    private static boolean state = true;
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        String className = loadPackageParam.appInfo.className;
        if (state) {
            try {
                Class<?> clazz = loadPackageParam.classLoader.loadClass(className);
                XposedBridge.hookAllMethods(clazz, "hook", new OnHookListener());
                XposedBridge.hookAllMethods(clazz, "invoke", new OnInvokeListener());
                XposedBridge.log("* done!"); state = false;
            } catch (ClassNotFoundException ignored) {}
        }
    }

    private static final class OnHookListener extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            // Application
            Object thisObject = param.thisObject;
            base = XposedHelpers.getObjectField(thisObject, "base");
            sign = (byte[][]) XposedHelpers.getObjectField(thisObject, "sign");
            appPkgName = (String) XposedHelpers.getObjectField(thisObject, "appPkgName");
            Context context = (Context) param.args[0];
            XposedHelpers.findAndHookMethod(context.getPackageManager().getClass(), "getPackageInfo", String.class, int.class, new PackageInfoHook());
        }
    }

    private static Signature[] signatures;
    private static void replaceSignature(PackageInfo packageInfo) {
        if (signatures == null) {
            signatures = new Signature[sign.length];
            for (int i = 0; i < signatures.length; i++) signatures[i] = new Signature(sign[i]);
        }
        packageInfo.signatures = signatures;
    }

    private static final class PackageInfoHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            if (appPkgName.equals(param.args[0])) replaceSignature((PackageInfo) param.getResult());
            super.afterHookedMethod(param);
        }
    }

    private static final class OnInvokeListener extends XC_MethodReplacement {
        @Override
        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
            Method method = (Method) methodHookParam.args[1];
            Object[] objArr = (Object[]) methodHookParam.args[2];
            return method.invoke(base, objArr);
        }
    }
}
