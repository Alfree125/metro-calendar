package com.calendarioanno.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;

/**
 * Notifiche di promemoria dell'app, in stile cartello: pallino della linea, "Departs in 10 min",
 * pulsante per rimandare. Partono dai promemoria degli eventi, quando il calendario di sistema
 * li fa scattare (vedi {@link RicevitorePromemoria}).
 */
final class Promemoria {

    static final String CANALE = "promemoria_eventi";
    static final String AZIONE_RIMANDA = "com.calendarioanno.app.RIMANDA";
    static final String AZIONE_RIPROPONI = "com.calendarioanno.app.RIPROPONI";
    static final String EXTRA_EVENTO = "id_evento";
    static final String EXTRA_INIZIO = "inizio";
    static final String EXTRA_FINE = "fine";
    static final int MINUTI_RIMANDA = 10;

    private Promemoria() {
    }

    /** Su Android 13+ le notifiche vanno chieste all'utente. */
    static boolean serveRichiesta(Context c) {
        return Build.VERSION.SDK_INT >= 33 && Preferenze.notificheAttive(c)
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    static boolean possoNotificare(Context c) {
        return NotificationManagerCompat.from(c).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }

    static void creaCanale(Context base) {
        if (Build.VERSION.SDK_INT < 26) return;
        Context c = Preferenze.conLingua(base);
        NotificationChannel canale = new NotificationChannel(CANALE, c.getString(R.string.canale_promemoria),
                NotificationManager.IMPORTANCE_HIGH);
        canale.setDescription(c.getString(R.string.canale_descrizione));
        c.getSystemService(NotificationManager.class).createNotificationChannel(canale);
    }

    /** Notifica per un'occorrenza di un evento (letto dal calendario, con colore e codice della sua linea). */
    static void notifica(Context base, long idEvento, long inizio, long fine) {
        Context c = Preferenze.conLingua(base);
        Calendario.InfoNotifica info;
        Map<Long, Linee.Linea> linee;
        try {
            info = Calendario.perNotifica(c.getContentResolver(), idEvento);
            if (info == null) return;
            linee = Linee.mappa(Linee.da(c, Calendario.calendari(c.getContentResolver(), false)));
        } catch (SecurityException e) {
            return;
        }
        Linee.Linea l = linee.get(info.idCalendario);
        int colore = l != null ? l.colore : ContextCompat.getColor(c, R.color.linea_blu);
        String codice = l != null ? l.codice : "";
        String titolo = TextUtils.isEmpty(info.titolo) ? c.getString(R.string.senza_titolo) : info.titolo;
        mostra(c, idNotifica(idEvento, inizio), idEvento, inizio, fine, info.tuttoIlGiorno, titolo, info.luogo,
                l != null ? l.nome : null, colore, codice);
    }

    /** Notifica di esempio, dalle impostazioni. */
    static void prova(Context base) {
        Context c = Preferenze.conLingua(base);
        long tra = System.currentTimeMillis() + 10 * 60_000;
        mostra(c, 1, -1, tra, tra + 30 * 60_000, false, c.getString(R.string.notifica_prova_titolo),
                c.getString(R.string.notifica_prova_luogo), null, ContextCompat.getColor(c, R.color.linea_gialla), "B");
    }

    private static void mostra(Context c, int id, long idEvento, long inizio, long fine, boolean tuttoIlGiorno,
                               String titolo, String luogo, String linea, int colore, String codice) {
        if (!possoNotificare(c)) return;
        creaCanale(c);
        long adesso = System.currentTimeMillis();
        DateTimeFormatter fmtOra = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Preferenze.locale(c));
        String ora = LocalDateTime.ofInstant(Instant.ofEpochMilli(inizio), ZoneId.systemDefault()).format(fmtOra);

        String quando;
        if (tuttoIlGiorno) {
            quando = c.getString(R.string.tutto_il_giorno);
        } else if (inizio - adesso > 30_000) {
            quando = c.getString(R.string.notifica_parte_tra, durata(c, (inizio - adesso + 59_999) / 60_000)) + " · " + ora;
        } else if (adesso < fine) {
            quando = c.getString(R.string.notifica_in_partenza) + " · " + ora;
        } else {
            quando = ora;
        }
        String testo = TextUtils.isEmpty(luogo) ? quando : quando + " · " + luogo;

        int lato = Math.round(64 * c.getResources().getDisplayMetrics().density);
        NotificationCompat.Builder n = new NotificationCompat.Builder(c, CANALE)
                .setSmallIcon(R.drawable.ic_notifica)
                .setLargeIcon(DrawablePallino.bitmap(colore, codice, lato))
                .setColor(ContextCompat.getColor(c, R.color.giallo))
                .setContentTitle(titolo)
                .setContentText(testo)
                .setSubText(linea)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(inizio)
                .setShowWhen(!tuttoIlGiorno)
                .setAutoCancel(true);

        Intent apri = idEvento >= 0
                ? new Intent(c, EventoActivity.class).putExtra(EventoActivity.EXTRA_ID_EVENTO, idEvento)
                : new Intent(c, MainActivity.class);
        apri.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        n.setContentIntent(PendingIntent.getActivity(c, id, apri,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        if (idEvento >= 0 && !tuttoIlGiorno && adesso < fine) {
            Intent rimanda = new Intent(c, RicevitoreAzioni.class).setAction(AZIONE_RIMANDA)
                    .putExtra(EXTRA_EVENTO, idEvento).putExtra(EXTRA_INIZIO, inizio).putExtra(EXTRA_FINE, fine);
            n.addAction(R.drawable.ic_campanella, c.getString(R.string.rimanda, MINUTI_RIMANDA),
                    PendingIntent.getBroadcast(c, id, rimanda, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        try {
            NotificationManagerCompat.from(c).notify(id, n.build());
        } catch (SecurityException ignorata) {
            // permesso tolto nel frattempo
        }
    }

    /** Toglie la notifica e la ripropone fra qualche minuto. */
    static void rimanda(Context c, long idEvento, long inizio, long fine) {
        NotificationManagerCompat.from(c).cancel(idNotifica(idEvento, inizio));
        Intent ripropone = new Intent(c, RicevitoreAzioni.class).setAction(AZIONE_RIPROPONI)
                .putExtra(EXTRA_EVENTO, idEvento).putExtra(EXTRA_INIZIO, inizio).putExtra(EXTRA_FINE, fine);
        PendingIntent pi = PendingIntent.getBroadcast(c, idNotifica(idEvento, inizio), ripropone,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + MINUTI_RIMANDA * 60_000L, pi);
    }

    /** Stessa occorrenza, stessa notifica: piu' promemoria dello stesso evento si sostituiscono. */
    private static int idNotifica(long idEvento, long inizio) {
        return (int) (idEvento * 31 + inizio / 60_000) | 0x1000;
    }

    private static String durata(Context c, long minuti) {
        if (minuti < 60) return c.getString(R.string.durata_min, (int) minuti);
        if (minuti % 60 == 0) return c.getString(R.string.durata_ore, (int) (minuti / 60));
        return c.getString(R.string.durata_ore_min, (int) (minuti / 60), (int) (minuti % 60));
    }
}
