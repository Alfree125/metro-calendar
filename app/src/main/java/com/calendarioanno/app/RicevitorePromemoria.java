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
        String azione = intent.getAction();
        if (azione == null) return;
        PendingResult risultato = goAsync();
        new Thread(() -> {
            try {
                gestisci(context, intent, azione);
            } finally {
                risultato.finish();
            }
        }).start();
    }

    private static void gestisci(Context c, Intent intent, String azione) {
        long idEvento = intent.getLongExtra(Promemoria.EXTRA_EVENTO, -1);
        long inizio = intent.getLongExtra(Promemoria.EXTRA_INIZIO, 0);
        long fine = intent.getLongExtra(Promemoria.EXTRA_FINE, 0);
        switch (azione) {
            case Promemoria.AZIONE_RIMANDA:
                Promemoria.rimanda(c, idEvento, inizio, fine);
                return;
            case Promemoria.AZIONE_RIPROPONI:
                Promemoria.notifica(c, idEvento, inizio, fine);
                return;
            case CalendarContract.ACTION_EVENT_REMINDER:
                if (Preferenze.notificheAttive(c)) avvisiDi(c, intent);
                return;
            default:
                break;
        }
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
