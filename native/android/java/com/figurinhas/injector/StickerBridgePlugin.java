package com.figurinhas.injector;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import androidx.activity.result.ActivityResult;

import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "StickerBridge")
public class StickerBridgePlugin extends Plugin {
    @PluginMethod
    public void importCollection(PluginCall call) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        picker.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, picker, "collectionPicked");
    }

    @ActivityCallback
    private void collectionPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            JSObject error = new JSObject();
            error.put("code", "PICKER_CANCELLED");
            call.reject("Seleção de pasta cancelada.", "PICKER_CANCELLED", null, error);
            return;
        }

        Uri treeUri = result.getData().getData();
        if (treeUri == null) {
            call.reject("Não recebi uma pasta válida.");
            return;
        }
        try {
            int imported = StickerStore.importCollection(getContext(), treeUri);
            Uri metadata = Uri.parse("content://" + getContext().getPackageName() + ".stickers/metadata");
            getContext().getContentResolver().notifyChange(metadata, null);
            JSObject response = new JSObject();
            response.put("imported", imported);
            call.resolve(response);
        } catch (Exception error) {
            call.reject(error.getMessage() == null ? "Falha ao importar a collection." : error.getMessage(), "IMPORT_FAILED", error);
        }
    }

    @PluginMethod
    public void listPacks(PluginCall call) {
        try {
            JSONArray stored = StickerStore.listPacks(getContext());
            JSArray packs = new JSArray();
            for (int index = 0; index < stored.length(); index++) {
                JSONObject item = stored.getJSONObject(index);
                JSObject pack = new JSObject();
                pack.put("identifier", item.getString("identifier"));
                pack.put("name", item.getString("name"));
                pack.put("publisher", item.optString("publisher", "Figurinhas"));
                pack.put("animated", item.optBoolean("animated_sticker_pack", false));
                pack.put("stickerCount", item.getJSONArray("stickers").length());
                packs.put(pack);
            }
            JSObject response = new JSObject();
            response.put("packs", packs);
            call.resolve(response);
        } catch (Exception error) {
            call.reject("Não consegui ler os packs importados.", "STORE_READ_FAILED", error);
        }
    }

    @PluginMethod
    public void addPack(PluginCall call) {
        String identifier = call.getString("identifier");
        if (identifier == null || identifier.isEmpty()) {
            call.reject("Pack sem identificador.");
            return;
        }
        try {
            JSONObject pack = StickerStore.findPack(getContext(), identifier);
            if (pack == null) {
                call.reject("Esse pack não está importado no injector.");
                return;
            }
            Intent addPack = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
            addPack.putExtra("sticker_pack_id", identifier);
            addPack.putExtra("sticker_pack_authority", getContext().getPackageName() + ".stickers");
            addPack.putExtra("sticker_pack_name", pack.getString("name"));
            if (getContext().getPackageManager().queryIntentActivities(addPack, 0).isEmpty()) {
                call.reject("Não encontrei o WhatsApp ou WhatsApp Business neste dispositivo.", "WHATSAPP_NOT_FOUND");
                return;
            }
            Intent chooser = Intent.createChooser(addPack, "Adicionar pack ao WhatsApp");
            getActivity().startActivity(chooser);
            call.resolve();
        } catch (ActivityNotFoundException error) {
            call.reject("Não encontrei um WhatsApp compatível neste dispositivo.", "WHATSAPP_NOT_FOUND", error);
        } catch (Exception error) {
            call.reject(error.getMessage() == null ? "Não consegui abrir o WhatsApp." : error.getMessage(), "ADD_FAILED", error);
        }
    }

    @PluginMethod
    public void removePack(PluginCall call) {
        String identifier = call.getString("identifier");
        if (identifier == null || identifier.isEmpty()) {
            call.reject("Pack sem identificador.");
            return;
        }
        try {
            StickerStore.removePack(getContext(), identifier);
            Uri metadata = Uri.parse("content://" + getContext().getPackageName() + ".stickers/metadata");
            getContext().getContentResolver().notifyChange(metadata, null);
            call.resolve();
        } catch (Exception error) {
            call.reject(error.getMessage() == null ? "Não consegui remover esse pack." : error.getMessage(), "REMOVE_FAILED", error);
        }
    }
}
