package com.example.administrator.plugindemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.limpoxe.fairy.content.PluginDescriptor;
import com.limpoxe.fairy.manager.PluginManager;
import com.limpoxe.fairy.manager.PluginManagerHelper;
import com.limpoxe.fairy.manager.PluginStatusChangeListener;
import com.limpoxe.fairy.util.FileUtil;
import com.limpoxe.fairy.util.ResourceUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.button)
    Button button;
    @BindView(R.id.list)
    ListView mListView;
    private BaseAdapter listAdapter;
    private ArrayList<PluginDescriptor> plugins;
    private final BroadcastReceiver pluginInstallEvent = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String actionType = intent.getStringExtra("type");
            String pluginId = intent.getStringExtra("id");
            int code = intent.getIntExtra("code", -1);

            Toast.makeText(MainActivity.this,
                    (pluginId==null?"" : ("插件: " + pluginId + ", ")) + "action = " + actionType+","+code ,
                    Toast.LENGTH_SHORT).show();

            refreshListView();
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        plugins = new ArrayList<>();
        // 监听插件安装 安装新插件后刷新当前页面
        registerReceiver(pluginInstallEvent, new IntentFilter(PluginStatusChangeListener.ACTION_PLUGIN_CHANGED));
        initListAdapter();
        mListView.setAdapter(listAdapter);
        refreshListView();
    }

    private void refreshListView() {
        plugins.clear();
        plugins.addAll(PluginManager.getPlugins());
        listAdapter.notifyDataSetChanged();
    }
    @OnClick(R.id.button)
    public void onViewClicked() {
        new Thread(() -> {
            try {
                String[] files = getAssets().list("");
                for (String apk : files) {
                    if (apk.endsWith(".apk")) {
                        copyAndInstall(apk);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void copyAndInstall(String name) {
        try {
            InputStream assestInput = getAssets().open(name);
            File file = getExternalFilesDir(null);
            if (file == null) {
                Toast.makeText(MainActivity.this, "ExternalFilesDir not exist", Toast.LENGTH_LONG).show();
                return;
            }
            String dest = file.getAbsolutePath() + "/" + name;
            if (FileUtil.copyFile(assestInput, dest)) {
                PluginManager.installPlugin(dest);
            } else {
                assestInput = getAssets().open(name);
                file = getCacheDir();
                if (file == null) {
                    Toast.makeText(MainActivity.this, "CacheDir not exist", Toast.LENGTH_LONG).show();
                    return;
                }
                dest = file.getAbsolutePath() + "/" + name;
                if (FileUtil.copyFile(assestInput, dest)) {
                    PluginManager.installPlugin(dest);
                } else {
                    Toast.makeText(MainActivity.this, "抽取assets中的Apk失败" + dest, Toast.LENGTH_LONG).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "安装失败", Toast.LENGTH_LONG).show();
        }
    }

    private void testStartActivity(PluginDescriptor pluginDescriptor) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pluginDescriptor.getPackageName());
        if (launchIntent != null) {
            //打开插件的Launcher界面
            if (!pluginDescriptor.isStandalone()) {
                //测试向非独立插件传宿主中定义的VO对象
                launchIntent.putExtra("paramVO", "宿主传过来的测试VO");
            }
            startActivity(launchIntent);
        } else {
            Toast.makeText(MainActivity.this, "插件" + pluginDescriptor.getPackageName() + "没有配置Launcher", Toast.LENGTH_SHORT).show();
            //没有找到Launcher，打开插件详情
        }
    }

    private void initListAdapter(){
        listAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return plugins.size();
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = getLayoutInflater().inflate(R.layout.list_item_plugin, null);
                ImageView icon = (ImageView) view.findViewById(R.id.icon);
                TextView appName = (TextView) view.findViewById(R.id.appName);
                TextView packageName = (TextView) view.findViewById(R.id.packageName);
                TextView isStandard = (TextView) view.findViewById(R.id.is_standard);
                TextView pluginVersion = (TextView) view.findViewById(R.id.plugin_version);
                TextView uninstall = (TextView) view.findViewById(R.id.uninstall);

                final PluginDescriptor pluginDescriptor = plugins.get(position);
                appName.setText(ResourceUtil.getLabel(pluginDescriptor));
                packageName.setText(pluginDescriptor.getPackageName());
                isStandard.setText(pluginDescriptor.isStandalone()?"独立插件":"非独立插件");
                pluginVersion.setText(pluginDescriptor.getVersion());
                icon.setImageDrawable(ResourceUtil.getIcon(pluginDescriptor));

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //MobclickAgent.onEvent(MainActivity.this, "test_1");
                        testStartActivity(pluginDescriptor);
                    }
                });

                uninstall.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //MobclickAgent.onEvent(MainActivity.this, "test_2");
                        PluginManager.remove(pluginDescriptor.getPackageName());
                        refreshListView();
                    }
                });

                return view;
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pluginInstallEvent);
    }
}
