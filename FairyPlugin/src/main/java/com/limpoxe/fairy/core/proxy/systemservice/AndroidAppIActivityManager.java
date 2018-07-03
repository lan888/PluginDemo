package com.limpoxe.fairy.core.proxy.systemservice;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.IBinder;

import com.limpoxe.fairy.content.PluginDescriptor;
import com.limpoxe.fairy.content.PluginProviderInfo;
import com.limpoxe.fairy.core.FairyGlobal;
import com.limpoxe.fairy.core.PluginLauncher;
import com.limpoxe.fairy.core.PluginLoader;
import com.limpoxe.fairy.core.android.HackComponentName;
import com.limpoxe.fairy.core.bridge.PluginShadowService;
import com.limpoxe.fairy.core.android.HackActivityManager;
import com.limpoxe.fairy.core.android.HackActivityManagerNative;
import com.limpoxe.fairy.core.android.HackActivityThread;
import com.limpoxe.fairy.core.android.HackContentProviderHolder;
import com.limpoxe.fairy.core.android.HackSingleton;
import com.limpoxe.fairy.core.bridge.ProviderClientProxy;
import com.limpoxe.fairy.core.proxy.MethodDelegate;
import com.limpoxe.fairy.core.proxy.MethodProxy;
import com.limpoxe.fairy.core.proxy.ProxyUtil;
import com.limpoxe.fairy.manager.PluginManagerHelper;
import com.limpoxe.fairy.manager.PluginManagerProvider;
import com.limpoxe.fairy.manager.mapping.PluginStubBinding;
import com.limpoxe.fairy.manager.mapping.StubMappingProcessor;
import com.limpoxe.fairy.util.LogUtil;
import com.limpoxe.fairy.util.PendingIntentHelper;
import com.limpoxe.fairy.util.ProcessUtil;
import com.limpoxe.fairy.util.ResourceUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by cailiming on 16/1/15.
 */
public class AndroidAppIActivityManager extends MethodProxy {

    static {
        sMethods.put("getRunningAppProcesses", new getRunningAppProcesses());
        sMethods.put("getIntentSender", new getIntentSender());
        sMethods.put("overridePendingTransition", new overridePendingTransition());
        sMethods.put("serviceDoneExecuting", new serviceDoneExecuting());
        sMethods.put("getContentProvider", new getContentProvider());
        sMethods.put("getTasks", new getTasks());
        sMethods.put("getAppTasks", new getAppTasks());
        sMethods.put("getServices", new getServices());
        //暂不需要
        //sMethods.put("broadcastIntent", new broadcastIntent());
        //sMethods.put("startService", new startService());
        //sMethods.put("stopService", new stopService());
        //sMethods.put("bindService", new bindService());
        //sMethods.put("unbindService", new unbindService());
        //sMethods.put("clearApplicationUserData", new clearApplicationUserData());
    }

    public static void installProxy() {
        LogUtil.d("安装ActivityManagerProxy");
        Object androidAppActivityManagerProxy = HackActivityManagerNative.getDefault();
        Object androidAppIActivityManagerStubProxyProxy = ProxyUtil.createProxy(androidAppActivityManagerProxy, new AndroidAppIActivityManager());
        //O Preview版本暂时不能通过SDK_INT来区分 2017-5-18
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            Object singleton = HackActivityManagerNative.getGDefault();
            //如果是IActivityManager
            if (singleton.getClass().isAssignableFrom(androidAppIActivityManagerStubProxyProxy.getClass())) {
                HackActivityManagerNative.setGDefault(androidAppIActivityManagerStubProxyProxy);
            } else {//否则是包装过的单例
                new HackSingleton(singleton).setInstance(androidAppIActivityManagerStubProxyProxy);
            }
        } else {
            //Android O 没有gDefault这个成员了, 变量被移到了ActivityManager这个类中
            Object singleton = HackActivityManager.getIActivityManagerSingleton();
            if (singleton != null) {
                new HackSingleton(singleton).setInstance(androidAppIActivityManagerStubProxyProxy);
            } else {
                LogUtil.e("WTF!!");
            }
        }
        LogUtil.d("安装完成");
    }

    //public List<RunningAppProcessInfo> getRunningAppProcesses()
    public static class getRunningAppProcesses extends MethodDelegate {

        @Override
        public Object afterInvoke(Object target, Method method, Object[] args, Object beforeInvoke, Object invokeResult) {
            LogUtil.v("afterInvoke", method.getName());
            //由于插件运行在插件进程中，这里需要欺骗插件，让插件的中判断进程的逻辑以为当前是在主进程中运行
            //但是这会导致插件框架也无法判断当前的进程了，因此框架中判断插件进程的方法一定要在安装ActivityManager代理之前执行并记住状态
            //同时要保证主进程能正确判断进程。
            //这里不会导致无限递归，因为ProcessUtil.isPluginProcess方法内部有缓存，再安装ActivityManager代理之前已经执行并缓存了
            if (ProcessUtil.isPluginProcess() && FairyGlobal.isFakePluginProcessName()) {
                List<ActivityManager.RunningAppProcessInfo> result = (List<ActivityManager.RunningAppProcessInfo>)invokeResult;
                for (ActivityManager.RunningAppProcessInfo appProcess : result) {
                    if (appProcess != null && appProcess.pid == android.os.Process.myPid()) {
                        appProcess.processName = FairyGlobal.getHostApplication().getPackageName();
                        break;
                    }
                }
            }

            return super.afterInvoke(target, method, args, beforeInvoke, invokeResult);
        }
    }

    public static class getIntentSender extends MethodDelegate {

        public static final int INTENT_SENDER_BROADCAST = 1;
        public static final int INTENT_SENDER_ACTIVITY = 2;
        public static final int INTENT_SENDER_ACTIVITY_RESULT = 3;
        public static final int INTENT_SENDER_SERVICE = 4;

        @Override
        public Object beforeInvoke(Object target, Method method, Object[] args) {
            LogUtil.v("beforeInvoke", method.getName(), args[1]);
            int type = (int)args[0];
            args[1] = FairyGlobal.getHostApplication().getPackageName();
            if (type != INTENT_SENDER_ACTIVITY_RESULT) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && args[i].getClass().isAssignableFrom(Intent[].class)) {
                        Intent[] intents = (Intent[])args[i];
                        if (type == INTENT_SENDER_BROADCAST) {
                            type = PluginDescriptor.BROADCAST;
                        } else if (type == INTENT_SENDER_ACTIVITY) {
                            type = PluginDescriptor.ACTIVITY;
                        } else if (type == INTENT_SENDER_SERVICE) {
                            type = PluginDescriptor.SERVICE;
                        }
                        for(int j = 0; j < intents.length; j++) {
                            intents[j] =  PendingIntentHelper.resolvePendingIntent(intents[j], type);
                        }
                        break;
                    }
                }
            }

            return super.beforeInvoke(target, method, args);
        }
    }

    public static class overridePendingTransition extends MethodDelegate {
        public Object beforeInvoke(Object target, Method method, Object[] args) {
            if (ProcessUtil.isPluginProcess()) {
                if (!ResourceUtil.isMainResId((Integer) args[2])) {
                    args[2] = 0;
                }
                if (!ResourceUtil.isMainResId((Integer) args[3])) {
                    args[3] = 0;
                }
            }
            return null;
        }
    }

    public static class serviceDoneExecuting extends MethodDelegate {
        public Object beforeInvoke(Object target, Method method, Object[] args) {
            if (ProcessUtil.isPluginProcess()) {
                if (((Integer)args[1]).equals(HackActivityThread.getSERVICE_DONE_EXECUTING_ANON())) {
                    for (Object obj: args) {
                        if (obj instanceof IBinder) {
                            Map<IBinder, Service> services = HackActivityThread.get().getServices();
                            Service service = services.get(obj);
                            if (service instanceof PluginShadowService) {
                                if (((PluginShadowService) service).realService != null) {
                                    services.put((IBinder) obj, ((PluginShadowService) service).realService);
                                } else {
                                    throw new IllegalStateException("unable to create service");
                                }
                            }
                            break;
                        }
                    }
                }
            }
            return null;
        }
    }

    public static class getContentProvider extends MethodDelegate {

        //ApplicationThread, auth, userId, stable
        @Override
        public Object afterInvoke(Object target, Method method, Object[] args, Object beforeInvoke, Object invokeResult) {
            //非插件进程
            if (!ProcessUtil.isPluginProcess()) {
                if (invokeResult == null) {
                    String auth = (String)args[1];
                    LogUtil.d("auth", auth);
                    if (PluginManagerProvider.buildUri().getAuthority().equals(auth)) {
                        return invokeResult;
                    }
                    ArrayList<PluginDescriptor> list = PluginManagerHelper.getPlugins();
                    for(PluginDescriptor pluginDescriptor : list) {
                        HashMap<String, PluginProviderInfo> map = pluginDescriptor.getProviderInfos();
                        if (map != null) {
                            Iterator<PluginProviderInfo> iterator = map.values().iterator();
                            while(iterator.hasNext()) {
                                PluginProviderInfo pluginProviderInfo = iterator.next();
                                //在插件中找到了匹配的contentprovider
                                if (auth != null && auth.equals(pluginProviderInfo.getAuthority())) {
                                    //先检查插件是否已经初始化
                                    boolean isrunning = PluginManagerHelper.isRunning(pluginDescriptor.getPackageName());
                                    if (!isrunning) {
                                        isrunning = PluginManagerHelper.wakeup(pluginDescriptor.getPackageName());
                                    }
                                    if (!isrunning) {
                                        return invokeResult;
                                    }

                                    ProviderInfo providerInfo = new ProviderInfo();
                                    providerInfo.applicationInfo = FairyGlobal.getHostApplication().getApplicationInfo();
                                    providerInfo.authority = auth;
                                    providerInfo.name = ProviderClientProxy.class.getName();
                                    providerInfo.packageName = FairyGlobal.getHostApplication().getPackageName();
                                    Object holder = HackContentProviderHolder.newInstance(providerInfo);

                                    if (holder != null) {
                                        return holder;
                                    } else {
                                        return invokeResult;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return invokeResult;
        }
    }

    public static class getTasks extends MethodDelegate {
        public Object afterInvoke(Object target, Method method, Object[] args, Object beforeInvoke, Object invokeResult) {
            if (ProcessUtil.isPluginProcess()) {
                List<ActivityManager.RunningTaskInfo> list = (List<ActivityManager.RunningTaskInfo>)invokeResult;
                if (list != null && list.size() > 0) {
                    for(ActivityManager.RunningTaskInfo taskInfo : list) {
                        fixStubName(taskInfo.baseActivity);
                        fixStubName(taskInfo.topActivity);
                    }
                }
            }
            return invokeResult;
        }

        private void fixStubName(ComponentName componentName) {
            if(componentName == null) {
                return;
            }
            if (PluginStubBinding.isStub(componentName.getClassName())) {
                //通过stub查询其绑定的插件组件名称，如果是Activity，只支持非Standard模式的
                //因为standard模式是1对多的关系，1个stub对应多个插件Activity，通过stub查绑定关系是是查不出来的，这种情况需要通过lifecycle来记录
                //其他模式的可以通过这种方法查出来
                String realClassName = PluginStubBinding.getBindedPluginClassName(componentName.getClassName(), StubMappingProcessor.TYPE_ACTIVITY);
                if (realClassName != null) {
                    new HackComponentName(componentName).setClassName(realClassName);
                }
            }
        }
    }

    public static class getAppTasks extends MethodDelegate {
        public Object afterInvoke(Object target, Method method, Object[] args, Object beforeInvoke, Object invokeResult) {
            if (ProcessUtil.isPluginProcess()) {
                LogUtil.d("getAppTasks", invokeResult);
            }
            return invokeResult;
        }
    }

    public static class getServices extends MethodDelegate {
        public Object afterInvoke(Object target, Method method, Object[] args, Object beforeInvoke, Object invokeResult) {
            if (ProcessUtil.isPluginProcess()) {
                LogUtil.d("getServices", invokeResult);
            }
            return invokeResult;
        }
    }

}
