package com.example.modernbookshelf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Shares only the downloaded update APK with the system package installer. */
public final class UpdateFileProvider extends ContentProvider {
    static final String AUTHORITY = "com.example.modernbookshelf.updatefile";

    static Uri apkUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("update.apk").build();
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"update.apk".equals(uri.getLastPathSegment()) || getContext() == null) throw new FileNotFoundException();
        File apk = new File(getContext().getCacheDir(), "update.apk");
        if (!apk.isFile()) throw new FileNotFoundException(apk.getAbsolutePath());
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
}
