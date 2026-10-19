package com.calendarioanno.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Icone dell'app. Android non permette di cambiare icona e nome a piacere: l'icona nell'elenco
 * app si sceglie tra varianti gia' pronte (activity-alias), mentre nome libero e foto vanno
 * su un'icona aggiunta alla home (shortcut fissato).
 */
final class Icone {

    static final String PREDEFINITA = "metro";
    static final String FOTO = "foto";
    static final String[] NOMI = {"metro", "live", "led", "a", "express", "carta"};
    private static final int[] RISORSE = {
            R.mipmap.ic_launcher, R.mipmap.ic_launcher_live, R.mipmap.ic_launcher_led,
            R.mipmap.ic_launcher_a, R.mipmap.ic_launcher_express, R.mipmap.ic_launcher_carta,
    };
    private static final String FILE_FOTO = "icona_foto.png";
    private static final int LATO_FOTO = 432; // 108dp a xxxhdpi, come le icone adattive

    private Icone() {
    }

    static int risorsa(String nome) {
        for (int k = 0; k < NOMI.length; k++) if (NOMI[k].equals(nome)) return RISORSE[k];
        return RISORSE[0];
    }

    private static ComponentName alias(Context c, String nome) {
        return new ComponentName(c.getPackageName(), c.getPackageName() + ".Icona_" + nome);
    }

    /** Attiva l'alias con l'icona scelta e spegne gli altri (prima accende, poi spegne: mai zero icone). */
    static void applica(Context c, String nome) {
        PackageManager pm = c.getPackageManager();
        pm.setComponentEnabledSetting(alias(c, nome),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (String altro : NOMI) {
            if (altro.equals(nome)) continue;
            pm.setComponentEnabledSetting(alias(c, altro),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    static File fileFoto(Context c) {
        return new File(c.getFilesDir(), FILE_FOTO);
    }

    static Bitmap foto(Context c) {
        File f = fileFoto(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    /** Ritaglia al centro un quadrato della foto scelta e lo salva, pronto per l'icona. */
    static boolean salvaFoto(Context c, InputStream in) throws IOException {
        // InputStream.readAllBytes() esiste solo da Android 13
        java.io.ByteArrayOutputStream tutto = new java.io.ByteArrayOutputStream();
        byte[] blocco = new byte[16 * 1024];
        for (int letti; (letti = in.read(blocco)) != -1; ) tutto.write(blocco, 0, letti);
        byte[] dati = tutto.toByteArray();
        BitmapFactory.Options misure = new BitmapFactory.Options();
        misure.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(dati, 0, dati.length, misure);
        int lato = Math.min(misure.outWidth, misure.outHeight);
        if (lato <= 0) return false;
        BitmapFactory.Options opzioni = new BitmapFactory.Options();
        opzioni.inSampleSize = Math.max(1, Integer.highestOneBit(lato / LATO_FOTO));
        Bitmap intera = BitmapFactory.decodeByteArray(dati, 0, dati.length, opzioni);
        if (intera == null) return false;
        int q = Math.min(intera.getWidth(), intera.getHeight());
        Bitmap quadrata = Bitmap.createBitmap(intera, (intera.getWidth() - q) / 2, (intera.getHeight() - q) / 2, q, q);
        Bitmap finale = Bitmap.createScaledBitmap(quadrata, LATO_FOTO, LATO_FOTO, true);
        try (FileOutputStream out = new FileOutputStream(fileFoto(c))) {
            finale.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return true;
    }

    /** Chiede al launcher di aggiungere un'icona con nome e immagine scelti. */
    static boolean aggiungiAllaHome(Context c, String nome, String icona) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(c)) return false;
        Bitmap foto = FOTO.equals(icona) ? foto(c) : null;
        IconCompat immagine = foto != null
                ? IconCompat.createWithAdaptiveBitmap(foto)
                : IconCompat.createWithResource(c, risorsa(icona));
        Intent apri = new Intent(Intent.ACTION_MAIN).setClass(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ShortcutInfoCompat scorciatoia = new ShortcutInfoCompat.Builder(c, "home_" + System.currentTimeMillis())
                .setShortLabel(nome)
                .setLongLabel(nome)
                .setIcon(immagine)
                .setIntent(apri)
                .build();
        return ShortcutManagerCompat.requestPinShortcut(c, scorciatoia, null);
    }
}
