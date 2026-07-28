
package com.bluestacks.internal;

import android.os.SystemProperties;
import java.util.Objects;

public class Sdk23 {
    private static final String TAG = "Sdk23";

    /** Value used for when a build property is unknown. */
    public static final String UNKNOWN = "unknown";

    /** Various version strings. */
    public static class VERSION {

        public static final String INCREMENTAL = getString("ro.build.version.incremental");

        public static final String RELEASE = getString("ro.build.version.release");

        public static final String BASE_OS = SystemProperties.get("ro.build.version.base_os", "");

        public static final String SECURITY_PATCH = SystemProperties.get(
                "ro.build.version.security_patch", "");

        // Android 6
        public static final String SDK = "23";

        public static final int SDK_INT = 23;

        public static final int PREVIEW_SDK_INT = SystemProperties.getInt(
                "ro.build.version.preview_sdk", 0);

        public static final String CODENAME = getString("ro.build.version.codename");

        private static final String[] ALL_CODENAMES
                = getStringList("ro.build.version.all_codenames", ",");

        /**
         * @hide
         */
        public static final String[] ACTIVE_CODENAMES = "REL".equals(ALL_CODENAMES[0])
                ? new String[0] : ALL_CODENAMES;

        public static final int RESOURCES_SDK_INT = SDK_INT + ACTIVE_CODENAMES.length;
    }

    private static String getString(String property) {
        return SystemProperties.get(property, UNKNOWN);
    }

    private static String[] getStringList(String property, String separator) {
        String value = SystemProperties.get(property);
        if (value.isEmpty()) {
            return new String[0];
        } else {
            return value.split(separator);
        }
    }
}
