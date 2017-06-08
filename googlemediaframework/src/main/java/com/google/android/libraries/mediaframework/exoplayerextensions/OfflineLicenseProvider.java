package com.google.android.libraries.mediaframework.exoplayerextensions;

import android.net.Uri;

/**
 * Created by Daniel Ramos on 6/8/2017.
 */

public interface OfflineLicenseProvider {

    byte[] licenseForVideoUri(Uri uri);
}
