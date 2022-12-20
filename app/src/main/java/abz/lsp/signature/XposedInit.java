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
    public static final String TAG = "SignatureFix: ";

    private String appPkgName;
    private Object base;
    private byte[][] sign;

    public static final String className = "bin.mt.apksignaturekillerplus.HookApplication";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam.isFirstApplication) {
            log("* package: " + loadPackageParam.packageName);
            log("* application: " + loadPackageParam.appInfo.packageName);
            try {
                Class<?> clazz = loadPackageParam.classLoader.loadClass(className);
                log("* loading class: " + className);
                log("\t\thook field init..");
                XposedBridge.hookAllMethods(clazz, "hook", new OnHookListener());
                log("\t\treplace invoke..");
                XposedBridge.hookAllMethods(clazz, "invoke", new OnInvokeListener());
                log("\t\tsucceed!");
            } catch (ClassNotFoundException e) {
                log("? class not found: " + className);
            }
            log("* finish");
        }
    }

    public void log(String msg) {
        XposedBridge.log(TAG + msg);
    }

    /* log
     public void log(String format, Object ... args) {
         log(String.format(Locale.getDefault(), format, args));
     } */

    class OnHookListener extends XC_MethodHook {
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

    Signature[] signatures;
    public void replaceSignature(PackageInfo packageInfo) {
        if (signatures == null) {
            signatures = new Signature[sign.length];
            for (int i = 0; i < signatures.length; i++) signatures[i] = new Signature(sign[i]);
        }
        packageInfo.signatures = signatures;
    }

    class PackageInfoHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            if (appPkgName.equals(param.args[0])) replaceSignature((PackageInfo) param.getResult());
            super.afterHookedMethod(param);
        }
    }

    class OnInvokeListener extends XC_MethodReplacement {
        @Override
        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//            Object obj = methodHookParam.args[0];
            Method method = (Method) methodHookParam.args[1];
            Object[] objArr = (Object[]) methodHookParam.args[2];
            return method.invoke(base, objArr);
        }
    }
}
