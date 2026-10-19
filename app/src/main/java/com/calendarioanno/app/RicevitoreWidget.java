package com.calendarioanno.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riceve il battito del minuto (e i cambi di ora o fuso orario) e ridisegna i widget. */
public class RicevitoreWidget extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult risultato = goAsync();
        new Thread(() -> {
            try {
                AggiornaWidget.aggiornaTutti(context);
                if (!AggiornaWidget.AZIONE_MINUTO.equals(intent.getAction())) AggiornaWidget.programma(context);
            } finally {
                risultato.finish();
            }
        }).start();
    }
}
