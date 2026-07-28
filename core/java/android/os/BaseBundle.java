/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.os;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Intent;
import android.security.Flags;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.BstUtils;
import android.util.Log;
import android.util.MathUtils;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.function.BiFunction;

import org.json.JSONObject;

/**
 * A mapping from String keys to values of various types. In most cases, you
 * should work directly with either the {@link Bundle} or
 * {@link PersistableBundle} subclass.
 */
@android.ravenwood.annotation.RavenwoodKeepWholeClass
@SuppressWarnings("HiddenSuperclass")
public class BaseBundle implements Parcel.ClassLoaderProvider {
    /** @hide */
    protected static final String TAG = "Bundle";
    static final boolean DEBUG = false;

    /**
     * Keep them in sync with frameworks/native/libs/binder/PersistableBundle.cpp.
     *
     * @hide
     */
    @VisibleForTesting
    static final int BUNDLE_MAGIC = 0x4C444E42; // 'B' 'N' 'D' 'L'
    private static final int BUNDLE_MAGIC_NATIVE = 0x4C444E44; // 'B' 'N' 'D' 'N'

    /**
     * Flag indicating that this Bundle is okay to "defuse", see {@link #setShouldDefuse(boolean)}
     * for more details.
     * <p>
     * This should <em>only</em> be set when the Bundle reaches its final destination, otherwise a
     * system process may clobber contents that were destined for an app that could have unparceled
     * them.
     */
    static final int FLAG_DEFUSABLE = 1 << 0;

    private static final boolean LOG_DEFUSABLE = false;

    private static volatile boolean sShouldDefuse = false;

    // A16DBG:P2:FW-CORE-APP-15 BaseBundle affiliate (a13)
    private static final String BST_REFERRAL_TAG = "Bundle-Affiliate";
    static final boolean BST_DEBUG = DEBUG || android.os.SystemProperties.getInt("bst.debug.referral", 0) > 0 ? true : false;
    //stores referral Data, ArrayList stores referral, referralClickedTime, statSent, delay and skipReferral info in the given order.
    private static HashMap<String, String> mBstReferralInstallTimeList = new HashMap<String, String>();
    // stores the firstInstallTime data in the list.
    private static HashMap<String, ArrayList<Long>> mBstInstallTimeList = new HashMap<String, ArrayList<Long>>();
    //stores the actual and the modified values.
    private static HashMap<String, String> mBstReferralModDataList = new HashMap<String, String>();
    //stores if we have sent the stat for that package, so that we don't send the stat again and again
    private static HashMap<String, Boolean> mBstStatSendList = new HashMap<String, Boolean>();

    //It stores the referral data for stat.
     private static final String affiliateFilePath = "/data/downloads/.aff/";

    private static final String bstInstallReferralPath = affiliateFilePath + ".ir";
    private static final String bstOtherInstallReferrerPath = affiliateFilePath + ".or";

    //It stores the list containing firstInstallTime data.
    private static final String bstInstallTimeListPath = affiliateFilePath + ".pit";
    //It stores the list containing referral related data.
    private static final String bstAppReferralListPath = affiliateFilePath + ".fl";
    //It stores data for event app_install_referrer_request
    private static final String bstAppInstallReferrerReqStatPath = affiliateFilePath + ".airr";

    private static final int mBstAffiliateTestingValue = SystemProperties.getInt("bst.debug.affiliate.test", 0);

    /**
     * Set global variable indicating that any Bundles parsed in this process should be "defused".
     * That is, any {@link BadParcelableException} encountered will be suppressed and logged. Also:
     * <ul>
     *   <li>If it was the deserialization of a custom item (eg. {@link Parcelable}) that caused the
     *   exception, {@code null} will be returned but the item will be held in the map in its
     *   serialized form (lazy value).
     *   <li>If the exception happened during partial deserialization, that is, during the read of
     *   the map and its basic types (while skipping custom types), the map will be left empty.
     * </ul>
     *
     * @hide
     */
    public static void setShouldDefuse(boolean shouldDefuse) {
        sShouldDefuse = shouldDefuse;
    }

    // A parcel cannot be obtained during compile-time initialization. Put the
    // empty parcel into an inner class that can be initialized separately. This
    // allows to initialize BaseBundle, and classes depending on it.
    /** @hide */
    static final class NoImagePreloadHolder {
        public static final Parcel EMPTY_PARCEL = Parcel.obtain();
    }

    // Invariant - exactly one of mMap / mParcelledData will be null
    // (except inside a call to unparcel)

    @UnsupportedAppUsage
    ArrayMap<String, Object> mMap = null;

    /*
     * If mParcelledData is non-null, then mMap will be null and the
     * data are stored as a Parcel containing a Bundle.  When the data
     * are unparcelled, mParcelledData will be set to null.
     */
    @UnsupportedAppUsage
    volatile Parcel mParcelledData = null;

    /**
     * Whether {@link #mParcelledData} was generated by native code or not.
     */
    private boolean mParcelledByNative;

    /**
     * Flag indicating if mParcelledData is only referenced in this bundle.
     * mParcelledData could be referenced elsewhere if mMap contains lazy values,
     * and bundle data is copied to another bundle using putAll or the copy constructors.
     */
    boolean mOwnsLazyValues = true;

    /** Tracks how many lazy values are referenced in mMap */
    private int mLazyValues = 0;

    /**
     * As mParcelledData is set to null when it is unparcelled, we keep a weak reference to
     * it to aid in recycling it. Do not use this reference otherwise.
     * Is non-null iff mMap contains lazy values.
    */
    private WeakReference<Parcel> mWeakParcelledData = null;

    /**
     * The ClassLoader used when unparcelling data from mParcelledData.
     */
    private ClassLoader mClassLoader;

    /** @hide */
    @VisibleForTesting
    public int mFlags;
    private boolean mHasIntent = false;

    /**
     * Constructs a new, empty Bundle that uses a specific ClassLoader for
     * instantiating Parcelable and Serializable objects.
     *
     * @param loader An explicit ClassLoader to use when instantiating objects
     * inside of the Bundle.
     * @param capacity Initial size of the ArrayMap.
     */
    BaseBundle(@Nullable ClassLoader loader, int capacity) {
        mMap = capacity > 0 ?
                new ArrayMap<String, Object>(capacity) : new ArrayMap<String, Object>();
        mClassLoader = loader == null ? getClass().getClassLoader() : loader;
    }

    /**
     * Constructs a new, empty Bundle.
     */
    BaseBundle() {
        this((ClassLoader) null, 0);
    }

    /**
     * Constructs a Bundle whose data is stored as a Parcel.  The data
     * will be unparcelled on first contact, using the assigned ClassLoader.
     *
     * @param parcelledData a Parcel containing a Bundle
     */
    BaseBundle(Parcel parcelledData) {
        readFromParcelInner(parcelledData);
    }

    BaseBundle(Parcel parcelledData, int length) {
        readFromParcelInner(parcelledData, length);
    }

    /**
     * Constructs a new, empty Bundle that uses a specific ClassLoader for
     * instantiating Parcelable and Serializable objects.
     *
     * @param loader An explicit ClassLoader to use when instantiating objects
     * inside of the Bundle.
     */
    BaseBundle(ClassLoader loader) {
        this(loader, 0);
    }

    /**
     * Constructs a new, empty Bundle sized to hold the given number of
     * elements. The Bundle will grow as needed.
     *
     * @param capacity the initial capacity of the Bundle
     */
    BaseBundle(int capacity) {
        this((ClassLoader) null, capacity);
    }

    /**
     * Constructs a Bundle containing a copy of the mappings from the given
     * Bundle.
     *
     * @param b a Bundle to be copied.
     */
    BaseBundle(BaseBundle b) {
        this(b, /* deep */ false);
    }

    /**
     * Constructs a {@link BaseBundle} containing a copy of {@code from}.
     *
     * @param from The bundle to be copied.
     * @param deep Whether is a deep or shallow copy.
     *
     * @hide
     */
    BaseBundle(BaseBundle from, boolean deep) {
        synchronized (from) {
            mClassLoader = from.mClassLoader;

            if (from.mMap != null) {
                mOwnsLazyValues = false;
                from.mOwnsLazyValues = false;

                if (!deep) {
                    mMap = new ArrayMap<>(from.mMap);
                } else {
                    final ArrayMap<String, Object> fromMap = from.mMap;
                    final int n = fromMap.size();
                    mMap = new ArrayMap<>(n);
                    for (int i = 0; i < n; i++) {
                        mMap.append(fromMap.keyAt(i), deepCopyValue(fromMap.valueAt(i)));
                    }
                }
            } else {
                mMap = null;
            }

            final Parcel parcelledData;
            if (from.mParcelledData != null) {
                if (from.isEmptyParcel()) {
                    parcelledData = NoImagePreloadHolder.EMPTY_PARCEL;
                    mParcelledByNative = false;
                } else {
                    parcelledData = Parcel.obtain();
                    parcelledData.appendFrom(from.mParcelledData, 0,
                            from.mParcelledData.dataSize());
                    parcelledData.setDataPosition(0);
                    mParcelledByNative = from.mParcelledByNative;
                }
            } else {
                parcelledData = null;
                mParcelledByNative = false;
            }

            // Keep as last statement to ensure visibility of other fields
            mParcelledData = parcelledData;
            mHasIntent = from.mHasIntent;
        }
    }

    /** @hide */
    public boolean hasIntent() {
        return mHasIntent;
    }

    /** @hide */
    public void setHasIntent(boolean hasIntent) {
        mHasIntent = hasIntent;
    }

    /**
     * TODO: optimize this later (getting just the value part of a Bundle
     * with a single pair) once Bundle.forPair() above is implemented
     * with a special single-value Map implementation/serialization.
     *
     * Note: value in single-pair Bundle may be null.
     *
     * @hide
     */
    public String getPairValue() {
        unparcel();
        int size = mMap.size();
        if (size > 1) {
            Log.w(TAG, "getPairValue() used on Bundle with multiple pairs.");
        }
        if (size == 0) {
            return null;
        }
        try {
            return getValueAt(0, String.class);
        } catch (ClassCastException | BadTypeParcelableException e) {
            typeWarning("getPairValue()", "String", e);
            return null;
        }
    }

    /**
     * Changes the ClassLoader this Bundle uses when instantiating objects.
     *
     * @param loader An explicit ClassLoader to use when instantiating objects
     * inside of the Bundle.
     */
    void setClassLoader(ClassLoader loader) {
        mClassLoader = loader;
    }

    /**
     * Return the ClassLoader currently associated with this Bundle.
     * @hide
     */
    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    /**
     * If the underlying data are stored as a Parcel, unparcel them
     * using the currently assigned class loader.
     */
    @UnsupportedAppUsage
    final void unparcel() {
        unparcel(/* itemwise */ false);
    }

    /** Deserializes the underlying data and each item if {@code itemwise} is true. */
    final void unparcel(boolean itemwise) {
        synchronized (this) {
            final Parcel source = mParcelledData;
            if (source != null) {
                Preconditions.checkState(mOwnsLazyValues);
                initializeFromParcelLocked(source, /*ownsParcel*/ true, mParcelledByNative);
            } else {
                if (DEBUG) {
                    Log.d(TAG, "unparcel "
                            + Integer.toHexString(System.identityHashCode(this))
                            + ": no parcelled data");
                }
            }
            if (itemwise) {
                if (LOG_DEFUSABLE && sShouldDefuse && (mFlags & FLAG_DEFUSABLE) == 0) {
                    Slog.wtf(TAG,
                            "Attempting to unparcel all items in a Bundle while in transit; this "
                                    + "may remove elements intended for the final desitination.",
                            new Throwable());
                }
                for (int i = 0, n = mMap.size(); i < n; i++) {
                    // Triggers deserialization of i-th item, if needed
                    getValueAt(i, /* clazz */ null);
                }
            }
        }
    }

    /**
     * Returns the value for key {@code key}.
     *
     * This call should always be made after {@link #unparcel()} or inside a lock after making sure
     * {@code mMap} is not null.
     *
     * @deprecated Use {@link #getValue(String, Class, Class[])}. This method should only be used in
     *      other deprecated APIs.
     *
     * @hide
     */
    @Deprecated
    @Nullable
    final Object getValue(String key) {
        return getValue(key, /* clazz */ null);
    }

    /** Same as {@link #getValue(String, Class, Class[])} with no item types. */
    @Nullable
    final <T> T getValue(String key, @Nullable Class<T> clazz) {
        // Avoids allocating Class[0] array
        return getValue(key, clazz, (Class<?>[]) null);
    }

    /**
     * Returns the value for key {@code key} for expected return type {@code clazz} (or pass {@code
     * null} for no type check).
     *
     * For {@code itemTypes}, see {@link Parcel#readValue(int, ClassLoader, Class, Class[])}.
     *
     * This call should always be made after {@link #unparcel()} or inside a lock after making sure
     * {@code mMap} is not null.
     *
     * @hide
     */
    @Nullable
    final <T> T getValue(String key, @Nullable Class<T> clazz, @Nullable Class<?>... itemTypes) {
        int i = mMap.indexOfKey(key);
        return (i >= 0) ? getValueAt(i, clazz, itemTypes) : null;
    }

    /**
     * return true if the value corresponding to this key is still parceled.
     * @hide
     */
    public boolean isValueParceled(String key) {
        if (mMap == null) return true;
        int i = mMap.indexOfKey(key);
        return (mMap.valueAt(i) instanceof BiFunction<?, ?, ?>);
    }
    /**
     * Returns the value for a certain position in the array map for expected return type {@code
     * clazz} (or pass {@code null} for no type check).
     *
     * For {@code itemTypes}, see {@link Parcel#readValue(int, ClassLoader, Class, Class[])}.
     *
     * This call should always be made after {@link #unparcel()} or inside a lock after making sure
     * {@code mMap} is not null.
     *
     * @hide
     */
    @SuppressWarnings("unchecked")
    @Nullable
    final <T> T getValueAt(int i, @Nullable Class<T> clazz, @Nullable Class<?>... itemTypes) {
        Object object = mMap.valueAt(i);
        if (object instanceof BiFunction<?, ?, ?>) {
            synchronized (this) {
                object = unwrapLazyValueFromMapLocked(i, clazz, itemTypes);
            }
            if ((mFlags & Bundle.FLAG_VERIFY_TOKENS_PRESENT) != 0) {
                Intent.maybeMarkAsMissingCreatorToken(object);
            }
        } else if (object instanceof Bundle) {
            Bundle bundle = (Bundle) object;
            bundle.setClassLoaderSameAsContainerBundleWhenRetrievedFirstTime(this);
        }
        return (clazz != null) ? clazz.cast(object) : (T) object;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @GuardedBy("this")
    private Object unwrapLazyValueFromMapLocked(int i, @Nullable Class<?> clazz,
            @Nullable Class<?>... itemTypes) {
        Object object = mMap.valueAt(i);
        if (object instanceof BiFunction<?, ?, ?>) {
            try {
                object = ((BiFunction<Class<?>, Class<?>[], ?>) object).apply(clazz, itemTypes);
            } catch (BadParcelableException e) {
                if (sShouldDefuse) {
                    if (Flags.wtfBundleDefuse()) {
                        Slog.wtf(TAG, "Failed to parse item " + mMap.keyAt(i) + ", returning null.",
                                e);
                    } else {
                        Log.w(TAG, "Failed to parse item " + mMap.keyAt(i) + ", returning null.",
                                e);
                    }
                    if (Flags.deprecateBundleDefuse()) {
                        throw e;
                    }
                    return null;
                } else {
                    throw e;
                }
            }
            mMap.setValueAt(i, object);
            mLazyValues--;
            if (mOwnsLazyValues) {
                Preconditions.checkState(mLazyValues >= 0,
                        "Lazy values ref count below 0");
                // No more lazy values in mMap, so we can destroy the parcel early rather than
                // waiting for the next GC run
                Parcel parcel = mWeakParcelledData.get();
                if (mLazyValues == 0 && parcel != null) {
                    parcel.destroy();
                    mWeakParcelledData = null;
                }
            }
        }
        return object;
    }

    private void initializeFromParcelLocked(@NonNull Parcel parcelledData, boolean ownsParcel,
            boolean parcelledByNative) {
        if (isEmptyParcel(parcelledData)) {
            if (DEBUG) {
                Log.d(TAG, "unparcel "
                        + Integer.toHexString(System.identityHashCode(this)) + ": empty");
            }
            if (mMap == null) {
                mMap = new ArrayMap<>(1);
            } else {
                mMap.erase();
            }
            mParcelledByNative = false;
            mParcelledData = null;
            return;
        }

        final int count = parcelledData.readInt();
        if (DEBUG) {
            Log.d(TAG, "unparcel " + Integer.toHexString(System.identityHashCode(this))
                    + ": reading " + count + " maps");
        }
        if (count < 0) {
            return;
        }
        ArrayMap<String, Object> map = mMap;
        if (map == null) {
            map = new ArrayMap<>(count);
        } else {
            map.erase();
            map.ensureCapacity(count);
        }
        int[] numLazyValues = new int[]{0};
        try {
            parcelledData.readArrayMap(map, count, !parcelledByNative,
                    /* lazy */ ownsParcel, this, numLazyValues);
        } catch (BadParcelableException e) {
            if (sShouldDefuse) {
                if (Flags.wtfBundleDefuse()) {
                    Slog.wtf(TAG, "Failed to parse Bundle, but defusing quietly", e);
                } else {
                    Log.w(TAG, "Failed to parse Bundle, but defusing quietly", e);
                }
                if (Flags.deprecateBundleDefuse()) {
                    throw e;
                }
                map.erase();
            } else {
                throw e;
            }
        } finally {
            mWeakParcelledData = null;
            if (ownsParcel) {
                if (numLazyValues[0] == 0) {
                    // No lazy value, we can directly recycle this parcel
                    parcelledData.recycle();
                } else {
                    mWeakParcelledData = new WeakReference<>(parcelledData);
                }
            }

            mLazyValues = numLazyValues[0];
            mParcelledByNative = false;
            mMap = map;
            // Set field last as it is volatile
            mParcelledData = null;
        }
        if (DEBUG) {
            Log.d(TAG, "unparcel " + Integer.toHexString(System.identityHashCode(this))
                    + " final map: " + mMap);
        }
    }

    /**
     * @hide
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public boolean isParcelled() {
        return mParcelledData != null;
    }

    /**
     * @hide
     */
    public boolean isEmptyParcel() {
        return isEmptyParcel(mParcelledData);
    }

    /**
     * @hide
     */
    private static boolean isEmptyParcel(Parcel p) {
        return p == NoImagePreloadHolder.EMPTY_PARCEL;
    }

    /**
     * Returns the backing map of this bundle after deserializing every item.
     *
     * <p><b>Warning:</b> This method will deserialize every item on the bundle, including custom
     * types such as {@link Parcelable} and {@link Serializable}, so only use this when you trust
     * the source. Specifically don't use this method on app-provided bundles.
     *
     * @hide
     */
    ArrayMap<String, Object> getItemwiseMap() {
        unparcel(/* itemwise */ true);
        return mMap;
    }

    /**
     * Returns the number of mappings contained in this Bundle.
     *
     * @return the number of mappings as an int.
     */
    public int size() {
        unparcel();
        return mMap.size();
    }

    /**
     * Returns true if the mapping of this Bundle is empty, false otherwise.
     */
    public boolean isEmpty() {
        unparcel();
        return mMap.isEmpty();
    }

    /**
     * This method returns true when the parcel is 'definitely' empty.
     * That is, it may return false for an empty parcel. But will never return true for a non-empty
     * one.
     *
     * @hide this should probably be the implementation of isEmpty().  To do that we
     * need to ensure we always use the special empty parcel form when the bundle is
     * empty.  (This may already be the case, but to be safe we'll do this later when
     * we aren't trying to stabilize.)
     */
    public boolean isDefinitelyEmpty() {
        if (isParcelled()) {
            return isEmptyParcel();
        } else {
            return isEmpty();
        }
    }

    /**
     * Does a loose equality check between two given {@link BaseBundle} objects.
     * Returns {@code true} if both are {@code null}, or if both are equal as per
     * {@link #kindofEquals(BaseBundle)}
     *
     * @param a A {@link BaseBundle} object
     * @param b Another {@link BaseBundle} to compare with a
     * @return {@code true} if both are the same, {@code false} otherwise
     *
     * @see #kindofEquals(BaseBundle)
     *
     * @hide
     */
    public static boolean kindofEquals(@Nullable BaseBundle a, @Nullable BaseBundle b) {
        return (a == b) || (a != null && a.kindofEquals(b));
    }

    /**
     * Performs a loose equality check, which means there can be false negatives but if the method
     * returns true than both objects are guaranteed to be equal.
     *
     * The point is that this method is a light-weight check in performance terms.
     *
     * @hide
     */
    public boolean kindofEquals(BaseBundle other) {
        if (other == null) {
            return false;
        }
        if (isDefinitelyEmpty() && other.isDefinitelyEmpty()) {
            return true;
        }
        if (isParcelled() != other.isParcelled()) {
            // Big kind-of here!
            return false;
        } else if (isParcelled()) {
            return mParcelledData.compareData(other.mParcelledData) == 0;
        } else {
            // Following semantic above of failing in case we get a serialized value vs a
            // deserialized one, we'll compare the map. If a certain element hasn't been
            // deserialized yet, it's a function object (or more specifically a LazyValue, but let's
            // pretend we don't know that here :P), we'll use that element's equality comparison as
            // map naturally does. That will takes care of comparing the payload if needed (see
            // Parcel.readLazyValue() for details).
            return mMap.equals(other.mMap);
        }
    }

    /**
     * Removes all elements from the mapping of this Bundle.
     * Recycles the underlying parcel if it is still present.
     */
    public void clear() {
        unparcel();
        if (mOwnsLazyValues && mWeakParcelledData != null) {
            Parcel parcel = mWeakParcelledData.get();
            if (parcel != null) {
                parcel.destroy();
            }
        }

        mWeakParcelledData = null;
        mLazyValues = 0;
        mOwnsLazyValues = true;
        mMap.clear();
    }

    private Object deepCopyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Bundle) {
            return ((Bundle)value).deepCopy();
        } else if (value instanceof PersistableBundle) {
            return ((PersistableBundle)value).deepCopy();
        } else if (value instanceof ArrayList) {
            return deepcopyArrayList((ArrayList) value);
        } else if (value.getClass().isArray()) {
            if (value instanceof int[]) {
                return ((int[])value).clone();
            } else if (value instanceof long[]) {
                return ((long[])value).clone();
            } else if (value instanceof float[]) {
                return ((float[])value).clone();
            } else if (value instanceof double[]) {
                return ((double[])value).clone();
            } else if (value instanceof Object[]) {
                return ((Object[])value).clone();
            } else if (value instanceof byte[]) {
                return ((byte[])value).clone();
            } else if (value instanceof short[]) {
                return ((short[])value).clone();
            } else if (value instanceof char[]) {
                return ((char[]) value).clone();
            }
        }
        return value;
    }

    private ArrayList deepcopyArrayList(ArrayList from) {
        final int N = from.size();
        ArrayList out = new ArrayList(N);
        for (int i=0; i<N; i++) {
            out.add(deepCopyValue(from.get(i)));
        }
        return out;
    }

    /**
     * Returns true if the given key is contained in the mapping
     * of this Bundle.
     *
     * @param key a String key
     * @return true if the key is part of the mapping, false otherwise
     */
    public boolean containsKey(String key) {
        unparcel();
        return mMap.containsKey(key);
    }

    /**
     * Returns the entry with the given key as an object.
     *
     * @param key a String key
     * @return an Object, or null
     *
     * @deprecated Use the type-safe specific APIs depending on the type of the item to be
     *      retrieved, eg. {@link #getString(String)}.
     */
    @Deprecated
    @Nullable
    public Object get(String key) {
        unparcel();
        return getValue(key);
    }

    /**
     * Returns the object of type {@code clazz} for the given {@code key}, or {@code null} if:
     * <ul>
     *     <li>No mapping of the desired type exists for the given key.
     *     <li>A {@code null} value is explicitly associated with the key.
     *     <li>The object is not of type {@code clazz}.
     * </ul>
     *
     * <p>Use the more specific APIs where possible, especially in the case of containers such as
     * lists, since those APIs allow you to specify the type of the items.
     *
     * @param key String key
     * @param clazz The type of the object expected
     * @return an Object, or null
     */
    @Nullable
    <T> T get(@Nullable String key, @NonNull Class<T> clazz) {
        unparcel();
        try {
            return getValue(key, requireNonNull(clazz));
        } catch (ClassCastException | BadTypeParcelableException e) {
            typeWarning(key, clazz.getCanonicalName(), e);
            return null;
        }
    }

    /**
     * Removes any entry with the given key from the mapping of this Bundle.
     *
     * @param key a String key
     */
    public void remove(String key) {
        unparcel();
        mMap.remove(key);
    }

    /**
     * Inserts all mappings from the given PersistableBundle into this BaseBundle.
     *
     * @param bundle a PersistableBundle
     */
    public void putAll(PersistableBundle bundle) {
        unparcel();
        bundle.unparcel();
        mMap.putAll(bundle.mMap);
    }

    /**
     * Inserts all mappings from the given Map into this BaseBundle.
     *
     * @param map a Map
     */
    void putAll(ArrayMap map) {
        unparcel();
        mMap.putAll(map);
    }

    /**
     * Returns a Set containing the Strings used as keys in this Bundle.
     *
     * @return a Set of String keys
     */
    public Set<String> keySet() {
        unparcel();
        return mMap.keySet();
    }

    /** @hide */
    public void putObject(@Nullable String key, @Nullable Object value) {
        if (value == null) {
            putString(key, null);
        } else if (value instanceof Boolean) {
            putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            putLong(key, (Long) value);
        } else if (value instanceof Double) {
            putDouble(key, (Double) value);
        } else if (value instanceof String) {
            putString(key, (String) value);
        } else if (value instanceof boolean[]) {
            putBooleanArray(key, (boolean[]) value);
        } else if (value instanceof int[]) {
            putIntArray(key, (int[]) value);
        } else if (value instanceof long[]) {
            putLongArray(key, (long[]) value);
        } else if (value instanceof double[]) {
            putDoubleArray(key, (double[]) value);
        } else if (value instanceof String[]) {
            putStringArray(key, (String[]) value);
        } else {
            throw new IllegalArgumentException("Unsupported type " + value.getClass());
        }
    }

    /**
     * Inserts a Boolean value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a boolean
     */
    public void putBoolean(@Nullable String key, boolean value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a byte value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a byte
     */
    void putByte(@Nullable String key, byte value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a char value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a char
     */
    void putChar(@Nullable String key, char value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a short value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a short
     */
    void putShort(@Nullable String key, short value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts an int value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value an int
     */
    public void putInt(@Nullable String key, int value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a long value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a long
     */
    public void putLong(@Nullable String key, long value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a float value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a float
     */
    void putFloat(@Nullable String key, float value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a double value into the mapping of this Bundle, replacing
     * any existing value for the given key.
     *
     * @param key a String, or null
     * @param value a double
     */
    public void putDouble(@Nullable String key, double value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a String value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a String, or null
     */
    public void putString(@Nullable String key, @Nullable String value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a CharSequence value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a CharSequence, or null
     */
    void putCharSequence(@Nullable String key, @Nullable CharSequence value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts an ArrayList<Integer> value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value an ArrayList<Integer> object, or null
     */
    void putIntegerArrayList(@Nullable String key, @Nullable ArrayList<Integer> value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts an ArrayList<String> value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value an ArrayList<String> object, or null
     */
    void putStringArrayList(@Nullable String key, @Nullable ArrayList<String> value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts an ArrayList<CharSequence> value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value an ArrayList<CharSequence> object, or null
     */
    void putCharSequenceArrayList(@Nullable String key, @Nullable ArrayList<CharSequence> value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a Serializable value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a Serializable object, or null
     */
    void putSerializable(@Nullable String key, @Nullable Serializable value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a boolean array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a boolean array object, or null
     */
    public void putBooleanArray(@Nullable String key, @Nullable boolean[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a byte array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a byte array object, or null
     */
    void putByteArray(@Nullable String key, @Nullable byte[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a short array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a short array object, or null
     */
    void putShortArray(@Nullable String key, @Nullable short[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a char array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a char array object, or null
     */
    void putCharArray(@Nullable String key, @Nullable char[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts an int array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value an int array object, or null
     */
    public void putIntArray(@Nullable String key, @Nullable int[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a long array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a long array object, or null
     */
    public void putLongArray(@Nullable String key, @Nullable long[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a float array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a float array object, or null
     */
    void putFloatArray(@Nullable String key, @Nullable float[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a double array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a double array object, or null
     */
    public void putDoubleArray(@Nullable String key, @Nullable double[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a String array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a String array object, or null
     */
    public void putStringArray(@Nullable String key, @Nullable String[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Inserts a CharSequence array value into the mapping of this Bundle, replacing
     * any existing value for the given key.  Either key or value may be null.
     *
     * @param key a String, or null
     * @param value a CharSequence array object, or null
     */
    void putCharSequenceArray(@Nullable String key, @Nullable CharSequence[] value) {
        unparcel();
        mMap.put(key, value);
    }

    /**
     * Returns the value associated with the given key, or false if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a boolean value
     */
    public boolean getBoolean(String key) {
        unparcel();
        if (DEBUG) Log.d(TAG, "Getting boolean in "
                + Integer.toHexString(System.identityHashCode(this)));
        return getBoolean(key, false);
    }

    // Log a message if the value was non-null but not of the expected type
    void typeWarning(String key, @Nullable Object value, String className,
            Object defaultValue, RuntimeException e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Key ");
        sb.append(key);
        sb.append(" expected ");
        sb.append(className);
        if (value != null) {
            sb.append(" but value was a ");
            sb.append(value.getClass().getName());
        } else {
            sb.append(" but value was of a different type");
        }
        sb.append(".  The default value ");
        sb.append(defaultValue);
        sb.append(" was returned.");
        Log.w(TAG, sb.toString());
        Log.w(TAG, "Attempt to cast generated internal exception:", e);
    }

    void typeWarning(String key, @Nullable Object value, String className, RuntimeException e) {
        typeWarning(key, value, className, "<null>", e);
    }

    void typeWarning(String key, String className, RuntimeException e) {
        typeWarning(key, /* value */ null, className, "<null>", e);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a boolean value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Boolean) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Boolean", defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Returns the value associated with the given key, or (byte) 0 if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a byte value
     */
    byte getByte(String key) {
        unparcel();
        return getByte(key, (byte) 0);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a byte value
     */
    Byte getByte(String key, byte defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Byte) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Byte", defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Returns the value associated with the given key, or (char) 0 if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a char value
     */
    char getChar(String key) {
        unparcel();
        return getChar(key, (char) 0);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a char value
     */
    char getChar(String key, char defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Character) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Character", defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Returns the value associated with the given key, or (short) 0 if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a short value
     */
    short getShort(String key) {
        unparcel();
        return getShort(key, (short) 0);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a short value
     */
    short getShort(String key, short defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Short) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Short", defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Returns the value associated with the given key, or 0 if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return an int value
     */
    public int getInt(String key) {
        unparcel();
        return getInt(key, 0);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return an int value
     */
   public int getInt(String key, int defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Integer) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Integer", defaultValue, e);
            return defaultValue;
        }
    }

    // HACK for Google play referral API. Making sure that if affiliate offer is present for this app,
    // then referrer_click_timestamp_seconds and install_begin_timestamp_seconds should be computed accordingly.
    private String bstAffiliateHack(String key, String orig_value) {
        String value = orig_value;
        JSONObject miscdata = new JSONObject();
        String packageName = null;
        try {
            if ("referrer_click_timestamp_seconds".equals(key)
                    || "referrer_click_timestamp_server_seconds".equals(key)
                    || "install_begin_timestamp_seconds".equals(key)
                    || "install_begin_timestamp_server_seconds".equals(key)
                    || "install_referrer".equals(key)) {
                /** Initialize all the variables */
                int pid = Binder.getCallingPid();
                packageName = BstUtils.getAppNameFromPid(pid);
               //sending event app_install_referrer_request by saving to file and read by FileObserver in BstCommandProcessor
                if (key.equals("install_referrer") && SystemProperties.getInt("bst.feature.send_offer_stats", 0) >= 2) {
                    File installReferrerStatFile = new File(bstAppInstallReferrerReqStatPath);
                    JSONObject object = new JSONObject();
                    object.put("event_name", "app_install_referrer_request");
                    object.put("pkg_name", packageName);
                    object.put("orig_install_referrer", value);
                    object.put("current_system_time", System.currentTimeMillis()/1000);
                    object.put("boot_system_time", (System.currentTimeMillis() - SystemClock.elapsedRealtime())/1000);
                    HashMap<String, String> map = new HashMap<String, String>();
                    map.put("data", object.toString());
                    boolean result = BstUtils.writeListToFile(map, bstAppInstallReferrerReqStatPath);
                }
                int maxDelayInstallBegin            = 5;
                long firstInstallTime               = 0L;
                String statFilePath                 = null;
                JSONObject referralDataObj          = new JSONObject();
                ArrayList<Long> pkgInstallTimeData  = null;
                boolean addMiscData                 = false;

                if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "PackageName : " + packageName);
                miscdata.put("pkgName", packageName);

                /** Loading the lists */
                mBstReferralInstallTimeList = (HashMap<String,String>) BstUtils.loadListFromFile(bstAppReferralListPath, mBstReferralInstallTimeList);
                mBstInstallTimeList = (HashMap<String, ArrayList<Long>>) BstUtils.loadListFromFile(bstInstallTimeListPath, mBstInstallTimeList);

                /** Fetch original values from map */
                String origInstallReferrer = String.valueOf(mMap.get("install_referrer"));
                long origReferrerClickServer = Long.parseLong(mMap.get("referrer_click_timestamp_server_seconds").toString());
                long origReferrerClick = Long.parseLong(mMap.get("referrer_click_timestamp_seconds").toString());
                long origInstallBeginServer = Long.parseLong(mMap.get("install_begin_timestamp_server_seconds").toString());
                long origInstallBegin = Long.parseLong(mMap.get("install_begin_timestamp_seconds").toString());

                miscdata.put("origInstallReferrer", origInstallReferrer);
                miscdata.put("origReferrerClick", origReferrerClick);
                miscdata.put("origReferrerClickServer", origReferrerClickServer);
                miscdata.put("origInstallBegin", origInstallBegin);
                miscdata.put("origInstallBeginServer", origInstallBeginServer);

                /** Check if we want to modify the referrer values for the package */
                String response = BstUtils.bstModifyReferrerApiValues(packageName, pid, mBstReferralInstallTimeList, mBstInstallTimeList, origInstallReferrer);
                JSONObject respObj = new JSONObject(response);
                miscdata.put("bstUtil-Response", response);

                //modifyReferrerValues is always true for affiliate apps
                boolean modifyReferrerValues = respObj.optBoolean("success", false);

                //sendOtherStat is true if we are not modifying values due to some reason.
                boolean sendOtherStat = false;
                if (!modifyReferrerValues) {
                    // If referrer is not present then send stat only if bst.feature.send_offer_stats is greater than 0.
                    if (SystemProperties.getInt("bst.feature.send_offer_stats", 0) <= 0 && respObj.optString("reason", "").equals("referrerNotPresent"))
                        sendOtherStat = false;
                    else
                        sendOtherStat = true;
                }

                if (BST_DEBUG) {
                    Log.d(BST_REFERRAL_TAG, "package = " + packageName + ", key = " + key + ", modifyValues = " + modifyReferrerValues);
                    Log.d(BST_REFERRAL_TAG, "ActualValue: package = " + packageName + ", key = " + key + ", val = " + orig_value);
                }

                // Initialize modified values.
                String modInstallReferrer = origInstallReferrer;
                long modReferrerClickServer = origReferrerClickServer;
                long modReferrerClick = origReferrerClick;
                long modInstallBeginServer = origInstallBeginServer;
                long modInstallBegin = origInstallBegin;

                if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "modifyReferrerValues : " + modifyReferrerValues
                        + " sendOtherStat : " + sendOtherStat);

                // we need to modify the values
                if (modifyReferrerValues) {
                    String referralData = mBstReferralInstallTimeList.getOrDefault(packageName, "{}");
                    referralDataObj = new JSONObject(referralData);

                    String modDataFilePath = referralDataObj.optString("mod_data_file_path", "");

                    File modDataFile = new File(modDataFilePath);
                    String modDataJson = null;

                    if (mBstAffiliateTestingValue == 9) {
                        modDataFile = null;
                    }

                    if (modDataFile == null || !modDataFile.exists()) {
                        if (BST_DEBUG) Log.w(BST_REFERRAL_TAG, "Modified data file " + modDataFilePath + ", does not exist, returning with orig_value : " + orig_value);
                        miscdata.put("error", "file doesn't exist " + modDataFilePath);
                        miscdata.put("referrer_source", "gplay_other_install_referrer");
                        sendOtherReferrerStat(packageName, miscdata);
                        return orig_value;
                    }

                    mBstReferralModDataList = (HashMap<String, String>) BstUtils.loadListFromFile(modDataFilePath, mBstReferralModDataList);
                    modDataJson = mBstReferralModDataList.getOrDefault(packageName, null);

                    // Check if final values are already present or not, if present use them.
                    if (modDataJson != null) {
                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "Modified data json is already populated for package : " + packageName + ", not recalculating values");
                        JSONObject modDataJsonObj = new JSONObject(modDataJson);
                        modInstallReferrer = modDataJsonObj.optString("mod_install_referrer", "");
                        modReferrerClickServer = modDataJsonObj.optLong("mod_referrer_click_timestamp_server_seconds", 0L);
                        modReferrerClick = modDataJsonObj.optLong("mod_referrer_click_timestamp_seconds", 0L);
                        modInstallBeginServer = modDataJsonObj.optLong("mod_install_begin_timestamp_server_seconds", 0L);
                        modInstallBegin = modDataJsonObj.optLong("mod_install_begin_timestamp_seconds", 0L);
                    } else {
                        // final values are not present
                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "Modified data json not present in file for packageName: " + packageName);

                        modInstallReferrer = referralDataObj.optString("referrer", "");

                        modReferrerClick = referralDataObj.optLong("mod_referrer_click_timestamp", 0L); // final URL click time as recorded by our HomeService
                        // Server timings could be different because of local clock is not synchronized with NTP, so treat server timings separately from local timings.
                        long clockTimeAdjustment = origInstallBeginServer - origInstallBegin;
                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "clockTimeAdjustment : " + clockTimeAdjustment);
                        modReferrerClickServer = modReferrerClick + clockTimeAdjustment;

                        pkgInstallTimeData = mBstInstallTimeList.getOrDefault(packageName, new ArrayList<Long>());
                        if (pkgInstallTimeData.size() > 0)
                            firstInstallTime = pkgInstallTimeData.get(0);

                        // Not check modReferrerClickServer timing, as that could be different if local client machine is not in sync. with NTP
                        // In that case, we can't compare local time (firstInstallTime) with server time (modReferrerClickServer)
                        if (mBstAffiliateTestingValue == 10) {
                            firstInstallTime = modReferrerClick - 10;
                        }

                        if (modReferrerClick > firstInstallTime) {
                            if (BST_DEBUG) Log.w(BST_REFERRAL_TAG, "Not modifying values as modReferrerClick(" + modReferrerClick + ") is greater than firstInstallTime(" + firstInstallTime + ")");
                            miscdata.put("error", "modReferrerClick > firstInstallTime");
                            miscdata.put("modReferrerClick", modReferrerClick);
                            miscdata.put("firstInstallTime", firstInstallTime);
                            miscdata.put("clockTimeAdjustment", clockTimeAdjustment);
                            miscdata.put("modInstallReferrer", modInstallReferrer);
                            miscdata.put("modReferrerClickServer", modReferrerClickServer);
                            miscdata.put("referrer_source", "gplay_other_install_referrer");
                            sendOtherReferrerStat(packageName, miscdata);
                            return orig_value;
                        }

                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "origInstallBegin : " + origInstallBegin
                                + " modReferrerClick : " + modReferrerClick);
                        // Check if install begin (download started) after final url hit time or not?
                        if (origInstallBegin > modReferrerClick) {
                            modInstallBegin = origInstallBegin;
                            modInstallBeginServer = origInstallBeginServer;
                        } else {
                            long delta = (long) Math.ceil((firstInstallTime - modReferrerClick) / (double) 4);
                            long delayInstallBegin = Math.min(delta, maxDelayInstallBegin);
                            modInstallBegin = modReferrerClick + delayInstallBegin;
                            modInstallBeginServer = modInstallBegin + clockTimeAdjustment;
                            if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "delta : " + delta + " delayInstallBegin : " + delayInstallBegin + " modInstallBegin : " + modInstallBegin + " modInstallBeginServer : " + modInstallBeginServer);

                            if (mBstAffiliateTestingValue == 11) {
                                firstInstallTime = modInstallBegin - 10;
                            }

                            if (modInstallBegin >= firstInstallTime) {
                                if (BST_DEBUG) Log.w(BST_REFERRAL_TAG, "Not modifying values as modInstallBegin(" + modInstallBegin + ") is greater than or equal to firstInstallTime(" + firstInstallTime + ") returning with orig_value : " + orig_value);
                                miscdata.put("error", "modInstallBegin(" + modInstallBegin + ") is more than firstInstallTime(" + firstInstallTime + ")");
                                miscdata.put("modReferrerClick", modReferrerClick);
                                miscdata.put("firstInstallTime", firstInstallTime);
                                miscdata.put("clockTimeAdjustment", clockTimeAdjustment);
                                miscdata.put("modInstallReferrer", modInstallReferrer);
                                miscdata.put("modReferrerClickServer", modReferrerClickServer);
                                miscdata.put("delta", delta);
                                miscdata.put("delayInstallBegin", delayInstallBegin);
                                miscdata.put("modInstallBegin", modInstallBegin);
                                miscdata.put("modInstallBeginServer", modInstallBeginServer);
                                miscdata.put("referrer_source", "gplay_other_install_referrer");
                                sendOtherReferrerStat(packageName, miscdata);
                                return orig_value;
                            }
                        }

                        if (mBstAffiliateTestingValue == 12) {
                            modInstallBegin = modReferrerClick - 10;
                        }

                        if( firstInstallTime <= modInstallBegin ||  modInstallBegin <= modReferrerClick || firstInstallTime <= modReferrerClick) {
                            // basic condition don't met
                            if (BST_DEBUG) Log.w(BST_REFERRAL_TAG, "Basic check failed, firstInstallTime : " + firstInstallTime + " modInstallBegin : " + modInstallBegin + " modReferrerClick : " + modReferrerClick + " orig_value : " + orig_value);
                            miscdata.put("error", "firstInstallTime <= modInstallBegin || modInstallBegin <= modReferrerClick || firstInstallTime <= modReferrerClick");
                            miscdata.put("modReferrerClick", modReferrerClick);
                            miscdata.put("firstInstallTime", firstInstallTime);
                            miscdata.put("clockTimeAdjustment", clockTimeAdjustment);
                            miscdata.put("modInstallReferrer", modInstallReferrer);
                            miscdata.put("modReferrerClickServer", modReferrerClickServer);
                            miscdata.put("modInstallBegin", modInstallBegin);
                            miscdata.put("modInstallBeginServer", modInstallBeginServer);
                            miscdata.put("referrer_source", "gplay_other_install_referrer");
                            sendOtherReferrerStat(packageName, miscdata);
                            return orig_value;
                        }

                        if (mBstAffiliateTestingValue == 13) {
                            modInstallBeginServer = modReferrerClickServer - 10;
                        }

                        // similarly check for server timing.
                        if(modReferrerClickServer > modInstallBeginServer) {
                            if (BST_DEBUG) Log.w(BST_REFERRAL_TAG, "Server basic check failed, modReferrerClickServer : " + modReferrerClickServer + " modInstallBeginServer : " + modInstallBeginServer + " returning with : " + orig_value);
                            miscdata.put("error", "modReferrerClickServer > modInstallBeginServer");
                            miscdata.put("modReferrerClick", modReferrerClick);
                            miscdata.put("firstInstallTime", firstInstallTime);
                            miscdata.put("clockTimeAdjustment", clockTimeAdjustment);
                            miscdata.put("modInstallReferrer", modInstallReferrer);
                            miscdata.put("modReferrerClickServer", modReferrerClickServer);
                            miscdata.put("modInstallBegin", modInstallBegin);
                            miscdata.put("modInstallBeginServer", modInstallBeginServer);
                            miscdata.put("referrer_source", "gplay_other_install_referrer");
                            sendOtherReferrerStat(packageName, miscdata);
                            return orig_value;
                        }

                        JSONObject modDataJsonObj = new JSONObject();
                        modDataJsonObj.put("install_referrer", origInstallReferrer);
                        modDataJsonObj.put("referrer_click_timestamp_server_seconds", origReferrerClickServer);
                        modDataJsonObj.put("referrer_click_timestamp_seconds", origReferrerClick);
                        modDataJsonObj.put("install_begin_timestamp_server_seconds", origInstallBeginServer);
                        modDataJsonObj.put("install_begin_timestamp_seconds", origInstallBegin);

                        modDataJsonObj.put("mod_install_referrer", modInstallReferrer);
                        modDataJsonObj.put("mod_referrer_click_timestamp_server_seconds", modReferrerClickServer);
                        modDataJsonObj.put("mod_referrer_click_timestamp_seconds", modReferrerClick);
                        modDataJsonObj.put("mod_install_begin_timestamp_server_seconds", modInstallBeginServer);
                        modDataJsonObj.put("mod_install_begin_timestamp_seconds", modInstallBegin);

                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "modDataJsonObj : " + modDataJsonObj.toString());

                        mBstReferralModDataList.put(packageName, modDataJsonObj.toString());
                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "Modified data json object for package " + packageName + " is " + mBstReferralModDataList);
                        BstUtils.writeListToFile(mBstReferralModDataList, modDataFilePath);
                    }

                    if ("referrer_click_timestamp_seconds".equals(key)) {
                        value = String.valueOf(modReferrerClick); // provide original value as measured by us.
                    } else if ("referrer_click_timestamp_server_seconds".equals(key)) {
                        value = String.valueOf(modReferrerClickServer);
                    } else if ("install_begin_timestamp_seconds".equals(key)) {
                        value = String.valueOf(modInstallBegin);
                    } else if ("install_begin_timestamp_server_seconds".equals(key)) {
                        value = String.valueOf(modInstallBeginServer);
                    } else if ("install_referrer".equals(key)) {
                        value = modInstallReferrer;
                    }

                    boolean installReferrerStatSent =
                        Boolean.valueOf(referralDataObj.optBoolean("gplay_install_referrer_stat", true)); //statSent
                    if (!installReferrerStatSent) {
                        addMiscData = true;
                        statFilePath = bstInstallReferralPath;

                        miscdata.put("referrer_source", "gplay_install_referrer");
                    }
                } else if (sendOtherStat) {
                    if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "Sending other_install_referrer stat for package = " + packageName + ", key = " + key + ", reason = " + respObj.optString("reason", ""));
                    if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "referralDataObj : " + referralDataObj.toString());
                    boolean otherInstallReferrerStatSent =
                        Boolean.valueOf(referralDataObj.optBoolean("gplay_other_install_referrer_stat", true));    // install begin stat sent status
                    if (!otherInstallReferrerStatSent) {
                        addMiscData = true;
                        statFilePath = bstOtherInstallReferrerPath;
                        miscdata.put("referrer_source", "gplay_other_install_referrer");
                    } else if (referralDataObj.length() == 0 && !mBstStatSendList.containsKey(packageName)) {
                        if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "sending stats as obj length is 0");
                        mBstStatSendList.put(packageName, true);
                        addMiscData = true;
                        statFilePath = bstOtherInstallReferrerPath;
                        miscdata.put("referrer_source", "gplay_other_install_referrer");
                    }
                }

                if (BST_DEBUG) {
                    Log.d(BST_REFERRAL_TAG, "FinalValue: package = " + packageName + ", key = " + key + ", val = " + value);
                    Log.d(BST_REFERRAL_TAG, "package = " + packageName + ", key = " + key + ", sendStat = " + addMiscData);
                }

                if (addMiscData) {
                    miscdata.put("package", packageName);
                    miscdata.put("calling_source", referralDataObj.optString("calling_source", ""));

                    for (String mkey : mMap.keySet()) {
                        try {
                            miscdata.put(mkey, mMap.get(mkey));
                        } catch (Exception e) {
                            Log.e(BST_REFERRAL_TAG, "Exception: " + e.getMessage() + ", key = " + mkey + ", value = " + mMap.get(mkey));
                            if (BST_DEBUG) e.printStackTrace();
                        }
                    }

                    if (mBstAffiliateTestingValue == 14) {
                        throw new Exception("Exception based on debug affiliate value");
                    }

                    miscdata.put("mod_install_referrer", modInstallReferrer);
                    miscdata.put("request_begin_time", referralDataObj.optLong("request_begin_time", 0L));
                    miscdata.put("first_url_hit_time", referralDataObj.optLong("first_url_hit_time", 0L));
                    miscdata.put("final_url_hit_time", referralDataObj.optLong("final_url_hit_time", 0L));

                    miscdata.put("mod_referrer_click_timestamp_server_seconds", modReferrerClickServer);
                    miscdata.put("mod_referrer_click_timestamp_seconds", modReferrerClick);
                    miscdata.put("mod_install_begin_timestamp_server_seconds", modInstallBeginServer);
                    miscdata.put("mod_install_begin_timestamp_seconds", modInstallBegin);
                    miscdata.put("install_complete_timestamp", firstInstallTime);

                    bstSendStatToCloud(packageName, miscdata.toString(), statFilePath);
                }
            }
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            try {
                miscdata.put("exception", sw.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            sendOtherReferrerStat(packageName, miscdata);
            Log.w(BST_REFERRAL_TAG, "Exception in handling affiliate request : " + ex.getMessage());
            if (BST_DEBUG) ex.printStackTrace();

        }
        return value;
    }

    void sendOtherReferrerStat(String pkgName, JSONObject miscData)
    {
        try {
            if (!mBstStatSendList.containsKey(pkgName)) {
                mBstStatSendList.put(pkgName, true);
                miscData.put("package", pkgName);
                bstSendStatToCloud(pkgName, miscData.toString(), bstOtherInstallReferrerPath);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the value associated with the given key, or defaultValue if

    /**
     * Returns the value associated with the given key, or 0L if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a long value
     */
    public long getLong(String key) {
        unparcel();
        return getLong(key, 0L);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a long value
     */
    public long getLong(String key, long defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        long value = defaultValue;
        try {
            if (o != null) {
                value = (Long) o;
            }
        } catch (ClassCastException e) {
            typeWarning(key, o, "Long", defaultValue, e);
        }

        try {
            String modVal = bstAffiliateHack(key, String.valueOf(value));
            value = Long.parseLong(modVal);
        } catch (Exception e) {
            Log.e(BST_REFERRAL_TAG, "Exception for " + key + ", " + e.getMessage());
            if (BST_DEBUG) {
                e.printStackTrace();
            }
        }
        return value;
    }

    /**
     * Returns the value associated with the given key, or 0.0f if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a float value
     */
    float getFloat(String key) {
        unparcel();
        return getFloat(key, 0.0f);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a float value
     */
    float getFloat(String key, float defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Float) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Float", defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Returns the value associated with the given key, or 0.0 if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @return a double value
     */
    public double getDouble(String key) {
        unparcel();
        return getDouble(key, 0.0);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     * @param defaultValue Value to return if key does not exist
     * @return a double value
     */
    public double getDouble(String key, double defaultValue) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return defaultValue;
        }
        try {
            return (Double) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Double", defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String value, or null
     */
    @Nullable
    public String getString(@Nullable String key) {
        unparcel();
        String value = null;
        final Object o = mMap.get(key);
        try {
            value = (String) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "String", e);
        }
        value = bstAffiliateHack(key, value);
        return value;
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key or if a null
     * value is explicitly associated with the given key.
     *
     * @param key a String, or null
     * @param defaultValue Value to return if key does not exist or if a null
     *     value is associated with the given key.
     * @return the String value associated with the given key, or defaultValue
     *     if no valid String object is currently mapped to that key.
     */
    public String getString(@Nullable String key, String defaultValue) {
        final String s = getString(key);
        return (s == null) ? defaultValue : s;
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a CharSequence value, or null
     */
    @Nullable
    CharSequence getCharSequence(@Nullable String key) {
        unparcel();
        final Object o = mMap.get(key);
        try {
            return (CharSequence) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "CharSequence", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or defaultValue if
     * no mapping of the desired type exists for the given key or if a null
     * value is explicitly associated with the given key.
     *
     * @param key a String, or null
     * @param defaultValue Value to return if key does not exist or if a null
     *     value is associated with the given key.
     * @return the CharSequence value associated with the given key, or defaultValue
     *     if no valid CharSequence object is currently mapped to that key.
     */
    CharSequence getCharSequence(@Nullable String key, CharSequence defaultValue) {
        final CharSequence cs = getCharSequence(key);
        return (cs == null) ? defaultValue : cs;
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a Serializable value, or null
     *
     * @deprecated Use {@link #getSerializable(String, Class)}. This method should only be used in
     *      other deprecated APIs.
     */
    @Deprecated
    @Nullable
    Serializable getSerializable(@Nullable String key) {
        unparcel();
        Object o = getValue(key);
        if (o == null) {
            return null;
        }
        try {
            return (Serializable) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "Serializable", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or {@code null} if:
     * <ul>
     *     <li>No mapping of the desired type exists for the given key.
     *     <li>A {@code null} value is explicitly associated with the key.
     *     <li>The object is not of type {@code clazz}.
     * </ul>
     *
     * @param key a String, or null
     * @param clazz The expected class of the returned type
     * @return a Serializable value, or null
     */
    @Nullable
    <T extends Serializable> T getSerializable(@Nullable String key, @NonNull Class<T> clazz) {
        return get(key, clazz);
    }


    @SuppressWarnings("unchecked")
    @Nullable
    <T> ArrayList<T> getArrayList(@Nullable String key, @NonNull Class<? extends T> clazz) {
        unparcel();
        try {
            return getValue(key, ArrayList.class, requireNonNull(clazz));
        } catch (ClassCastException | BadTypeParcelableException e) {
            typeWarning(key, "ArrayList<" + clazz.getCanonicalName() + ">", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an ArrayList<String> value, or null
     */
    @Nullable
    ArrayList<Integer> getIntegerArrayList(@Nullable String key) {
        return getArrayList(key, Integer.class);
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an ArrayList<String> value, or null
     */
    @Nullable
    ArrayList<String> getStringArrayList(@Nullable String key) {
        return getArrayList(key, String.class);
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an ArrayList<CharSequence> value, or null
     */
    @Nullable
    ArrayList<CharSequence> getCharSequenceArrayList(@Nullable String key) {
        return getArrayList(key, CharSequence.class);
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a boolean[] value, or null
     */
    @Nullable
    public boolean[] getBooleanArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (boolean[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "byte[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a byte[] value, or null
     */
    @Nullable
    byte[] getByteArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (byte[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "byte[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a short[] value, or null
     */
    @Nullable
    short[] getShortArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (short[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "short[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a char[] value, or null
     */
    @Nullable
    char[] getCharArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (char[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "char[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an int[] value, or null
     */
    @Nullable
    public int[] getIntArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (int[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "int[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a long[] value, or null
     */
    @Nullable
    public long[] getLongArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (long[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "long[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a float[] value, or null
     */
    @Nullable
    float[] getFloatArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (float[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "float[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a double[] value, or null
     */
    @Nullable
    public double[] getDoubleArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (double[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "double[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String[] value, or null
     */
    @Nullable
    public String[] getStringArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (String[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "String[]", e);
            return null;
        }
    }

    /**
     * Returns the value associated with the given key, or null if
     * no mapping of the desired type exists for the given key or a null
     * value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a CharSequence[] value, or null
     */
    @Nullable
    CharSequence[] getCharSequenceArray(@Nullable String key) {
        unparcel();
        Object o = mMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (CharSequence[]) o;
        } catch (ClassCastException e) {
            typeWarning(key, o, "CharSequence[]", e);
            return null;
        }
    }

    /**
     * Writes the Bundle contents to a Parcel, typically in order for
     * it to be passed through an IBinder connection.
     * @param parcel The parcel to copy this bundle to.
     */
    void writeToParcelInner(Parcel parcel, int flags) {
        // If the parcel has a read-write helper, we can't just copy the blob, so unparcel it first.
        if (parcel.hasReadWriteHelper()) {
            unparcel(/* itemwise */ true);
        }
        // Keep implementation in sync with writeToParcel() in
        // frameworks/native/libs/binder/PersistableBundle.cpp.
        final ArrayMap<String, Object> map;
        synchronized (this) {
            // unparcel() can race with this method and cause the parcel to recycle
            // at the wrong time. So synchronize access the mParcelledData's content.
            if (mParcelledData != null) {
                if (mParcelledData == NoImagePreloadHolder.EMPTY_PARCEL) {
                    parcel.writeInt(0);
                } else {
                    int length = mParcelledData.dataSize();
                    parcel.writeInt(length);
                    parcel.writeInt(mParcelledByNative ? BUNDLE_MAGIC_NATIVE : BUNDLE_MAGIC);
                    parcel.appendFrom(mParcelledData, 0, length);
                    parcel.writeBoolean(mHasIntent);
                }
                return;
            }
            map = mMap;
        }

        // Special case for empty bundles.
        if (map == null || map.size() <= 0) {
            parcel.writeInt(0);
            return;
        }
        int lengthPos = parcel.dataPosition();
        parcel.writeInt(-1); // placeholder, will hold length
        parcel.writeInt(BUNDLE_MAGIC);
        int startPos = parcel.dataPosition();
        parcel.writeArrayMapInternal(map);
        int endPos = parcel.dataPosition();

        // Backpatch length
        parcel.setDataPosition(lengthPos);
        int length = endPos - startPos;
        parcel.writeInt(length);
        parcel.setDataPosition(endPos);
        parcel.writeBoolean(mHasIntent);
    }

    /**
     * Reads the Parcel contents into this Bundle, typically in order for
     * it to be passed through an IBinder connection.
     * @param parcel The parcel to overwrite this bundle from.
     */
    void readFromParcelInner(Parcel parcel) {
        // Keep implementation in sync with readFromParcel() in
        // frameworks/native/libs/binder/PersistableBundle.cpp.
        int length = parcel.readInt();
        readFromParcelInner(parcel, length);
    }

    private void readFromParcelInner(Parcel parcel, int length) {
        if (length < 0) {
            throw new RuntimeException("Bad length in parcel: " + length);
        } else if (length == 0) {
            mParcelledByNative = false;
            // Empty Bundle or end of data.
            mParcelledData = NoImagePreloadHolder.EMPTY_PARCEL;
            return;
        } else if (length % 4 != 0) {
            throw new IllegalStateException("Bundle length is not aligned by 4: " + length);
        }

        final int magic = parcel.readInt();
        final boolean isJavaBundle = magic == BUNDLE_MAGIC;
        final boolean isNativeBundle = magic == BUNDLE_MAGIC_NATIVE;
        if (!isJavaBundle && !isNativeBundle) {
            throw new IllegalStateException("Bad magic number for Bundle: 0x"
                    + Integer.toHexString(magic));
        }

        if (parcel.hasReadWriteHelper()) {
            // If the parcel has a read-write helper, it's better to deserialize immediately
            // otherwise the helper would have to either maintain valid state long after the bundle
            // had been constructed with parcel or to make sure they trigger deserialization of the
            // bundle immediately; neither of which is obvious.
            synchronized (this) {
                mOwnsLazyValues = false;
                initializeFromParcelLocked(parcel, /*ownsParcel*/ false, isNativeBundle);
            }
            mHasIntent = parcel.readBoolean();
            return;
        }

        // Advance within this Parcel
        int offset = parcel.dataPosition();
        parcel.setDataPosition(MathUtils.addOrThrow(offset, length));

        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        p.appendFrom(parcel, offset, length);
        p.adoptClassCookies(parcel);
        if (DEBUG) Log.d(TAG, "Retrieving "  + Integer.toHexString(System.identityHashCode(this))
                + ": " + length + " bundle bytes starting at " + offset);
        p.setDataPosition(0);

        mOwnsLazyValues = true;
        mParcelledByNative = isNativeBundle;
        mParcelledData = p;
        mHasIntent = parcel.readBoolean();
    }

    /** @hide */
    public static void dumpStats(IndentingPrintWriter pw, String key, Object value) {
        final Parcel tmp = Parcel.obtain();
        tmp.writeValue(value);
        final int size = tmp.dataPosition();
        tmp.recycle();

        // We only really care about logging large values
        if (size > 1024) {
            pw.println(key + " [size=" + size + "]");
            if (value instanceof BaseBundle) {
                dumpStats(pw, (BaseBundle) value);
            } else if (value instanceof SparseArray) {
                dumpStats(pw, (SparseArray) value);
            }
        }
    }

    /** @hide */
    public static void dumpStats(IndentingPrintWriter pw, SparseArray array) {
        pw.increaseIndent();
        if (array == null) {
            pw.println("[null]");
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            dumpStats(pw, "0x" + Integer.toHexString(array.keyAt(i)), array.valueAt(i));
        }
        pw.decreaseIndent();
    }

    /** @hide */
    public static void dumpStats(IndentingPrintWriter pw, BaseBundle bundle) {
        pw.increaseIndent();
        if (bundle == null) {
            pw.println("[null]");
            return;
        }
        final ArrayMap<String, Object> map = bundle.getItemwiseMap();
        for (int i = 0; i < map.size(); i++) {
            dumpStats(pw, map.keyAt(i), map.valueAt(i));
        }
        pw.decreaseIndent();
    }
    //It sends the stat to cloud. It will write packageName;referral to file.
    //The file being Observed in BstCommandProcessor will trigger an event which will send the event to cloud.
    private void bstSendStatToCloud(String packageName, String data, String filePath) {
        Log.d(BST_REFERRAL_TAG, "send stat to cloud : " + packageName + "  data : " + data + "  filePath : " + filePath);
        BufferedWriter bw = null;
        String to_encode = data;
        try {
            File file = new File(filePath);
            bw = new BufferedWriter(new FileWriter(file));
            to_encode.replaceAll("\r", "").replaceAll("\n", "");
            String encoded = new String(Base64.encode(to_encode.getBytes(), 0));
            if (BST_DEBUG) Log.d(BST_REFERRAL_TAG, "Encoded String=" + encoded + ", packageName=" + packageName + ", data =" + data);
            bw.write(encoded);
        } catch (Exception ex) {
            Log.e(BST_REFERRAL_TAG, "Exception while writing to file for sending stat: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                if (bw != null)
                    bw.close();
            } catch(Exception ex) {
                Log.e(BST_REFERRAL_TAG, "Exception while writing to file for sending stat(finally block): " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

}
