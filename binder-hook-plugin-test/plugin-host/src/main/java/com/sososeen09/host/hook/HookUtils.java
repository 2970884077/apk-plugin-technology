package com.sososeen09.host.hook;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.sososeen09.host.delegate.ActivityCallback;
import com.sososeen09.host.delegate.ActivityStartMethodHandler;
import com.sososeen09.host.delegate.InterceptPackageManagerHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Created by yunlong.su on 2018/4/3.
 */

public class HookUtils {

    private Context context;

    public void initHook(Context context) {
        this.context = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hookActivityManagerApi26();

        } else {
            hookActivityManagerApi25();
        }

        HookPackageManager();
        hookActivityThreadHandler();
    }

    private void HookPackageManager() {
        //需要hook ActivityThread
        try {
            //获取ActivityThread的成员变量 sCurrentActivityThread
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            Object iPackageManagerObj = sPackageManagerField.get(null);


            Class<?> iPackageManagerClass = Class.forName("android.content.pm.IPackageManager");
            InterceptPackageManagerHandler interceptInvocationHandler = new InterceptPackageManagerHandler(iPackageManagerObj);
            Object iPackageManagerObjProxy = Proxy.newProxyInstance(context.getClassLoader(), new Class[]{iPackageManagerClass}, interceptInvocationHandler);

            sPackageManagerField.set(null, iPackageManagerObjProxy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void hookActivityManagerApi26() {
        try {
            // 反射获取ActivityManager的静态成员变量IActivityManagerSingleton,适配8.0
            Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManager");
            Field iActivityManagerSingletonField = activityManagerNativeClass.getDeclaredField("IActivityManagerSingleton");
            iActivityManagerSingletonField.setAccessible(true);
            Object iActivityManagerSingleton = iActivityManagerSingletonField.get(null);
            realHookActivityManager(iActivityManagerSingleton);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hookActivityManagerApi25() {
        try {
            // 反射获取ActivityManagerNative的静态成员变量gDefault, 注意，在8.0的时候这个已经更改了
            Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
            Field gDefaultField = activityManagerNativeClass.getDeclaredField("gDefault");
            gDefaultField.setAccessible(true);
            Object gDefaultObj = gDefaultField.get(null);
            realHookActivityManager(gDefaultObj);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void realHookActivityManager(Object iActivityManagerSingleton) throws IllegalAccessException, ClassNotFoundException, NoSuchFieldException {
        // ActivityManagerNative.getDefault()方法在ActivityThread调用attach方法初始化的时候已经调用过，
        // 所以我们在这里拿到的instanceObj对象不为空，如果为空的话就没办法使用
        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);

        Object instanceObj = mInstanceField.get(iActivityManagerSingleton);

        // 需要动态代理IActivityManager，把Singleton的成员变量mInstance的值设置为我们的这个动态代理对象
        // 但是有一点，我们不可能完全重写一个IActivityManager的实现类
        // 所以还是需要用到原始的IActivityManager对象，只是在调用某些方法的时候做一些手脚
        Class<?> iActivityManagerClass = Class.forName("android.app.IActivityManager");
        ActivityStartMethodHandler activityStartMethodHandler = new ActivityStartMethodHandler(context, instanceObj);
        Object iActivityManagerObj = Proxy.newProxyInstance(context.getClassLoader(), new Class[]{iActivityManagerClass}, activityStartMethodHandler);
        mInstanceField.set(iActivityManagerSingleton, iActivityManagerObj);
    }

    private void hookActivityThreadHandler() {
        //需要hook ActivityThread
        try {
            //获取ActivityThread的成员变量 sCurrentActivityThread
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThread = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThread.setAccessible(true);
            Object activityThreadObj = sCurrentActivityThread.get(null);

            //获取ActivityThread的成员变量 mH
            Field mHField = activityThreadClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            Handler mHObj = (Handler) mHField.get(activityThreadObj);

            Field mCallbackField = Handler.class.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);
            mCallbackField.set(mHObj, new ActivityCallback(context, mHObj));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
