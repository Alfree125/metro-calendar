package com.calendarioanno.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Snooze e notifica riproposta. Receiver separato e NON esportato: i PendingIntent dell'app lo
 * chiamano per classe, ma un'altra app non può forgiare queste azioni (a differenza di
 * {@link RicevitorePromemoria}, esportato per forza per ricevere EVENT_REMINDER dal sistema).
 */
public class RicevitoreAzioni extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String azione = intent.getAction();
        if (azione == null) return;
        long idEvento = intent.getLongExtra(Promemoria.EXTRA_EVENTO, -1);
        long inizio = intent.getLongExtra(Promemoria.EXTRA_INIZIO, 0);
        long fine = intent.getLongExtra(Promemoria.EXTRA_FINE, 0);
        PendingResult risultato = goAsync();
        new Thread(() -> {
            try {
                if (Promemoria.AZIONE_RIMANDA.equals(azione)) {
                    Promemoria.rimanda(context, idEvento, inizio, fine);
                } else if (Promemoria.AZIONE_RIPROPONI.equals(azione)) {
                    Promemoria.notifica(context, idEvento, inizio, fine);
                }
            } finally {
                risultato.finish();
            }
        }).start();
    }
}
