package com.calendarioanno.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Base dei tre widget: disegna in background e tiene attivi gli aggiornamenti. */
public abstract class WidgetTabellone extends AppWidgetProvider {

    abstract AggiornaWidget.Tipo tipo();

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        PendingResult risultato = goAsync();
        new Thread(() -> {
            try {
                for (int id : ids) AggiornaWidget.aggiorna(context, mgr, id, tipo());
                AggiornaWidget.programma(context);
            } finally {
                risultato.finish();
            }
        }).start();
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) OpzioniWidget.elimina(context, id);
    }

    @Override
    public void onEnabled(Context context) {
        AggiornaWidget.programma(context);
    }

    @Override
    public void onDisabled(Context context) {
        // Se era l'ultimo widget in assoluto, programma() ferma gli aggiornamenti
        AggiornaWidget.programma(context);
    }
}
