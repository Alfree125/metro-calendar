package com.calendarioanno.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Disegna i widget a tabellone LED del video, secondo le opzioni di ciascuno, e li tiene
 * aggiornati: ogni minuto (allarme non "sveglia", quindi solo a schermo acceso) e quando
 * cambia il calendario (JobScheduler).
 */
final class AggiornaWidget {

    enum Tipo {
        PICCOLO(WidgetPiccolo.class, R.string.widget_piccolo, 8),
        MEDIO(WidgetMedio.class, R.string.widget_medio, 8),
        GRANDE(WidgetGrande.class, R.string.widget_grande, 8),
        /** 2x4: tutta la giornata in una colonna, righe su due livelli. */
        GIORNATA(WidgetGiornata.class, R.string.widget_giornata, 10),
        /** 4x2: tutta la giornata in largo, una riga per evento. */
        GIORNATA_LARGA(WidgetGiornataLarga.class, R.string.widget_giornata_larga, 6);

        final Class<?> provider;
        final int nome;
        final int maxRighe;

        Tipo(Class<?> provider, int nome, int maxRighe) {
            this.provider = provider;
            this.nome = nome;
            this.maxRighe = maxRighe;
        }

        /** Il formato di un widget gia' sulla home, dal suo provider. */
        static Tipo di(Context c, int idWidget) {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(idWidget);
            if (info != null) {
                for (Tipo t : values()) {
                    if (t.provider.getName().equals(info.provider.getClassName())) return t;
                }
            }
            return MEDIO;
        }
    }

    static final String AZIONE_MINUTO = "com.calendarioanno.app.AGGIORNA_WIDGET";
    private static final int ID_LAVORO = 1;
    private static final int[] RIGHE = {R.id.w_r0, R.id.w_r1, R.id.w_r2, R.id.w_r3, R.id.w_r4, R.id.w_r5, R.id.w_r6, R.id.w_r7};
    private static final int[] PALLINI = {R.id.w_r0_p, R.id.w_r1_p, R.id.w_r2_p, R.id.w_r3_p, R.id.w_r4_p, R.id.w_r5_p, R.id.w_r6_p, R.id.w_r7_p};
    private static final int[] COLONNE = {R.id.w_r0_c, R.id.w_r1_c, R.id.w_r2_c, R.id.w_r3_c, R.id.w_r4_c, R.id.w_r5_c, R.id.w_r6_c, R.id.w_r7_c};
    private static final int[] TITOLI = {R.id.w_r0_t, R.id.w_r1_t, R.id.w_r2_t, R.id.w_r3_t, R.id.w_r4_t, R.id.w_r5_t, R.id.w_r6_t, R.id.w_r7_t};
    private static final int[] MINUTI = {R.id.w_r0_m, R.id.w_r1_m, R.id.w_r2_m, R.id.w_r3_m, R.id.w_r4_m, R.id.w_r5_m, R.id.w_r6_m, R.id.w_r7_m};
    private static final int[] INTESTAZIONE = {R.id.w_h_ln, R.id.w_h_ev, R.id.w_h_min};
    private static final int[] G_RIGHE = {R.id.g_r0, R.id.g_r1, R.id.g_r2, R.id.g_r3, R.id.g_r4, R.id.g_r5, R.id.g_r6, R.id.g_r7, R.id.g_r8, R.id.g_r9};
    private static final int[] G_COLONNE = {R.id.g_r0_c, R.id.g_r1_c, R.id.g_r2_c, R.id.g_r3_c, R.id.g_r4_c, R.id.g_r5_c, R.id.g_r6_c, R.id.g_r7_c, R.id.g_r8_c, R.id.g_r9_c};
    private static final int[] G_PALLINI = {R.id.g_r0_p, R.id.g_r1_p, R.id.g_r2_p, R.id.g_r3_p, R.id.g_r4_p, R.id.g_r5_p, R.id.g_r6_p, R.id.g_r7_p, R.id.g_r8_p, R.id.g_r9_p};
    private static final int[] G_ORARI = {R.id.g_r0_o, R.id.g_r1_o, R.id.g_r2_o, R.id.g_r3_o, R.id.g_r4_o, R.id.g_r5_o, R.id.g_r6_o, R.id.g_r7_o, R.id.g_r8_o, R.id.g_r9_o};
    private static final int[] G_STATI = {R.id.g_r0_s, R.id.g_r1_s, R.id.g_r2_s, R.id.g_r3_s, R.id.g_r4_s, R.id.g_r5_s, R.id.g_r6_s, R.id.g_r7_s, R.id.g_r8_s, R.id.g_r9_s};
    private static final int[] G_TITOLI = {R.id.g_r0_t, R.id.g_r1_t, R.id.g_r2_t, R.id.g_r3_t, R.id.g_r4_t, R.id.g_r5_t, R.id.g_r6_t, R.id.g_r7_t, R.id.g_r8_t, R.id.g_r9_t};

    private AggiornaWidget() {
    }

    /** Dall'app: ridisegna tutti i widget in background (dopo un salvataggio, un cambio di linea...). */
    static void richiedi(Context c) {
        Context app = c.getApplicationContext();
        new Thread(() -> aggiornaTutti(app)).start();
    }

    static void aggiornaTutti(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        for (Tipo t : Tipo.values()) {
            for (int id : mgr.getAppWidgetIds(new ComponentName(c, t.provider))) aggiorna(c, mgr, id, t);
        }
    }

    /** Tutti i widget sulla home, di ogni formato. */
    static List<int[]> elenco(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        List<int[]> risultato = new ArrayList<>();
        for (Tipo t : Tipo.values()) {
            for (int id : mgr.getAppWidgetIds(new ComponentName(c, t.provider))) risultato.add(new int[]{id, t.ordinal()});
        }
        return risultato;
    }

    static boolean ciSonoWidget(Context c) {
        return !elenco(c).isEmpty();
    }

    static void aggiorna(Context base, AppWidgetManager mgr, int idWidget, Tipo tipo) {
        RemoteViews rv = costruisci(base, idWidget, tipo, OpzioniWidget.carica(base, idWidget, tipo));
        if (rv != null) mgr.updateAppWidget(idWidget, rv);
    }

    /** Il widget disegnato con le opzioni date (usato anche per l'anteprima della personalizzazione). */
    static RemoteViews costruisci(Context base, int idWidget, Tipo tipo, OpzioniWidget o) {
        if (tipo == Tipo.GIORNATA || tipo == Tipo.GIORNATA_LARGA) return costruisciGiornata(base, idWidget, tipo, o);
        Context c = Preferenze.conLingua(base);
        Locale locale = Preferenze.locale(c);
        float f = o.fattore();
        int testo = o.colore();
        int verde = ContextCompat.getColor(c, R.color.led_verde);
        // Su testo verde il LIVE verde non si distinguerebbe
        int coloreLive = testo == verde ? 0xFFFFFFFF : verde;

        RemoteViews rv = new RemoteViews(base.getPackageName(), R.layout.widget_tabellone);
        rv.setInt(R.id.w_sfondo, "setColorFilter", OpzioniWidget.COLORI_SFONDO[o.coloreSfondo]);
        rv.setInt(R.id.w_sfondo, "setImageAlpha", Math.round(o.opacita * 255 / 100f));
        rv.setOnClickPendingIntent(R.id.w_radice, apri(base, idWidget * 16 + 15, new Intent(base, MainActivity.class)));

        rv.setViewVisibility(R.id.w_data, o.data ? View.VISIBLE : View.GONE);
        rv.setTextViewTextSize(R.id.w_data, TypedValue.COMPLEX_UNIT_SP, 20 * f);
        rv.setTextViewText(R.id.w_data, LocalDate.now()
                .format(DateTimeFormatter.ofPattern(c.getString(R.string.formato_breve), locale))
                .toUpperCase(locale).replace(".", ""));
        for (int id : INTESTAZIONE) {
            rv.setTextColor(id, testo);
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, 10 * f);
        }
        rv.setViewVisibility(R.id.w_h_ln, o.pallini ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.w_piede, o.piede ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_nome, Preferenze.nomeApp(c));
        rv.setTextViewTextSize(R.id.w_vuoto, TypedValue.COMPLEX_UNIT_SP, 12 * f);

        if (ContextCompat.checkSelfPermission(base, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            for (int riga : RIGHE) rv.setViewVisibility(riga, View.GONE);
            rv.setTextViewText(R.id.w_vuoto, c.getString(R.string.widget_permesso));
            rv.setViewVisibility(R.id.w_vuoto, View.VISIBLE);
            rv.setTextViewText(R.id.w_altri, "");
            return rv;
        }

        List<Calendario.Istanza> partenze;
        Map<Long, Linee.Linea> linee;
        try {
            linee = Linee.mappa(Linee.da(c, Calendario.calendari(c.getContentResolver(), false)));
            partenze = prossimePartenze(c, o.tuttoGiorno);
        } catch (SecurityException e) {
            return null;
        }

        long adesso = System.currentTimeMillis();
        int lato = Math.round((13 * f + 4) * c.getResources().getDisplayMetrics().density);
        for (int k = 0; k < RIGHE.length; k++) {
            if (k >= o.righe || k >= partenze.size()) {
                rv.setViewVisibility(RIGHE[k], View.GONE);
                continue;
            }
            Calendario.Istanza i = partenze.get(k);
            Linee.Linea l = linee.get(i.idCalendario);
            int colore = l != null ? l.colore : i.colore;
            String codice = l != null ? l.codice : DrawablePallino.iniziale(i.nomeCalendario);
            boolean inCorso = !i.tuttoIlGiorno && i.inizio <= adesso;
            String titolo = TextUtils.isEmpty(i.titolo) ? c.getString(R.string.senza_titolo) : i.titolo;
            String minuti = i.tuttoIlGiorno ? c.getString(R.string.widget_giorno)
                    : inCorso ? c.getString(R.string.live) : String.valueOf((i.inizio - adesso + 59_999) / 60_000);

            rv.setViewVisibility(RIGHE[k], View.VISIBLE);
            if (o.pallini) rv.setImageViewBitmap(PALLINI[k], DrawablePallino.bitmap(colore, codice, lato));
            rv.setTextViewText(TITOLI[k], titolo.toUpperCase(locale));
            rv.setTextColor(TITOLI[k], testo);
            rv.setTextViewTextSize(TITOLI[k], TypedValue.COMPLEX_UNIT_SP, 13 * f);
            rv.setTextViewText(MINUTI[k], minuti.toUpperCase(locale));
            rv.setTextColor(MINUTI[k], inCorso ? coloreLive : testo);
            rv.setTextViewTextSize(MINUTI[k], TypedValue.COMPLEX_UNIT_SP, 13 * f);
            rv.setOnClickPendingIntent(RIGHE[k], apri(base, idWidget * 16 + k,
                    new Intent(base, EventoActivity.class).putExtra(EventoActivity.EXTRA_ID_EVENTO, i.idEvento)));
        }
        // Senza pallini sparisce la colonna LN, sia nell'intestazione sia nelle righe
        for (int colonna : COLONNE) rv.setViewVisibility(colonna, o.pallini ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_vuoto, c.getString(R.string.widget_vuoto));
        rv.setViewVisibility(R.id.w_vuoto, partenze.isEmpty() ? View.VISIBLE : View.GONE);
        int altri = partenze.size() - o.righe;
        rv.setTextViewText(R.id.w_altri, altri > 0 ? c.getString(R.string.widget_altri, altri) : "");
        return rv;
    }

    /**
     * Il widget 2x4 con tutta la giornata: orario, pallino e stato (LIVE, minuti, partito) sopra,
     * titolo sotto. Se gli eventi sono piu' delle righe si parte dall'ultimo gia' partito,
     * cosi' le prossime partenze restano sempre in vista.
     */
    private static RemoteViews costruisciGiornata(Context base, int idWidget, Tipo tipo, OpzioniWidget o) {
        boolean stretto = tipo == Tipo.GIORNATA;
        Context c = Preferenze.conLingua(base);
        Locale locale = Preferenze.locale(c);
        float f = o.fattore();
        int testo = o.colore();
        int grigio = ContextCompat.getColor(c, R.color.led_grigio);
        int verde = ContextCompat.getColor(c, R.color.led_verde);
        int coloreLive = testo == verde ? 0xFFFFFFFF : verde;

        RemoteViews rv = new RemoteViews(base.getPackageName(),
                stretto ? R.layout.widget_giornata : R.layout.widget_giornata_larga);
        rv.setInt(R.id.w_sfondo, "setColorFilter", OpzioniWidget.COLORI_SFONDO[o.coloreSfondo]);
        rv.setInt(R.id.w_sfondo, "setImageAlpha", Math.round(o.opacita * 255 / 100f));
        rv.setOnClickPendingIntent(R.id.w_radice, apri(base, idWidget * 16 + 15, new Intent(base, MainActivity.class)));

        // "WED / SEP 30": nel 2x4 la data va su due righe, il widget e' stretto
        String data = LocalDate.now().format(DateTimeFormatter.ofPattern(c.getString(R.string.formato_breve), locale))
                .toUpperCase(locale).replace(".", "");
        rv.setTextViewText(R.id.w_data, stretto ? data.replaceFirst(" ", "\n") : data);
        rv.setTextViewTextSize(R.id.w_data, TypedValue.COMPLEX_UNIT_SP, (stretto ? 20 : 17) * f);
        rv.setViewVisibility(R.id.w_data, o.data ? View.VISIBLE : View.GONE);
        rv.setTextColor(R.id.g_titolo, testo);
        rv.setTextViewTextSize(R.id.g_titolo, TypedValue.COMPLEX_UNIT_SP, 10 * f);
        rv.setViewVisibility(R.id.w_piede, o.piede ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_nome, Preferenze.nomeApp(c));
        rv.setTextViewTextSize(R.id.w_vuoto, TypedValue.COMPLEX_UNIT_SP, 12 * f);

        if (ContextCompat.checkSelfPermission(base, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            for (int riga : G_RIGHE) rv.setViewVisibility(riga, View.GONE);
            rv.setTextViewText(R.id.w_vuoto, c.getString(R.string.widget_permesso));
            rv.setViewVisibility(R.id.w_vuoto, View.VISIBLE);
            rv.setTextViewText(R.id.w_altri, "");
            return rv;
        }

        List<Calendario.Istanza> tuttoGiorno = new ArrayList<>();
        List<Calendario.Istanza> conOrario = new ArrayList<>();
        Map<Long, Linee.Linea> linee;
        long adesso = System.currentTimeMillis();
        try {
            linee = Linee.mappa(Linee.da(c, Calendario.calendari(c.getContentResolver(), false)));
            LocalDate oggi = LocalDate.now();
            for (Calendario.Istanza i : Calendario.istanze(c.getContentResolver(), oggi, oggi.plusDays(1), Linee.nascoste(c))) {
                if (i.tuttoIlGiorno) {
                    if (o.tuttoGiorno) tuttoGiorno.add(i);
                } else if (o.passati || i.fine > adesso) {
                    conOrario.add(i);
                }
            }
        } catch (SecurityException e) {
            return null;
        }
        tuttoGiorno.sort(Calendario.ORDINE);
        conOrario.sort(Calendario.ORDINE);

        // Quelli di tutto il giorno restano in cima, ma al massimo un terzo delle righe
        int fissi = o.righe > 1 ? Math.min(tuttoGiorno.size(), Math.max(1, o.righe / 3)) : 0;
        int posti = o.righe - fissi;
        // Gli altri scorrono: dall'ultimo gia' partito in poi, cosi' le prossime partenze restano in vista
        int primo = 0;
        if (conOrario.size() > posti) {
            int prossimo = 0;
            while (prossimo < conOrario.size() && conOrario.get(prossimo).fine <= adesso) prossimo++;
            primo = Math.max(0, Math.min(prossimo - 1, conOrario.size() - posti));
        }
        List<Calendario.Istanza> giornata = new ArrayList<>(tuttoGiorno.subList(0, fissi));
        giornata.addAll(conOrario.subList(primo, Math.min(conOrario.size(), primo + posti)));
        int nascosti = (tuttoGiorno.size() - fissi) + (conOrario.size() - primo - Math.min(posti, conOrario.size() - primo));

        DateTimeFormatter fmtOra = DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale);
        int lato = Math.round((11 * f + 4) * c.getResources().getDisplayMetrics().density);
        for (int k = 0; k < tipo.maxRighe; k++) {
            if (k >= giornata.size()) {
                rv.setViewVisibility(G_RIGHE[k], View.GONE);
                continue;
            }
            Calendario.Istanza i = giornata.get(k);
            Linee.Linea l = linee.get(i.idCalendario);
            int colore = l != null ? l.colore : i.colore;
            String codice = l != null ? l.codice : DrawablePallino.iniziale(i.nomeCalendario);
            boolean partito = !i.tuttoIlGiorno && i.fine <= adesso;
            boolean inCorso = !i.tuttoIlGiorno && !partito && i.inizio <= adesso;
            String orario = i.tuttoIlGiorno ? c.getString(R.string.tutto_il_giorno)
                    : java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(i.inizio),
                    java.time.ZoneId.systemDefault()).format(fmtOra);
            String stato;
            if (i.tuttoIlGiorno) {
                stato = "";
            } else if (partito) {
                stato = c.getString(R.string.widget_partito);
            } else if (inCorso) {
                stato = c.getString(R.string.live);
            } else {
                long mancano = (i.inizio - adesso + 59_999) / 60_000;
                stato = mancano < 60 ? mancano + " " + c.getString(R.string.min)
                        : String.format(Locale.ROOT, "%d:%02d", mancano / 60, mancano % 60);
            }
            String titolo = TextUtils.isEmpty(i.titolo) ? c.getString(R.string.senza_titolo) : i.titolo;
            int coloreRiga = partito ? grigio : testo;

            rv.setViewVisibility(G_RIGHE[k], View.VISIBLE);
            rv.setViewVisibility(G_COLONNE[k], o.pallini ? View.VISIBLE : View.GONE);
            if (o.pallini) rv.setImageViewBitmap(G_PALLINI[k], DrawablePallino.bitmap(colore, codice, lato));
            rv.setTextViewText(G_ORARI[k], orario.toUpperCase(locale));
            rv.setTextColor(G_ORARI[k], coloreRiga);
            rv.setTextViewTextSize(G_ORARI[k], TypedValue.COMPLEX_UNIT_SP, (stretto ? 11 : 12) * f);
            rv.setTextViewText(G_STATI[k], stato.toUpperCase(locale));
            rv.setTextColor(G_STATI[k], inCorso ? coloreLive : coloreRiga);
            rv.setTextViewTextSize(G_STATI[k], TypedValue.COMPLEX_UNIT_SP, (stretto ? 11 : 12) * f);
            rv.setTextViewText(G_TITOLI[k], titolo.toUpperCase(locale));
            rv.setTextColor(G_TITOLI[k], coloreRiga);
            rv.setTextViewTextSize(G_TITOLI[k], TypedValue.COMPLEX_UNIT_SP, 13 * f);
            rv.setOnClickPendingIntent(G_RIGHE[k], apri(base, idWidget * 16 + k,
                    new Intent(base, EventoActivity.class).putExtra(EventoActivity.EXTRA_ID_EVENTO, i.idEvento)));
        }
        rv.setTextViewText(R.id.w_vuoto, c.getString(R.string.widget_vuoto));
        rv.setViewVisibility(R.id.w_vuoto, giornata.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_altri, nascosti > 0 ? c.getString(R.string.widget_altri, nascosti) : "");
        return rv;
    }

    /** Gli eventi di oggi non ancora finiti, in ordine di partenza (quelli di tutto il giorno in cima, se richiesti). */
    private static List<Calendario.Istanza> prossimePartenze(Context c, boolean tuttoGiorno) {
        LocalDate oggi = LocalDate.now();
        long adesso = System.currentTimeMillis();
        List<Calendario.Istanza> risultato = new ArrayList<>();
        for (Calendario.Istanza i : Calendario.istanze(c.getContentResolver(), oggi, oggi.plusDays(1), Linee.nascoste(c))) {
            if (i.tuttoIlGiorno ? tuttoGiorno : i.fine > adesso) risultato.add(i);
        }
        risultato.sort(Calendario.ORDINE);
        return risultato;
    }

    private static PendingIntent apri(Context c, int codice, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, codice, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---- Programmazione degli aggiornamenti ----

    /** Avvia (o ferma, se non ci sono piu' widget) l'aggiornamento al minuto e quello sui cambi del calendario. */
    static void programma(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent minuto = PendingIntent.getBroadcast(c, 0,
                new Intent(c, RicevitoreWidget.class).setAction(AZIONE_MINUTO),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (ciSonoWidget(c)) {
            long prossimo = (System.currentTimeMillis() / 60_000 + 1) * 60_000;
            am.setRepeating(AlarmManager.RTC, prossimo, 60_000, minuto);
            programmaLavoro(c);
        } else {
            am.cancel(minuto);
            js.cancel(ID_LAVORO);
        }
    }

    /** Lavoro che parte quando cambia qualcosa nel calendario (va riprogrammato ogni volta). */
    static void programmaLavoro(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 24) return;
        JobScheduler js = c.getSystemService(JobScheduler.class);
        JobInfo lavoro = new JobInfo.Builder(ID_LAVORO, new ComponentName(c, LavoroWidget.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(1_000)
                .setTriggerContentMaxDelay(5_000)
                .build();
        js.schedule(lavoro);
    }
}
