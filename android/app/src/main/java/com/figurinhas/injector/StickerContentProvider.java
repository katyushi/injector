package com.figurinhas.injector;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class StickerContentProvider extends ContentProvider {
    private static final String AUTHORITY_SUFFIX = ".stickers";
    private static final String METADATA = "metadata";
    private static final String STICKERS = "stickers";
    private static final String STICKERS_ASSET = "stickers_asset";
    private static final int ALL_PACKS = 1;
    private static final int ONE_PACK = 2;
    private static final int PACK_STICKERS = 3;
    private static final int PACK_FILE = 4;

    private static final String PACK_ID = "sticker_pack_identifier";
    private static final String PACK_NAME = "sticker_pack_name";
    private static final String PACK_PUBLISHER = "sticker_pack_publisher";
    private static final String PACK_ICON = "sticker_pack_icon";
    private static final String ANDROID_LINK = "android_play_store_link";
    private static final String IOS_LINK = "ios_app_download_link";
    private static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
    private static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
    private static final String PRIVACY_WEBSITE = "privacy_policy_website";
    private static final String LICENSE_WEBSITE = "license_agreement_website";
    private static final String IMAGE_VERSION = "image_data_version";
    private static final String AVOID_CACHE = "whatsapp_will_not_cache_stickers";
    private static final String ANIMATED_PACK = "animated_sticker_pack";
    private static final String STICKER_FILE_NAME = "sticker_file_name";
    private static final String STICKER_EMOJI = "sticker_emoji";
    private static final String STICKER_ACCESSIBILITY = "sticker_accessibility_text";

    private UriMatcher matcher;
    private String authority;

    @Override
    public boolean onCreate() {
        authority = requireContext().getPackageName() + AUTHORITY_SUFFIX;
        matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(authority, METADATA, ALL_PACKS);
        matcher.addURI(authority, METADATA + "/*", ONE_PACK);
        matcher.addURI(authority, STICKERS + "/*", PACK_STICKERS);
        matcher.addURI(authority, STICKERS_ASSET + "/*/*", PACK_FILE);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int match = matcher.match(uri);
        try {
            if (match == ALL_PACKS || match == ONE_PACK) return metadataCursor(uri, match == ONE_PACK ? uri.getLastPathSegment() : null);
            if (match == PACK_STICKERS) return stickerCursor(uri, uri.getLastPathSegment());
        } catch (Exception error) {
            throw new IllegalStateException("Could not read sticker pack data", error);
        }
        throw new IllegalArgumentException("Unknown sticker provider URI: " + uri);
    }

    private Cursor metadataCursor(Uri uri, String onlyIdentifier) throws Exception {
        String[] columns = {PACK_ID, PACK_NAME, PACK_PUBLISHER, PACK_ICON, ANDROID_LINK, IOS_LINK, PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE, PRIVACY_WEBSITE, LICENSE_WEBSITE, IMAGE_VERSION, AVOID_CACHE, ANIMATED_PACK};
        MatrixCursor cursor = new MatrixCursor(columns);
        JSONArray packs = StickerStore.listPacks(requireContext());
        for (int index = 0; index < packs.length(); index++) {
            JSONObject pack = packs.getJSONObject(index);
            if (onlyIdentifier != null && !onlyIdentifier.equals(pack.optString("identifier"))) continue;
            cursor.addRow(new Object[]{
                    pack.optString("identifier"), pack.optString("name"), pack.optString("publisher"), pack.optString("tray_image_file"),
                    pack.optString("android_play_store_link"), pack.optString("ios_app_store_link"), pack.optString("publisher_email"),
                    pack.optString("publisher_website"), pack.optString("privacy_policy_website"), pack.optString("license_agreement_website"),
                    pack.optString("image_data_version", "1"), 0, pack.optBoolean("animated_sticker_pack", false) ? 1 : 0
            });
        }
        cursor.setNotificationUri(requireContext().getContentResolver(), uri);
        return cursor;
    }

    private Cursor stickerCursor(Uri uri, String identifier) throws Exception {
        MatrixCursor cursor = new MatrixCursor(new String[]{STICKER_FILE_NAME, STICKER_EMOJI, STICKER_ACCESSIBILITY});
        JSONObject pack = StickerStore.findPack(requireContext(), identifier);
        if (pack != null) {
            JSONArray stickers = pack.optJSONArray("stickers");
            if (stickers != null) {
                for (int index = 0; index < stickers.length(); index++) {
                    JSONObject sticker = stickers.getJSONObject(index);
                    JSONArray emojis = sticker.optJSONArray("emojis");
                    String[] values = new String[emojis == null ? 0 : emojis.length()];
                    for (int emojiIndex = 0; emojiIndex < values.length; emojiIndex++) values[emojiIndex] = emojis.optString(emojiIndex);
                    cursor.addRow(new Object[]{sticker.optString("image_file"), TextUtils.join(",", values), sticker.optString("accessibility_text")});
                }
            }
        }
        cursor.setNotificationUri(requireContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) {
        if (matcher.match(uri) != PACK_FILE) throw new IllegalArgumentException("Unknown sticker asset URI: " + uri);
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 3) throw new IllegalArgumentException("Sticker asset URI must have three path segments.");
        String identifier = segments.get(1);
        String fileName = segments.get(2);
        try {
            File file = StickerStore.getPackAsset(requireContext(), identifier, fileName);
            if (file == null) return null;
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(descriptor, 0, file.length());
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not open sticker asset", error);
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (matcher.match(uri)) {
            case ALL_PACKS: return "vnd.android.cursor.dir/vnd." + authority + ".metadata";
            case ONE_PACK: return "vnd.android.cursor.item/vnd." + authority + ".metadata";
            case PACK_STICKERS: return "vnd.android.cursor.dir/vnd." + authority + ".stickers";
            case PACK_FILE: return uri.getLastPathSegment() != null && uri.getLastPathSegment().toLowerCase().endsWith(".png") ? "image/png" : "image/webp";
            default: throw new IllegalArgumentException("Unknown sticker provider URI: " + uri);
        }
    }

    private android.content.Context requireContext() {
        android.content.Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider context is not ready.");
        return context;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Insert is not supported."); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Delete is not supported."); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Update is not supported."); }
}
