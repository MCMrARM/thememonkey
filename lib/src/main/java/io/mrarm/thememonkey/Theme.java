package io.mrarm.thememonkey;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.os.Build;

public class Theme {

    private static AssetManager createAssetManager(Context context, String themeFile) {
        AssetManager assetManager = AssetManagerReflectionHelper.create();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        AssetManagerReflectionHelper.addAssetPath(assetManager, applicationInfo.sourceDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                applicationInfo.splitSourceDirs != null) {
            for (String path : applicationInfo.splitSourceDirs)
                AssetManagerReflectionHelper.addAssetPath(assetManager, path);
        }
        AssetManagerReflectionHelper.addAssetPath(assetManager, themeFile);
        // required on pre-KitKat according to Google in their MonkeyPatcher code
        AssetManagerReflectionHelper.ensureStringBlocks(assetManager);
        return assetManager;
    }


    private final AssetManager assetManager;

    public Theme(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Theme(Context context, String themeFile) {
        assetManager = createAssetManager(context, themeFile);
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public void applyToActivity(Activity activity) {
        MonkeyPatcher.setActivityAssetManager(activity, assetManager);
        MonkeyPatcher.updateResourcesConfiguration(activity.getResources());
    }

}
