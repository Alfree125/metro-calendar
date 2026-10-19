package com.calendarioanno.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Come appare un singolo widget: testo, righe, sfondo, colori, cosa mostrare. Salvate per id del widget. */
final class OpzioniWidget {

    /** Dimensioni del testo: S, M, L, XL. */
    static final float[] SCALE = {0.85f, 1f, 1.15f, 1.3f};
    static final String[] NOMI_SCALE = {"S", "M", "L", "XL"};
    /** Nero, blu notte, grafite, verde scuro, bordeaux. */
    static final int[] COLORI_SFONDO = {0xFF000000, 0xFF0B1F3A, 0xFF2A2A2A, 0xFF0D2B1D, 0xFF3A0D12};
    /** Giallo LED, bianco, arancio, verde LED, azzurro. */
    static final int[] COLORI_TESTO = {0xFFFFC72C, 0xFFFFFFFF, 0xFFFF8C1A, 0xFF3DDC97, 0xFF7FD3FF};

    int scala = 1;
    int righe;
    /** Opacita' dello sfondo in percentuale. */
    int opacita = 100;
    int coloreSfondo;
    int coloreTesto;
    boolean pallini = true;
    boolean data;
    boolean piede;
    boolean tuttoGiorno;
    /** Solo per il widget giornata: mostra anche gli eventi gia' finiti (attenuati). */
    boolean passati = true;

    private OpzioniWidget() {
    }

    /** Come nel video: il piccolo ha il piede con "+N", il grande ha la data in alto. */
    static OpzioniWidget predefinite(AggiornaWidget.Tipo tipo) {
        OpzioniWidget o = new OpzioniWidget();
        o.righe = tipo == AggiornaWidget.Tipo.GRANDE ? 8 : 4;
        o.scala = tipo == AggiornaWidget.Tipo.PICCOLO ? 0 : tipo == AggiornaWidget.Tipo.GRANDE ? 2 : 1;
        o.data = tipo == AggiornaWidget.Tipo.GRANDE;
        o.piede = tipo == AggiornaWidget.Tipo.PICCOLO;
        if (tipo == AggiornaWidget.Tipo.GIORNATA || tipo == AggiornaWidget.Tipo.GIORNATA_LARGA) {
            // Tutta la giornata: data in alto, eventi di tutto il giorno e gia' partiti compresi
            o.righe = tipo == AggiornaWidget.Tipo.GIORNATA ? 8 : 4;
            o.scala = 1;
            o.data = true;
            o.tuttoGiorno = true;
        }
        return o;
    }

    private static String chiave(int id, String nome) {
        return "widget_" + id + "_" + nome;
    }

    static OpzioniWidget carica(Context c, int id, AggiornaWidget.Tipo tipo) {
        OpzioniWidget o = predefinite(tipo);
        SharedPreferences p = Preferenze.di(c);
        o.scala = limita(p.getInt(chiave(id, "scala"), o.scala), 0, SCALE.length - 1);
        o.righe = limita(p.getInt(chiave(id, "righe"), o.righe), 1, tipo.maxRighe);
        o.opacita = limita(p.getInt(chiave(id, "opacita"), o.opacita), 0, 100);
        o.coloreSfondo = limita(p.getInt(chiave(id, "sfondo"), o.coloreSfondo), 0, COLORI_SFONDO.length - 1);
        o.coloreTesto = limita(p.getInt(chiave(id, "testo"), o.coloreTesto), 0, COLORI_TESTO.length - 1);
        o.pallini = p.getBoolean(chiave(id, "pallini"), o.pallini);
        o.data = p.getBoolean(chiave(id, "data"), o.data);
        o.piede = p.getBoolean(chiave(id, "piede"), o.piede);
        o.tuttoGiorno = p.getBoolean(chiave(id, "tutto_giorno"), o.tuttoGiorno);
        o.passati = p.getBoolean(chiave(id, "passati"), o.passati);
        return o;
    }

    void salva(Context c, int id) {
        Preferenze.di(c).edit()
                .putInt(chiave(id, "scala"), scala)
                .putInt(chiave(id, "righe"), righe)
                .putInt(chiave(id, "opacita"), opacita)
                .putInt(chiave(id, "sfondo"), coloreSfondo)
                .putInt(chiave(id, "testo"), coloreTesto)
                .putBoolean(chiave(id, "pallini"), pallini)
                .putBoolean(chiave(id, "data"), data)
                .putBoolean(chiave(id, "piede"), piede)
                .putBoolean(chiave(id, "tutto_giorno"), tuttoGiorno)
                .putBoolean(chiave(id, "passati"), passati)
                .apply();
    }

    static void elimina(Context c, int id) {
        SharedPreferences.Editor ed = Preferenze.di(c).edit();
        for (String nome : new String[]{"scala", "righe", "opacita", "sfondo", "testo", "pallini", "data", "piede", "tutto_giorno", "passati"}) {
            ed.remove(chiave(id, nome));
        }
        ed.apply();
    }

    /** Copia da passare al thread che disegna l'anteprima. */
    OpzioniWidget copia() {
        OpzioniWidget o = new OpzioniWidget();
        o.scala = scala;
        o.righe = righe;
        o.opacita = opacita;
        o.coloreSfondo = coloreSfondo;
        o.coloreTesto = coloreTesto;
        o.pallini = pallini;
        o.data = data;
        o.piede = piede;
        o.tuttoGiorno = tuttoGiorno;
        o.passati = passati;
        return o;
    }

    float fattore() {
        return SCALE[scala];
    }

    int colore() {
        return COLORI_TESTO[coloreTesto];
    }

    private static int limita(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
