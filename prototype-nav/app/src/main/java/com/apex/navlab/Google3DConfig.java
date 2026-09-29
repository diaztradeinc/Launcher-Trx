package com.apex.navlab;

import com.google.android.gms.maps3d.Map3DInitConfig;
import com.google.android.gms.maps3d.model.Map3DMode;
import java.util.Locale;

/** Avoid the absent Kotlin create$default bridge in Maps 3D SDK 0.2.2. */
final class Google3DConfig {
    private Google3DConfig() {}

    static Map3DInitConfig create(double latitude, double longitude) {
        return create(latitude, longitude, Map3DMode.HYBRID);
    }

    static Map3DInitConfig create(double latitude, double longitude, @Map3DMode int mode) {
        Locale locale = Locale.getDefault();
        return Map3DInitConfig.create(
                latitude, longitude, 0.0,
                0.0, 45.0, 0.0, 1800.0,
                0.0, 63170000.0,
                0.0, 360.0, 0.0, 90.0,
                null, mode, null,
                locale.getLanguage().isEmpty() ? "en" : locale.getLanguage(),
                locale.getCountry().isEmpty() ? "US" : locale.getCountry());
    }
}
