package com.calendarioanno.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.TextUtils;

import androidx.core.os.ConfigurationCompat;

import java.util.Locale;

/** Preferenze dell'app: nome personalizzato, icona, lingua. */
final class Preferenze {

    static final String LINGUA_INGLESE = "en";
    static final String LINGUA_ITALIANO = "it";
    static final String LINGUA_SISTEMA = "sistema";

    private static final String NOME_FILE = "anno";
    private static final String CHIAVE_NOME_APP = "nome_app";
    private static final String CHIAVE_ICONA = "icona";
    private static final String CHIAVE_LINGUA = "lingua";
    private static final String CHIAVE_NOTIFICHE = "notifiche_promemoria";
    private static final String CHIAVE_PROMEMORIA = "promemoria_predefinito";

    private Preferenze() {
    }

    static SharedPreferences di(Context c) {
        return c.getSharedPreferences(NOME_FILE, Context.MODE_PRIVATE);
    }

    /** Nome scelto dall'utente, altrimenti "Metro calendar". */
    static String nomeApp(Context c) {
        String nome = di(c).getString(CHIAVE_NOME_APP, null);
        return TextUtils.isEmpty(nome) ? c.getString(R.string.app_name) : nome;
    }

    static void impostaNomeApp(Context c, String nome) {
        di(c).edit().putString(CHIAVE_NOME_APP, nome == null ? null : nome.trim()).apply();
    }

    /** Notifiche di promemoria dell'app (si possono spegnere se arrivano gia' da Google Calendar). */
    static boolean notificheAttive(Context c) {
        return di(c).getBoolean(CHIAVE_NOTIFICHE, true);
    }

    static void impostaNotifiche(Context c, boolean attive) {
        di(c).edit().putBoolean(CHIAVE_NOTIFICHE, attive).apply();
    }

    /** Minuti di anticipo del promemoria dei nuovi eventi, -1 per nessuno. */
    static int promemoriaPredefinito(Context c) {
        return di(c).getInt(CHIAVE_PROMEMORIA, 10);
    }

    static void impostaPromemoriaPredefinito(Context c, int minuti) {
        di(c).edit().putInt(CHIAVE_PROMEMORIA, minuti).apply();
    }

    static String icona(Context c) {
        return di(c).getString(CHIAVE_ICONA, Icone.PREDEFINITA);
    }

    static void impostaIcona(Context c, String icona) {
        di(c).edit().putString(CHIAVE_ICONA, icona).apply();
    }

    /** Come nel video la lingua predefinita e' l'inglese. */
    static String lingua(Context c) {
        return di(c).getString(CHIAVE_LINGUA, LINGUA_INGLESE);
    }

    static void impostaLingua(Context c, String lingua) {
        di(c).edit().putString(CHIAVE_LINGUA, lingua).commit();
    }

    /** Contesto con la lingua scelta nell'app (anche per i widget, che non passano dalle activity). */
    static Context conLingua(Context base) {
        String lingua = lingua(base);
        if (LINGUA_SISTEMA.equals(lingua)) {
            Locale.setDefault(ConfigurationCompat.getLocales(base.getResources().getConfiguration()).get(0));
            return base;
        }
        Locale locale = Locale.forLanguageTag(lingua);
        // Anche i selettori di data e ora di Material usano Locale.getDefault()
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    static Locale locale(Context c) {
        return ConfigurationCompat.getLocales(c.getResources().getConfiguration()).get(0);
    }
}
