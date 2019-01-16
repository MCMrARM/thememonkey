package io.mrarm.thememonkey;

import android.content.res.AssetManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@SuppressWarnings("JavaReflectionMemberAccess")
class AssetManagerReflectionHelper {

    private static Constructor<AssetManager> assetManagerConstructor;
    private static Method mAddAssetPath;
    private static Method mEnsureStringBlocks;

    static {
        Class<AssetManager> clz = AssetManager.class;
        try {
            assetManagerConstructor = clz.getConstructor();
            mAddAssetPath = clz.getDeclaredMethod("addAssetPath", String.class);
            mAddAssetPath.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        try {
            mEnsureStringBlocks = clz.getDeclaredMethod("ensureStringBlocks");
            mEnsureStringBlocks.setAccessible(true);
        } catch (NoSuchMethodException e) {
        }
    }

    static AssetManager create() {
        try {
            return assetManagerConstructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int addAssetPath(AssetManager manager, String path) {
        try {
            return (Integer) mAddAssetPath.invoke(manager, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void ensureStringBlocks(AssetManager manager) {
        if (mEnsureStringBlocks == null)
            return;
        try {
            mEnsureStringBlocks.invoke(manager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



}
