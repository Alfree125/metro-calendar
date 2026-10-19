package com.calendarioanno.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Reminders;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Accesso al calendario di sistema (CalendarContract): gli eventi salvati qui
 * sono gli stessi che vedono Google Calendar e le altre app calendario.
 */
final class Calendario {

    private Calendario() {
    }

    /** Un'occorrenza di un evento (gli eventi ripetuti ne hanno una per ripetizione). */
    static final class Istanza {
        long idEvento, idCalendario;
        long inizio, fine;
        boolean tuttoIlGiorno;
        String titolo, luogo, nomeCalendario;
        int colore;
        /** Giorni locali coperti, estremi inclusi. */
        LocalDate primoGiorno, ultimoGiorno;

        boolean copre(LocalDate giorno) {
            return !giorno.isBefore(primoGiorno) && !giorno.isAfter(ultimoGiorno);
        }
    }

    static final class InfoCalendario {
        long id;
        String nome, account, tipoAccount;
        int colore;
        boolean primario;

        /** Le linee aggiunte da questa app: solo queste si possono eliminare da qui. */
        boolean creataDaApp() {
            return CalendarContract.ACCOUNT_TYPE_LOCAL.equals(tipoAccount) && ACCOUNT_APP.equals(account);
        }
    }

    private static final String ACCOUNT_APP = "Anno";

    /** Evento completo, per la schermata di modifica. */
    static final class Evento {
        long id = -1;
        long idCalendario = -1;
        String titolo = "", luogo = "", note = "";
        boolean tuttoIlGiorno;
        /** Per gli eventi di tutto il giorno conta solo la data; la fine e' inclusa. */
        LocalDateTime inizio, fine;
        /** Minuti di anticipo di ogni promemoria (vuoto se nessuno), in ordine. */
        List<Integer> promemoria = new ArrayList<>();
        boolean ricorrente;
    }

    /** Prima i "tutto il giorno", poi in ordine di inizio. */
    static final Comparator<Istanza> ORDINE = (a, b) -> {
        if (a.tuttoIlGiorno != b.tuttoIlGiorno) return a.tuttoIlGiorno ? -1 : 1;
        return Long.compare(a.inizio, b.inizio);
    };

    private static final String[] PROIEZIONE_ISTANZE = {
            Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
            Instances.TITLE, Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR,
            Instances.CALENDAR_ID, Instances.CALENDAR_DISPLAY_NAME,
    };

    /**
     * Occorrenze dei calendari visibili che toccano i giorni da {@code dal} (incluso) ad {@code al} (escluso),
     * escluse le linee nascoste nell'app.
     */
    static List<Istanza> istanze(ContentResolver cr, LocalDate dal, LocalDate al, Set<Long> nascoste) {
        ZoneId zona = ZoneId.systemDefault();
        // Un giorno di margine: gli eventi "tutto il giorno" sono salvati in UTC
        long da = dal.minusDays(1).atStartOfDay(zona).toInstant().toEpochMilli();
        long a = al.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli();
        Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, da);
        ContentUris.appendId(uri, a);
        String selezione = Instances.VISIBLE + "=1 AND (" + Instances.STATUS + " IS NULL OR "
                + Instances.STATUS + "!=" + Events.STATUS_CANCELED + ")";

        List<Istanza> risultato = new ArrayList<>();
        try (Cursor c = cr.query(uri.build(), PROIEZIONE_ISTANZE, selezione, null,
                Instances.BEGIN + " ASC")) {
            if (c == null) return risultato;
            while (c.moveToNext()) {
                Istanza i = new Istanza();
                i.idEvento = c.getLong(0);
                i.inizio = c.getLong(1);
                i.fine = c.getLong(2);
                i.tuttoIlGiorno = c.getInt(3) != 0;
                i.titolo = c.getString(4);
                i.luogo = c.getString(5);
                i.colore = c.isNull(6) ? 0xFF888888 : c.getInt(6);
                i.idCalendario = c.getLong(7);
                i.nomeCalendario = c.getString(8);
                if (nascoste != null && nascoste.contains(i.idCalendario)) continue;
                if (i.tuttoIlGiorno) {
                    i.primoGiorno = dataUtc(i.inizio);
                    i.ultimoGiorno = dataUtc(i.fine).minusDays(1);
                } else {
                    i.primoGiorno = dataLocale(i.inizio, zona);
                    i.ultimoGiorno = dataLocale(Math.max(i.inizio, i.fine - 1), zona);
                }
                if (i.ultimoGiorno.isBefore(i.primoGiorno)) i.ultimoGiorno = i.primoGiorno;
                if (i.ultimoGiorno.isBefore(dal) || !i.primoGiorno.isBefore(al)) continue;
                risultato.add(i);
            }
        }
        return risultato;
    }

    /** Calendari visibili nel sistema; con {@code soloScrivibili} solo quelli in cui si puo' salvare. */
    static List<InfoCalendario> calendari(ContentResolver cr, boolean soloScrivibili) {
        String[] proiezione = {
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.CALENDAR_COLOR, Calendars.IS_PRIMARY, Calendars.ACCOUNT_TYPE,
        };
        String selezione = Calendars.VISIBLE + "=1";
        if (soloScrivibili) {
            selezione += " AND " + Calendars.CALENDAR_ACCESS_LEVEL + ">=" + Calendars.CAL_ACCESS_CONTRIBUTOR;
        }
        List<InfoCalendario> risultato = new ArrayList<>();
        try (Cursor c = cr.query(Calendars.CONTENT_URI, proiezione, selezione, null,
                Calendars.IS_PRIMARY + " DESC, " + Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
            if (c == null) return risultato;
            while (c.moveToNext()) {
                InfoCalendario info = new InfoCalendario();
                info.id = c.getLong(0);
                info.nome = c.getString(1);
                info.account = c.getString(2);
                info.colore = c.getInt(3);
                info.primario = c.getInt(4) != 0;
                info.tipoAccount = c.getString(5);
                risultato.add(info);
            }
        }
        return risultato;
    }

    /** Crea sul telefono un calendario locale (senza account), per chi non ne ha nessuno. */
    static long creaCalendarioLocale(ContentResolver cr, String nome, int colore) {
        String account = ACCOUNT_APP;
        ContentValues v = new ContentValues();
        v.put(Calendars.ACCOUNT_NAME, account);
        v.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        v.put(Calendars.NAME, nome);
        v.put(Calendars.CALENDAR_DISPLAY_NAME, nome);
        v.put(Calendars.CALENDAR_COLOR, colore);
        v.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
        v.put(Calendars.OWNER_ACCOUNT, account);
        v.put(Calendars.VISIBLE, 1);
        v.put(Calendars.SYNC_EVENTS, 1);
        v.put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().getId());
        Uri creato = cr.insert(uriAccountApp(Calendars.CONTENT_URI), v);
        return creato == null ? -1 : ContentUris.parseId(creato);
    }

    /** Rinomina e ricolora una linea creata dall'app (le altre appartengono al loro account). */
    static void aggiornaCalendario(ContentResolver cr, long id, String nome, int colore) {
        ContentValues v = new ContentValues();
        v.put(Calendars.CALENDAR_DISPLAY_NAME, nome);
        v.put(Calendars.CALENDAR_COLOR, colore);
        cr.update(ContentUris.withAppendedId(uriAccountApp(Calendars.CONTENT_URI), id), v, null, null);
    }

    /** Elimina una linea creata dall'app, con tutti i suoi eventi. */
    static void eliminaCalendario(ContentResolver cr, InfoCalendario info) {
        if (!info.creataDaApp()) return;
        cr.delete(ContentUris.withAppendedId(uriAccountApp(Calendars.CONTENT_URI), info.id), null, null);
    }

    /** I calendari locali si creano ed eliminano solo "come sync adapter" del loro account. */
    private static Uri uriAccountApp(Uri base) {
        return base.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_APP)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build();
    }

    /** Legge un evento, o null se non esiste piu'. */
    static Evento leggi(ContentResolver cr, long id) {
        String[] proiezione = {
                Events.CALENDAR_ID, Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION,
                Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.RRULE, Events.RDATE,
                Events.DURATION,
        };
        Evento e = new Evento();
        try (Cursor c = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, id),
                proiezione, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            e.id = id;
            e.idCalendario = c.getLong(0);
            e.titolo = testo(c.getString(1));
            e.luogo = testo(c.getString(2));
            e.note = testo(c.getString(3));
            long inizio = c.getLong(4);
            e.tuttoIlGiorno = c.getInt(6) != 0;
            e.ricorrente = !c.isNull(7) || !c.isNull(8);
            // Gli eventi ripetuti hanno DURATION al posto di DTEND
            long fine = c.isNull(5) ? inizio + durataMillis(c.getString(9), e.tuttoIlGiorno) : c.getLong(5);
            if (e.tuttoIlGiorno) {
                e.inizio = dataUtc(inizio).atStartOfDay();
                LocalDate ultimo = dataUtc(fine).minusDays(1);
                e.fine = (ultimo.isBefore(e.inizio.toLocalDate()) ? e.inizio.toLocalDate() : ultimo).atStartOfDay();
            } else {
                ZoneId zona = ZoneId.systemDefault();
                e.inizio = LocalDateTime.ofInstant(Instant.ofEpochMilli(inizio), zona);
                e.fine = LocalDateTime.ofInstant(Instant.ofEpochMilli(Math.max(inizio, fine)), zona);
            }
        }
        try (Cursor c = cr.query(Reminders.CONTENT_URI, new String[]{Reminders.MINUTES},
                Reminders.EVENT_ID + "=?", new String[]{String.valueOf(id)},
                Reminders.MINUTES + " ASC")) {
            while (c != null && c.moveToNext()) {
                int minuti = c.getInt(0);
                if (!e.promemoria.contains(minuti)) e.promemoria.add(minuti);
            }
        }
        return e;
    }

    /** Inserisce o aggiorna l'evento; restituisce il suo id. */
    static long salva(ContentResolver cr, Evento e) {
        ContentValues v = new ContentValues();
        v.put(Events.TITLE, e.titolo);
        v.put(Events.EVENT_LOCATION, e.luogo);
        v.put(Events.DESCRIPTION, e.note);
        if (!e.ricorrente) {
            // Per gli eventi ripetuti data, ora e regola restano quelle dell'app che li ha creati
            v.put(Events.ALL_DAY, e.tuttoIlGiorno ? 1 : 0);
            if (e.tuttoIlGiorno) {
                v.put(Events.DTSTART, millisUtc(e.inizio.toLocalDate()));
                v.put(Events.DTEND, millisUtc(e.fine.toLocalDate().plusDays(1)));
                v.put(Events.EVENT_TIMEZONE, "UTC");
            } else {
                ZoneId zona = ZoneId.systemDefault();
                v.put(Events.DTSTART, e.inizio.atZone(zona).toInstant().toEpochMilli());
                v.put(Events.DTEND, e.fine.atZone(zona).toInstant().toEpochMilli());
                v.put(Events.EVENT_TIMEZONE, zona.getId());
            }
        }

        long id = e.id;
        if (id < 0) {
            // Il calendario si sceglie solo alla creazione: spostare eventi sincronizzati non e' affidabile
            v.put(Events.CALENDAR_ID, e.idCalendario);
            Uri creato = cr.insert(Events.CONTENT_URI, v);
            if (creato == null) throw new IllegalStateException("Inserimento rifiutato");
            id = ContentUris.parseId(creato);
        } else {
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null);
            cr.delete(Reminders.CONTENT_URI, Reminders.EVENT_ID + "=?", new String[]{String.valueOf(id)});
        }
        for (int minuti : e.promemoria) {
            ContentValues r = new ContentValues();
            r.put(Reminders.EVENT_ID, id);
            r.put(Reminders.MINUTES, minuti);
            r.put(Reminders.METHOD, Reminders.METHOD_ALERT);
            cr.insert(Reminders.CONTENT_URI, r);
        }
        return id;
    }

    /** Quello che serve a una notifica di promemoria. */
    static final class InfoNotifica {
        String titolo, luogo;
        long idCalendario;
        boolean tuttoIlGiorno;
    }

    /** Dati dell'evento per la notifica, o null se l'evento non c'e' piu'. */
    static InfoNotifica perNotifica(ContentResolver cr, long id) {
        String[] proiezione = {Events.TITLE, Events.EVENT_LOCATION, Events.CALENDAR_ID, Events.ALL_DAY};
        try (Cursor c = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), proiezione, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            InfoNotifica i = new InfoNotifica();
            i.titolo = testo(c.getString(0));
            i.luogo = testo(c.getString(1));
            i.idCalendario = c.getLong(2);
            i.tuttoIlGiorno = c.getInt(3) != 0;
            return i;
        }
    }

    static void elimina(ContentResolver cr, long id) {
        cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null);
    }

    static LocalDate dataUtc(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalDate dataLocale(long millis, ZoneId zona) {
        return Instant.ofEpochMilli(millis).atZone(zona).toLocalDate();
    }

    static long millisUtc(LocalDate data) {
        return data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static String testo(String s) {
        return s == null ? "" : s;
    }

    /** Durata RFC 5545 semplice (P1D, PT3600S, P1W...), quella usata da Android per gli eventi ripetuti. */
    private static long durataMillis(String durata, boolean tuttoIlGiorno) {
        long predefinita = tuttoIlGiorno ? 86_400_000L : 3_600_000L;
        if (durata == null || durata.isEmpty()) return predefinita;
        try {
            String d = durata.startsWith("+") ? durata.substring(1) : durata;
            if (!d.startsWith("P")) return predefinita;
            long totale = 0, numero = 0;
            for (int k = 1; k < d.length(); k++) {
                char ch = d.charAt(k);
                if (Character.isDigit(ch)) {
                    numero = numero * 10 + (ch - '0');
                    continue;
                }
                switch (ch) {
                    case 'W': totale += numero * 7 * 86_400_000L; break;
                    case 'D': totale += numero * 86_400_000L; break;
                    case 'H': totale += numero * 3_600_000L; break;
                    case 'M': totale += numero * 60_000L; break;
                    case 'S': totale += numero * 1_000L; break;
                    default: break; // 'T'
                }
                numero = 0;
            }
            return totale > 0 ? totale : predefinita;
        } catch (RuntimeException ex) {
            return predefinita;
        }
    }
}
