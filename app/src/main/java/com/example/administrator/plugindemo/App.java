package com.example.administrator.plugindemo;

import android.app.Application;
import android.content.Context;

import com.limpoxe.fairy.core.FairyGlobal;
import com.limpoxe.fairy.core.PluginLoader;

public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        //框架日志开关, 默认false
        FairyGlobal.setLogEnable(true);

        //首次加载插件会创建插件对象，比较耗时，通过弹出loading页来过渡。
        //这个方法是设置首次加载插件时, 定制loading页面的UI, 不传即默认没有loading页
        //在宿主中创建任意一个layout传进去即可
        //注意：首次唤起插件组件时，如果是通过startActivityForResult唤起的，如果配置了loading页，
        //则实际是先打开了loading页，再转到目标页面，此时会忽略ForResult的结果。这种情况下应该禁用loading页配置
        FairyGlobal.setLoadingResId(R.layout.loading);

        //是否支持插件中使用本地html, 默认false
        FairyGlobal.setLocalHtmlenable(true);

        //初始化框架
        PluginLoader.initLoader(this);
    }
}
