package com.calendarioanno.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarAlerts;

/**
 * Promemoria: il calendario di sistema manda EVENT_REMINDER quando scatta un promemoria di un
 * evento (di qualsiasi app); qui si leggono gli avvisi di quell'istante e si mostrano le notifiche.
 * Gestisce anche "rimanda" e la notifica riproposta.
 */
public class RicevitorePromemoria extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Esportato per forza (EVENT_REMINDER arriva dal provider calendario): niente azioni
        // interne qui, altrimenti un'altra app potrebbe forgiarle. Quelle stanno in RicevitoreAzioni.
        if (!CalendarContract.ACTION_EVENT_REMINDER.equals(intent.getAction())) return;
        if (!Preferenze.notificheAttive(context)) return;
        PendingResult risultato = goAsync();
        new Thread(() -> {
            try {
                avvisiDi(context, intent);
            } finally {
                risultato.finish();
            }
        }).start();
    }

    /** Tutti gli avvisi programmati per l'istante del promemoria (piu' eventi possono scattare insieme). */
    private static void avvisiDi(Context c, Intent intent) {
        long istante = intent.getLongExtra(CalendarAlerts.ALARM_TIME, -1);
        Uri dati = intent.getData();
        if (istante < 0 && dati != null && dati.getLastPathSegment() != null) {
            try {
                istante = Long.parseLong(dati.getLastPathSegment());
            } catch (NumberFormatException ignorata) {
                return;
            }
        }
        if (istante < 0) return;
        String[] proiezione = {CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END, CalendarAlerts.STATE};
        try (Cursor cur = c.getContentResolver().query(CalendarAlerts.CONTENT_URI, proiezione,
                CalendarAlerts.ALARM_TIME + "=?", new String[]{String.valueOf(istante)}, null)) {
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(3) == CalendarAlerts.STATE_DISMISSED) continue;
                Promemoria.notifica(c, cur.getLong(0), cur.getLong(1), cur.getLong(2));
            }
        } catch (SecurityException ignorata) {
            // senza permesso del calendario non c'e' niente da mostrare
        }
    }
}
