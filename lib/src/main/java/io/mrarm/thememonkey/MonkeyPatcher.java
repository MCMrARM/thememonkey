/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mrarm.thememonkey;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressWarnings("JavaReflectionMemberAccess")
@SuppressLint("PrivateApi")
public class MonkeyPatcher {

    static void setActivityAssetManager(Activity activity, AssetManager newAssetManager) {
        try {
            Resources resources = activity.getResources();
            try {
                Field mAssets = Resources.class.getDeclaredField("mAssets");
                mAssets.setAccessible(true);
                mAssets.set(resources, newAssetManager);
            } catch (Throwable ignore) {
                Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                mResourcesImpl.setAccessible(true);
                Object resourceImpl = mResourcesImpl.get(resources);
                Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                implAssets.setAccessible(true);
                implAssets.set(resourceImpl, newAssetManager);
            }
            Resources.Theme theme = activity.getTheme();
            try {
                try {
                    Field ma = Resources.Theme.class.getDeclaredField("mAssets");
                    ma.setAccessible(true);
                    ma.set(theme, newAssetManager);
                } catch (NoSuchFieldException ignore) {
                    Field themeField = Resources.Theme.class.getDeclaredField("mThemeImpl");
                    themeField.setAccessible(true);
                    Object impl = themeField.get(theme);
                    Field ma = impl.getClass().getDeclaredField("mAssets");
                    ma.setAccessible(true);
                    ma.set(impl, newAssetManager);
                }
                Field mt = ContextThemeWrapper.class.getDeclaredField("mTheme");
                mt.setAccessible(true);
                mt.set(activity, null);
                Method mtm = ContextThemeWrapper.class.getDeclaredMethod("initializeTheme");
                mtm.setAccessible(true);
                mtm.invoke(activity);
                // As of API 24, mTheme is gone (but updates work without these changes
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Method mCreateTheme = AssetManager.class
                            .getDeclaredMethod("createTheme");
                    mCreateTheme.setAccessible(true);
                    Object internalTheme = mCreateTheme.invoke(newAssetManager);
                    Field mTheme = Resources.Theme.class.getDeclaredField("mTheme");
                    mTheme.setAccessible(true);
                    mTheme.set(theme, internalTheme);
                }
            } catch (Throwable e) {
                Log.e("MonkeyPatcher",
                        "Failed to update existing theme for activity " + activity, e);
            }
            pruneResourceCaches(resources);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static void updateResourcesConfiguration(Resources resources) {
        resources.updateConfiguration(resources.getConfiguration(),
                resources.getDisplayMetrics());
    }



    private static void pruneResourceCaches(@NonNull Object resources) {
        // Drain TypedArray instances from the typed array pool since these can hold on
        // to stale asset data
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Field typedArrayPoolField =
                        Resources.class.getDeclaredField("mTypedArrayPool");
                typedArrayPoolField.setAccessible(true);
                Object pool = typedArrayPoolField.get(resources);
                Class<?> poolClass = pool.getClass();
                Method acquireMethod = poolClass.getDeclaredMethod("acquire");
                acquireMethod.setAccessible(true);
                while (true) {
                    Object typedArray = acquireMethod.invoke(pool);
                    if (typedArray == null) {
                        break;
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Really should only be N; fix this as soon as it has its own API level
            try {
                Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                mResourcesImpl.setAccessible(true);
                // For the remainder, use the ResourcesImpl instead, where all the fields
                // now live
                resources = mResourcesImpl.get(resources);
            } catch (Throwable ignore) {
            }
        }
        // Prune bitmap and color state lists etc caches
        Object lock = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                Field field = resources.getClass().getDeclaredField("mAccessLock");
                field.setAccessible(true);
                lock = field.get(resources);
            } catch (Throwable ignore) {
            }
        } else {
            try {
                Field field = Resources.class.getDeclaredField("mTmpValue");
                field.setAccessible(true);
                lock = field.get(resources);
            } catch (Throwable ignore) {
            }
        }
        if (lock == null) {
            lock = MonkeyPatcher.class;
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            // Prune bitmap and color caches
            pruneResourceCache(resources, "mDrawableCache");
            pruneResourceCache(resources,"mColorDrawableCache");
            pruneResourceCache(resources,"mColorStateListCache");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pruneResourceCache(resources, "mAnimatorCache");
                pruneResourceCache(resources, "mStateListAnimatorCache");
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                pruneResourceCache(resources, "sPreloadedDrawables");
                pruneResourceCache(resources, "sPreloadedColorDrawables");
                pruneResourceCache(resources, "sPreloadedColorStateLists");
            }
        }
    }
    @SuppressLint("NewApi")
    private static boolean pruneResourceCache(@NonNull Object resources,
                                              @NonNull String fieldName) {
        try {
            Class<?> resourcesClass = resources.getClass();
            Field cacheField;
            try {
                cacheField = resourcesClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignore) {
                cacheField = Resources.class.getDeclaredField(fieldName);
            }
            cacheField.setAccessible(true);
            Object cache = cacheField.get(resources);
            // Find the class which defines the onConfigurationChange method
            Class<?> type = cacheField.getType();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                if (cache instanceof SparseArray) {
                    ((SparseArray) cache).clear();
                    return true;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && cache instanceof LongSparseArray) {
                    // LongSparseArray has API level 16 but was private (and available inside
                    // the framework) in 15 and is used for this cache.
                    //noinspection AndroidLintNewApi
                    ((LongSparseArray) cache).clear();
                    return true;
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // JellyBean, KitKat, Lollipop
                if ("mColorStateListCache".equals(fieldName)) {
                    // For some reason framework doesn't call clearDrawableCachesLocked on
                    // this field
                    if (cache instanceof LongSparseArray) {
                        //noinspection AndroidLintNewApi
                        ((LongSparseArray)cache).clear();
                    }
                } else if (type.isAssignableFrom(ArrayMap.class)) {
                    Method clearArrayMap = Resources.class.getDeclaredMethod(
                            "clearDrawableCachesLocked", ArrayMap.class, Integer.TYPE);
                    clearArrayMap.setAccessible(true);
                    clearArrayMap.invoke(resources, cache, -1);
                    return true;
                } else if (type.isAssignableFrom(LongSparseArray.class)) {
                    try {
                        Method clearSparseMap = Resources.class.getDeclaredMethod(
                                "clearDrawableCachesLocked", LongSparseArray.class, Integer.TYPE);
                        clearSparseMap.setAccessible(true);
                        clearSparseMap.invoke(resources, cache, -1);
                        return true;
                    } catch (NoSuchMethodException e) {
                        if (cache instanceof LongSparseArray) {
                            //noinspection AndroidLintNewApi
                            ((LongSparseArray)cache).clear();
                            return true;
                        }
                    }
                } else if (type.isArray() &&
                        type.getComponentType().isAssignableFrom(LongSparseArray.class)) {
                    LongSparseArray[] arrays = (LongSparseArray[])cache;
                    for (LongSparseArray array : arrays) {
                        if (array != null) {
                            //noinspection AndroidLintNewApi
                            array.clear();
                        }
                    }
                    return true;
                }
            } else {
                // Marshmallow: DrawableCache class
                while (type != null) {
                    try {
                        Method configChangeMethod = type.getDeclaredMethod(
                                "onConfigurationChange", Integer.TYPE);
                        configChangeMethod.setAccessible(true);
                        configChangeMethod.invoke(cache, -1);
                        return true;
                    } catch (Throwable ignore) {
                    }
                    type = type.getSuperclass();
                }
            }
        } catch (Throwable ignore) {
            // Not logging these; while there is some checking of SDK_INT here to avoid
            // doing a lot of unnecessary field lookups, it's not entirely accurate and
            // errs on the side of caution (since different devices may have picked up
            // different snapshots of the framework); therefore, it's normal for this
            // to attempt to look up a field for a cache that isn't there; only if it's
            // really there will it continue to flush that particular cache.
        }
        return false;
    }

}
