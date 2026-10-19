package com.calendarioanno.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le "linee" del video: ogni calendario del telefono con un codice (la lettera nel pallino),
 * un nome e un colore. Codice, nome e colore si possono cambiare: restano salvati nell'app.
 */
final class Linee {

    static final class Linea {
        long id;
        String nome, codice, account;
        int colore;
        boolean nascosta, creataDaApp;
    }

    private static final String SEQUENZA_CODICI = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
    private static final String PREF_CODICE = "linea_codice_";
    private static final String PREF_NOME = "linea_nome_";
    private static final String PREF_COLORE = "linea_colore_";
    private static final String PREF_NASCOSTE = "linee_nascoste";

    /** Prima le lettere, poi le cifre, come sui cartelli. */
    static final Comparator<Linea> ORDINE = (a, b) -> {
        boolean ca = Character.isDigit(a.codice.charAt(0)), cb = Character.isDigit(b.codice.charAt(0));
        if (ca != cb) return ca ? 1 : -1;
        int c = a.codice.compareTo(b.codice);
        return c != 0 ? c : Long.compare(a.id, b.id);
    };

    private Linee() {
    }

    /** Linee dei calendari dati; a chi non ha ancora un codice assegna la prima lettera libera. */
    static List<Linea> da(Context c, List<Calendario.InfoCalendario> calendari) {
        SharedPreferences p = Preferenze.di(c);
        Set<Long> nascoste = nascoste(c);
        List<Calendario.InfoCalendario> perId = new ArrayList<>(calendari);
        perId.sort(Comparator.comparingLong(i -> i.id));

        Set<String> usati = new HashSet<>();
        for (Calendario.InfoCalendario i : perId) {
            String codice = p.getString(PREF_CODICE + i.id, null);
            if (codice != null) usati.add(codice);
        }
        SharedPreferences.Editor ed = null;
        List<Linea> risultato = new ArrayList<>();
        for (Calendario.InfoCalendario i : perId) {
            Linea l = new Linea();
            l.id = i.id;
            l.account = i.account;
            l.creataDaApp = i.creataDaApp();
            l.nome = p.getString(PREF_NOME + i.id, i.nome);
            l.colore = p.contains(PREF_COLORE + i.id) ? p.getInt(PREF_COLORE + i.id, i.colore) : i.colore;
            l.nascosta = nascoste.contains(i.id);
            l.codice = p.getString(PREF_CODICE + i.id, null);
            if (l.codice == null) {
                l.codice = primoLibero(usati);
                usati.add(l.codice);
                if (ed == null) ed = p.edit();
                ed.putString(PREF_CODICE + i.id, l.codice);
            }
            risultato.add(l);
        }
        if (ed != null) ed.apply();
        risultato.sort(ORDINE);
        return risultato;
    }

    static Map<Long, Linea> mappa(List<Linea> linee) {
        Map<Long, Linea> m = new HashMap<>();
        for (Linea l : linee) m.put(l.id, l);
        return m;
    }

    private static String primoLibero(Set<String> usati) {
        for (int k = 0; k < SEQUENZA_CODICI.length(); k++) {
            String s = String.valueOf(SEQUENZA_CODICI.charAt(k));
            if (!usati.contains(s)) return s;
        }
        return "#";
    }

    /** Prossimo codice libero, per una linea nuova. */
    static String codiceLibero(List<Linea> linee) {
        Set<String> usati = new HashSet<>();
        for (Linea l : linee) usati.add(l.codice);
        return primoLibero(usati);
    }

    static void salva(Context c, long id, String nome, String codice, int colore) {
        Preferenze.di(c).edit()
                .putString(PREF_NOME + id, nome)
                .putString(PREF_CODICE + id, codice)
                .putInt(PREF_COLORE + id, colore)
                .apply();
    }

    static void dimentica(Context c, long id) {
        Preferenze.di(c).edit().remove(PREF_NOME + id).remove(PREF_CODICE + id).remove(PREF_COLORE + id).apply();
        impostaNascosta(c, id, false);
    }

    static Set<Long> nascoste(Context c) {
        Set<Long> ids = new HashSet<>();
        for (String s : Preferenze.di(c).getStringSet(PREF_NASCOSTE, Collections.emptySet())) {
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException ignorata) {
                // voce rovinata: la salto
            }
        }
        return ids;
    }

    static void impostaNascosta(Context c, long id, boolean nascosta) {
        Set<String> ids = new HashSet<>(Preferenze.di(c).getStringSet(PREF_NASCOSTE, Collections.emptySet()));
        if (nascosta) ids.add(String.valueOf(id));
        else ids.remove(String.valueOf(id));
        Preferenze.di(c).edit().putStringSet(PREF_NASCOSTE, ids).apply();
    }
}
