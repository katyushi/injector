package com.figurinhas.injector;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class StickerStore {
    private static final String STORE_DIR = "sticker-packs";
    private static final String CONTENTS_FILE = "contents.json";
    private static final long STATIC_LIMIT = 100L * 1024L;
    private static final long ANIMATED_LIMIT = 500L * 1024L;
    private static final long TRAY_LIMIT = 50L * 1024L;
    private static final int MAX_PACKS = 10;

    private StickerStore() {}

    static synchronized JSONObject readManifest(Context context) throws Exception {
        File file = new File(getRoot(context), CONTENTS_FILE);
        if (!file.isFile()) {
            JSONObject empty = new JSONObject();
            empty.put("android_play_store_link", "");
            empty.put("ios_app_store_link", "");
            empty.put("sticker_packs", new JSONArray());
            return empty;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            return new JSONObject(new String(readAll(input), "UTF-8"));
        }
    }

    static synchronized JSONArray listPacks(Context context) throws Exception {
        return readManifest(context).getJSONArray("sticker_packs");
    }

    static synchronized JSONObject findPack(Context context, String identifier) throws Exception {
        JSONArray packs = listPacks(context);
        for (int index = 0; index < packs.length(); index++) {
            JSONObject pack = packs.getJSONObject(index);
            if (identifier.equals(pack.optString("identifier"))) return pack;
        }
        return null;
    }

    static synchronized int importCollection(Context context, Uri treeUri) throws Exception {
        DocumentFile collection = DocumentFile.fromTreeUri(context, treeUri);
        if (collection == null || !collection.isDirectory()) throw new IllegalArgumentException("A pasta escolhida não está acessível.");

        DocumentFile contentsFile = collection.findFile(CONTENTS_FILE);
        if (contentsFile == null || !contentsFile.isFile()) throw new IllegalArgumentException("Não encontrei contents.json. Escolha a pasta collection-XX gerada pelo Figurinhas.");

        JSONObject sourceManifest;
        try (InputStream input = context.getContentResolver().openInputStream(contentsFile.getUri())) {
            if (input == null) throw new IllegalArgumentException("Não consegui ler contents.json.");
            sourceManifest = new JSONObject(new String(readAll(input), "UTF-8"));
        }
        JSONArray sourcePacks = sourceManifest.optJSONArray("sticker_packs");
        if (sourcePacks == null || sourcePacks.length() == 0 || sourcePacks.length() > MAX_PACKS) {
            throw new IllegalArgumentException("A collection precisa conter de 1 a 10 packs.");
        }

        JSONObject installedManifest = readManifest(context);
        JSONArray installedPacks = installedManifest.getJSONArray("sticker_packs");
        if (installedPacks.length() + sourcePacks.length() > MAX_PACKS) {
            throw new IllegalArgumentException("O injector comporta até 10 packs. Remova alguns antes de importar outra collection.");
        }

        List<PendingPack> pending = new ArrayList<>();
        for (int packIndex = 0; packIndex < sourcePacks.length(); packIndex++) {
            JSONObject sourcePack = sourcePacks.getJSONObject(packIndex);
            String sourceId = sourcePack.getString("identifier");
            if (sourceId.contains("/") || sourceId.contains("\\") || sourceId.contains("..")) {
                throw new IllegalArgumentException("Identificador de pack inválido.");
            }
            String name = sourcePack.optString("name", "").trim();
            String publisher = sourcePack.optString("publisher", "").trim();
            String trayName = sourcePack.optString("tray_image_file", "");
            JSONArray stickers = sourcePack.optJSONArray("stickers");
            boolean animated = sourcePack.optBoolean("animated_sticker_pack", false);
            if (name.isEmpty() || name.length() > 128 || publisher.isEmpty() || publisher.length() > 128) {
                throw new IllegalArgumentException("Um pack tem nome ou autor inválido.");
            }
            if (stickers == null || stickers.length() < 3 || stickers.length() > 30) {
                throw new IllegalArgumentException("Cada pack precisa ter de 3 a 30 stickers.");
            }
            if (!isSafeFileName(trayName)) throw new IllegalArgumentException("Nome de ícone inválido no pack “" + name + "”.");
            DocumentFile sourcePackDir = collection.findFile(sourceId);
            if (sourcePackDir == null || !sourcePackDir.isDirectory()) throw new IllegalArgumentException("Não encontrei os arquivos do pack “" + name + "”.");
            DocumentFile trayFile = sourcePackDir.findFile(trayName);
            validateImage(context, trayFile, TRAY_LIMIT, 96, false, "ícone do pack “" + name + "”");

            List<DocumentFile> sourceStickerFiles = new ArrayList<>();
            for (int stickerIndex = 0; stickerIndex < stickers.length(); stickerIndex++) {
                JSONObject sticker = stickers.getJSONObject(stickerIndex);
                String imageName = sticker.optString("image_file", "");
                if (!isSafeFileName(imageName) || !imageName.toLowerCase().endsWith(".webp")) {
                    throw new IllegalArgumentException("Nome de sticker inválido no pack “" + name + "”.");
                }
                DocumentFile sourceSticker = sourcePackDir.findFile(imageName);
                validateImage(context, sourceSticker, animated ? ANIMATED_LIMIT : STATIC_LIMIT, 512, animated, "sticker “" + imageName + "”");
                sourceStickerFiles.add(sourceSticker);
            }

            String newIdentifier = "p" + UUID.randomUUID().toString().replace("-", "");
            JSONObject installedPack = new JSONObject(sourcePack.toString());
            installedPack.put("identifier", newIdentifier);
            installedPack.put("image_data_version", Long.toString(System.currentTimeMillis()));
            pending.add(new PendingPack(installedPack, sourcePackDir, trayName, sourceStickerFiles, newIdentifier));
        }

        File root = getRoot(context);
        for (PendingPack item : pending) {
            File destination = new File(root, item.identifier);
            if (!destination.mkdirs() && !destination.isDirectory()) throw new IllegalStateException("Não consegui preparar o armazenamento do pack.");
            copyDocument(context, item.sourceDirectory.findFile(item.trayName), new File(destination, item.trayName));
            JSONArray installedStickers = item.pack.getJSONArray("stickers");
            for (int index = 0; index < item.stickerFiles.size(); index++) {
                JSONObject sticker = installedStickers.getJSONObject(index);
                String imageName = sticker.getString("image_file");
                copyDocument(context, item.stickerFiles.get(index), new File(destination, imageName));
            }
            installedPacks.put(item.pack);
        }

        installedManifest.put("sticker_packs", installedPacks);
        writeManifest(context, installedManifest);
        return pending.size();
    }

    static synchronized void removePack(Context context, String identifier) throws Exception {
        JSONArray current = listPacks(context);
        JSONArray kept = new JSONArray();
        boolean found = false;
        for (int index = 0; index < current.length(); index++) {
            JSONObject pack = current.getJSONObject(index);
            if (identifier.equals(pack.optString("identifier"))) found = true;
            else kept.put(pack);
        }
        if (!found) throw new IllegalArgumentException("Esse pack não está no injector.");
        JSONObject manifest = readManifest(context);
        manifest.put("sticker_packs", kept);
        writeManifest(context, manifest);
        deleteRecursively(new File(getRoot(context), identifier));
    }

    static synchronized File getPackAsset(Context context, String identifier, String fileName) throws Exception {
        JSONObject pack = findPack(context, identifier);
        if (pack == null || !isSafeFileName(fileName)) return null;
        boolean isTray = fileName.equals(pack.optString("tray_image_file"));
        boolean isSticker = false;
        JSONArray stickers = pack.optJSONArray("stickers");
        if (stickers != null) {
            for (int index = 0; index < stickers.length(); index++) {
                if (fileName.equals(stickers.getJSONObject(index).optString("image_file"))) {
                    isSticker = true;
                    break;
                }
            }
        }
        if (!isTray && !isSticker) return null;
        File file = new File(new File(getRoot(context), identifier), fileName).getCanonicalFile();
        if (!file.getPath().startsWith(getRoot(context).getCanonicalPath() + File.separator) || !file.isFile()) return null;
        return file;
    }

    private static void validateImage(Context context, DocumentFile file, long maxBytes, int expectedDimension, boolean animatedExpected, String label) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("Não encontrei o " + label + ".");
        if (file.length() <= 0 || file.length() > maxBytes) throw new IllegalArgumentException("O " + label + " ultrapassa o limite de tamanho.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(file.getUri())) {
            if (input == null) throw new IllegalArgumentException("Não consegui ler o " + label + ".");
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth != expectedDimension || options.outHeight != expectedDimension) {
            throw new IllegalArgumentException("O " + label + " precisa medir " + expectedDimension + " × " + expectedDimension + " px.");
        }
        if (expectedDimension == 512 && isAnimatedWebP(context, file) != animatedExpected) {
            throw new IllegalArgumentException("O pack mistura stickers estáticos e animados ou a marcação está incorreta.");
        }
    }

    private static boolean isAnimatedWebP(Context context, DocumentFile file) throws Exception {
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(file.getUri())) {
            if (input == null) return false;
            bytes = readAll(input);
        }
        if (bytes.length < 12 || !matches(bytes, 0, "RIFF") || !matches(bytes, 8, "WEBP")) return false;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            if (matches(bytes, offset, "ANIM") || matches(bytes, offset, "ANMF")) return true;
            long chunkSize = (bytes[offset + 4] & 0xffL)
                    | ((bytes[offset + 5] & 0xffL) << 8)
                    | ((bytes[offset + 6] & 0xffL) << 16)
                    | ((bytes[offset + 7] & 0xffL) << 24);
            long next = offset + 8L + chunkSize + (chunkSize & 1L);
            if (next <= offset || next > bytes.length) break;
            offset = (int) next;
        }
        return false;
    }

    private static boolean matches(byte[] bytes, int offset, String value) {
        if (offset < 0 || offset + value.length() > bytes.length) return false;
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) return false;
        }
        return true;
    }

    private static boolean isSafeFileName(String name) {
        return name != null && !name.isEmpty() && name.equals(new File(name).getName()) && !name.equals(".") && !name.equals("..") && !name.contains("/") && !name.contains("\\");
    }

    private static void copyDocument(Context context, DocumentFile source, File target) throws Exception {
        if (source == null || !source.isFile()) throw new IllegalArgumentException("Um arquivo do pack sumiu durante a importação.");
        try (InputStream input = context.getContentResolver().openInputStream(source.getUri()); FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IllegalArgumentException("Não consegui ler um arquivo do pack.");
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static File getRoot(Context context) throws Exception {
        File root = new File(context.getFilesDir(), STORE_DIR);
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Não consegui criar o armazenamento local.");
        return root.getCanonicalFile();
    }

    private static void writeManifest(Context context, JSONObject manifest) throws Exception {
        File root = getRoot(context);
        File temporary = new File(root, CONTENTS_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(manifest.toString().getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        }
        File target = new File(root, CONTENTS_FILE);
        if (target.exists() && !target.delete()) throw new IllegalStateException("Não consegui atualizar o catálogo de packs.");
        if (!temporary.renameTo(target)) throw new IllegalStateException("Não consegui salvar o catálogo de packs.");
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static final class PendingPack {
        final JSONObject pack;
        final DocumentFile sourceDirectory;
        final String trayName;
        final List<DocumentFile> stickerFiles;
        final String identifier;

        PendingPack(JSONObject pack, DocumentFile sourceDirectory, String trayName, List<DocumentFile> stickerFiles, String identifier) {
            this.pack = pack;
            this.sourceDirectory = sourceDirectory;
            this.trayName = trayName;
            this.stickerFiles = stickerFiles;
            this.identifier = identifier;
        }
    }
}
