package com.calendarioanno.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** "Add Event" del video: nome, tutto il giorno, inizio, durata, linea, promemoria, luogo e note. */
public class EventoActivity extends AppCompatActivity {

    static final String EXTRA_ID_EVENTO = "id_evento";
    static final String EXTRA_GIORNO = "giorno";

    private static final String PREF_ULTIMA_LINEA = "ultimo_calendario";
    private static final String STATO_INIZIO = "inizio";
    private static final String STATO_FINE = "fine";
    private static final String STATO_TUTTO_GIORNO = "tutto_giorno";
    private static final String STATO_DURATA = "durata";
    private static final String STATO_LINEA = "linea";
    private static final String STATO_PROMEMORIA = "promemoria";
    private static final String STATO_LUOGO_NOTE = "luogo_note";
    private static final String TAG_DATA_INIZIO = "data_inizio";
    private static final String TAG_DATA_FINE = "data_fine";
    private static final String TAG_ORA_INIZIO = "ora_inizio";

    /** Scelte per un nuovo promemoria, in minuti di anticipo. */
    static final int[] MINUTI_PROMEMORIA = {0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080};
    /** Google Calendar accetta al massimo 5 promemoria per evento. */
    private static final int MAX_PROMEMORIA = 5;
    private static final int[] DURATE = {15, 30, 45, 60, 90, 120, 180, 240, 480};

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());
    private DateTimeFormatter fmtData, fmtOra;

    private SharedPreferences prefs;
    private View modulo, rigaFine, rigaDurata, gruppoLuogoNote;
    private TextView titoloFoglio, valDataInizio, valOraInizio, valDataFine, valDurata, valLinea, valNessunPromemoria;
    private TextView erroreTitolo, erroreOrario;
    private ImageView pallinoLinea;
    private ImageButton btnAzione;
    private EditText inTitolo, inLuogo, inNote;
    private LinearLayout listaPromemoria;
    private View rigaAggiungiPromemoria;
    private MaterialSwitch swTuttoGiorno;
    private View btnSalva;

    private Calendario.Evento evento = new Calendario.Evento();
    private List<Linee.Linea> linee = new ArrayList<>();
    private int durataMinuti = 60;
    private int indiceLinea = -1;
    private final List<Integer> promemoria = new ArrayList<>();
    private boolean salvataggioInCorso;

    private final ActivityResultLauncher<String> richiestaNotifiche =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concesso -> finish());

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Preferenze.conLingua(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_evento);
        prefs = Preferenze.di(this);
        Locale locale = Preferenze.locale(this);
        fmtData = DateTimeFormatter.ofPattern(getString(R.string.formato_data_evento), locale);
        fmtOra = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);

        View root = findViewById(R.id.root);
        View scorrimento = findViewById(R.id.scorrimento);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, 0);
            // Il foglio bianco arriva fino in fondo; il contenuto resta sopra barra e tastiera
            scorrimento.setPadding(0, 0, 0, Math.max(barre.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        ((TextView) findViewById(R.id.striscia_nome)).setText(Preferenze.nomeApp(this));
        View chiudi = findViewById(R.id.striscia_chiudi);
        chiudi.setVisibility(View.VISIBLE);
        chiudi.setOnClickListener(v -> finish());

        modulo = findViewById(R.id.modulo);
        rigaFine = findViewById(R.id.riga_fine);
        rigaDurata = findViewById(R.id.riga_durata);
        gruppoLuogoNote = findViewById(R.id.gruppo_luogo_note);
        titoloFoglio = findViewById(R.id.titolo_foglio);
        valDataInizio = findViewById(R.id.val_data_inizio);
        valOraInizio = findViewById(R.id.val_ora_inizio);
        valDataFine = findViewById(R.id.val_data_fine);
        valDurata = findViewById(R.id.val_durata);
        valLinea = findViewById(R.id.val_linea);
        valNessunPromemoria = findViewById(R.id.val_nessun_promemoria);
        listaPromemoria = findViewById(R.id.lista_promemoria);
        rigaAggiungiPromemoria = findViewById(R.id.riga_aggiungi_promemoria);
        erroreTitolo = findViewById(R.id.errore_titolo);
        erroreOrario = findViewById(R.id.errore_orario);
        pallinoLinea = findViewById(R.id.pallino_linea);
        btnAzione = findViewById(R.id.btn_azione);
        inTitolo = findViewById(R.id.in_titolo);
        inLuogo = findViewById(R.id.in_luogo);
        inNote = findViewById(R.id.in_note);
        swTuttoGiorno = findViewById(R.id.sw_tutto_giorno);
        btnSalva = findViewById(R.id.btn_salva);

        inTitolo.setOnFocusChangeListener((v, f) -> erroreTitolo.setVisibility(View.GONE));
        findViewById(R.id.riga_tutto_giorno).setOnClickListener(v -> swTuttoGiorno.toggle());
        swTuttoGiorno.setOnCheckedChangeListener((b, attivo) -> {
            if (evento.inizio == null || attivo == evento.tuttoIlGiorno) return;
            evento.tuttoIlGiorno = attivo;
            if (attivo) {
                if (evento.fine.toLocalDate().isBefore(evento.inizio.toLocalDate())) evento.fine = evento.inizio;
            } else {
                evento.fine = evento.inizio.plusMinutes(durataMinuti);
            }
            aggiornaOrari();
        });
        valDataInizio.setOnClickListener(v -> scegliData(TAG_DATA_INIZIO, evento.inizio.toLocalDate()));
        valDataFine.setOnClickListener(v -> scegliData(TAG_DATA_FINE, evento.fine.toLocalDate()));
        valOraInizio.setOnClickListener(v -> scegliOra());
        rigaDurata.setOnClickListener(v -> menuDurata());
        valDurata.setOnClickListener(v -> menuDurata());
        View.OnClickListener linea = v -> menuLinea();
        findViewById(R.id.riga_linea).setOnClickListener(linea);
        valLinea.setOnClickListener(linea);
        rigaAggiungiPromemoria.setOnClickListener(v -> menuPromemoria());
        findViewById(R.id.riga_luogo_note).setOnClickListener(v -> {
            boolean apri = gruppoLuogoNote.getVisibility() != View.VISIBLE;
            gruppoLuogoNote.setVisibility(apri ? View.VISIBLE : View.GONE);
            if (apri) inLuogo.requestFocus();
        });
        btnSalva.setOnClickListener(v -> salva());
        ricollegaSelettori();

        long id = getIntent().getLongExtra(EXTRA_ID_EVENTO, -1);
        Bundle stato = savedInstanceState;
        final Context contesto = this;
        esecutore.execute(() -> {
            List<Linee.Linea> trovate = new ArrayList<>();
            Calendario.Evento letto = null;
            try {
                trovate = lineeScrivibili(contesto);
                if (id >= 0) letto = Calendario.leggi(getContentResolver(), id);
            } catch (SecurityException e) {
                // senza permesso il modulo resta vuoto: lo segnala il salvataggio
            }
            List<Linee.Linea> lineeTrovate = trovate;
            Calendario.Evento eventoLetto = letto;
            principale.post(() -> {
                if (isDestroyed()) return;
                if (id >= 0 && eventoLetto == null) {
                    Toast.makeText(this, R.string.errore_lettura, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                inizializza(lineeTrovate, eventoLetto, stato);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdown();
    }

    /** Linee in cui si puo' salvare, coi codici assegnati su tutte le linee (come nella scheda LINES). */
    private static List<Linee.Linea> lineeScrivibili(Context c) {
        List<Linee.Linea> tutte = Linee.da(c, Calendario.calendari(c.getContentResolver(), false));
        Set<Long> scrivibili = new HashSet<>();
        for (Calendario.InfoCalendario i : Calendario.calendari(c.getContentResolver(), true)) scrivibili.add(i.id);
        List<Linee.Linea> risultato = new ArrayList<>();
        for (Linee.Linea l : tutte) if (scrivibili.contains(l.id)) risultato.add(l);
        return risultato;
    }

    private void inizializza(List<Linee.Linea> trovate, Calendario.Evento letto, Bundle stato) {
        boolean modifica = letto != null;
        if (modifica) {
            evento = letto;
            titoloFoglio.setText(R.string.titolo_modifica);
            btnAzione.setImageResource(R.drawable.ic_elimina);
            btnAzione.setContentDescription(getString(R.string.elimina));
            btnAzione.setOnClickListener(v -> confermaElimina());
            if (stato == null) {
                inTitolo.setText(evento.titolo);
                inLuogo.setText(evento.luogo);
                inNote.setText(evento.note);
            }
            if (!TextUtils.isEmpty(evento.luogo) || !TextUtils.isEmpty(evento.note)) {
                gruppoLuogoNote.setVisibility(View.VISIBLE);
            }
            impostaPromemoria(evento.promemoria);
        } else {
            evento = nuovoEvento(giornoRichiesto());
            impostaPromemoria(promemoriaPredefiniti());
            btnAzione.setOnClickListener(v -> ripristina());
            if (stato == null) inTitolo.requestFocus();
        }
        if (!evento.tuttoIlGiorno) {
            durataMinuti = (int) Math.max(0, Duration.between(evento.inizio, evento.fine).toMinutes());
        }

        // Linea: quella dell'evento, altrimenti l'ultima usata, altrimenti la prima
        long preferita = modifica ? letto.idCalendario : prefs.getLong(PREF_ULTIMA_LINEA, -1);
        if (stato != null) {
            evento.inizio = LocalDateTime.parse(stato.getString(STATO_INIZIO, evento.inizio.toString()));
            evento.fine = LocalDateTime.parse(stato.getString(STATO_FINE, evento.fine.toString()));
            evento.tuttoIlGiorno = stato.getBoolean(STATO_TUTTO_GIORNO, evento.tuttoIlGiorno);
            durataMinuti = stato.getInt(STATO_DURATA, durataMinuti);
            preferita = stato.getLong(STATO_LINEA, preferita);
            int[] salvati = stato.getIntArray(STATO_PROMEMORIA);
            if (salvati != null) {
                List<Integer> l = new ArrayList<>();
                for (int m : salvati) l.add(m);
                impostaPromemoria(l);
            }
            if (stato.getBoolean(STATO_LUOGO_NOTE)) gruppoLuogoNote.setVisibility(View.VISIBLE);
        }
        impostaLinee(trovate, preferita);
        mostraPromemoria();

        swTuttoGiorno.setChecked(evento.tuttoIlGiorno);
        if (evento.ricorrente) {
            findViewById(R.id.nota_ricorrente).setVisibility(View.VISIBLE);
            for (View v : new View[]{swTuttoGiorno, findViewById(R.id.riga_tutto_giorno), valDataInizio,
                    valOraInizio, valDataFine, valDurata, rigaDurata}) {
                v.setEnabled(false);
            }
        }
        aggiornaOrari();
        modulo.setVisibility(View.VISIBLE);

        if (linee.isEmpty() && evento.id < 0) proponiLineaPredefinita();
    }

    private LocalDate giornoRichiesto() {
        return LocalDate.ofEpochDay(getIntent().getLongExtra(EXTRA_GIORNO, LocalDate.now().toEpochDay()));
    }

    /** ↺ del video: rimette il modulo come appena aperto. */
    private void ripristina() {
        inTitolo.setText("");
        inLuogo.setText("");
        inNote.setText("");
        gruppoLuogoNote.setVisibility(View.GONE);
        evento = nuovoEvento(giornoRichiesto());
        durataMinuti = 30;
        impostaPromemoria(promemoriaPredefiniti());
        swTuttoGiorno.setChecked(false);
        mostraPromemoria();
        aggiornaOrari();
        inTitolo.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (evento.inizio == null) return;
        out.putString(STATO_INIZIO, evento.inizio.toString());
        out.putString(STATO_FINE, evento.fine.toString());
        out.putBoolean(STATO_TUTTO_GIORNO, evento.tuttoIlGiorno);
        out.putInt(STATO_DURATA, durataMinuti);
        if (indiceLinea >= 0) out.putLong(STATO_LINEA, linee.get(indiceLinea).id);
        int[] minuti = new int[promemoria.size()];
        for (int k = 0; k < minuti.length; k++) minuti[k] = promemoria.get(k);
        out.putIntArray(STATO_PROMEMORIA, minuti);
        out.putBoolean(STATO_LUOGO_NOTE, gruppoLuogoNote.getVisibility() == View.VISIBLE);
    }

    /** Oggi: dalla prossima mezz'ora; altri giorni: alle 9. Durata 30 minuti come nel video. */
    private static Calendario.Evento nuovoEvento(LocalDate giorno) {
        Calendario.Evento e = new Calendario.Evento();
        LocalDateTime adesso = LocalDateTime.now();
        if (giorno.equals(adesso.toLocalDate())) {
            LocalDateTime ora = adesso.truncatedTo(ChronoUnit.HOURS);
            e.inizio = adesso.getMinute() < 30 ? ora.plusMinutes(30) : ora.plusHours(1);
        } else {
            e.inizio = giorno.atTime(9, 0);
        }
        e.fine = e.inizio.plusMinutes(30);
        return e;
    }

    // ---- Linea ----

    private void impostaLinee(List<Linee.Linea> trovate, long preferita) {
        linee = trovate;
        indiceLinea = linee.isEmpty() ? -1 : 0;
        for (int k = 0; k < linee.size(); k++) if (linee.get(k).id == preferita) indiceLinea = k;
        mostraLinea();
    }

    private void mostraLinea() {
        if (indiceLinea < 0) {
            pallinoLinea.setImageDrawable(null);
            valLinea.setText("—");
            return;
        }
        Linee.Linea l = linee.get(indiceLinea);
        pallinoLinea.setImageDrawable(new DrawablePallino(l.colore, l.codice, -1));
        valLinea.setText(l.nome);
    }

    /** Elenco delle linee col pallino, come le altre scelte del modulo. La linea si sceglie solo per i nuovi eventi. */
    private void menuLinea() {
        if (evento.id >= 0 || linee.isEmpty()) return;
        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(valLinea);
        popup.setWidth(dp(240));
        popup.setModal(true);
        List<String> nomi = new ArrayList<>();
        for (Linee.Linea l : linee) nomi.add(l.nome);
        popup.setAdapter(new ArrayAdapter<String>(this, R.layout.voce_menu, nomi) {
            @Override
            public View getView(int posizione, View riciclata, ViewGroup genitore) {
                TextView t = (TextView) super.getView(posizione, riciclata, genitore);
                Linee.Linea l = linee.get(posizione);
                int lato = dp(24);
                t.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        new DrawablePallino(l.colore, l.codice, lato), null, null, null);
                t.setCompoundDrawablePadding(dp(12));
                return t;
            }
        });
        popup.setOnItemClickListener((p, v, pos, idVoce) -> {
            indiceLinea = pos;
            mostraLinea();
            popup.dismiss();
        });
        popup.show();
    }

    // ---- Durata e promemoria ----

    private String testoDurata(int minuti) {
        if (minuti < 60) return getString(R.string.durata_min, minuti);
        return minuti % 60 == 0 ? getString(R.string.durata_ore, minuti / 60)
                : getString(R.string.durata_ore_min, minuti / 60, minuti % 60);
    }

    private void menuDurata() {
        if (evento.ricorrente || evento.tuttoIlGiorno) return;
        PopupMenu menu = new PopupMenu(this, valDurata);
        List<Integer> durate = new ArrayList<>();
        for (int d : DURATE) durate.add(d);
        if (!durate.contains(durataMinuti)) {
            durate.add(durataMinuti);
            durate.sort(Integer::compare);
        }
        for (int k = 0; k < durate.size(); k++) menu.getMenu().add(0, k, k, testoDurata(durate.get(k)));
        menu.setOnMenuItemClickListener(item -> {
            durataMinuti = durate.get(item.getItemId());
            evento.fine = evento.inizio.plusMinutes(durataMinuti);
            aggiornaOrari();
            return true;
        });
        menu.show();
    }

    /** "At start", "10 min before", "2 hr before", "1 day before", "1 week before"... */
    static String testoPromemoria(Context c, int minuti) {
        if (minuti <= 0) return c.getString(R.string.promemoria_inizio);
        if (minuti == 10080) return c.getString(R.string.promemoria_settimana);
        if (minuti % 1440 == 0) {
            return minuti == 1440 ? c.getString(R.string.promemoria_giorno)
                    : c.getString(R.string.promemoria_giorni, minuti / 1440);
        }
        if (minuti % 60 == 0) return c.getString(R.string.promemoria_ore, minuti / 60);
        return c.getString(R.string.promemoria_min, minuti);
    }

    /** Il promemoria scelto nelle impostazioni per i nuovi eventi (nessuno se disattivato). */
    private List<Integer> promemoriaPredefiniti() {
        List<Integer> l = new ArrayList<>();
        int minuti = Preferenze.promemoriaPredefinito(this);
        if (minuti >= 0) l.add(minuti);
        return l;
    }

    private void impostaPromemoria(List<Integer> minuti) {
        List<Integer> nuovi = new ArrayList<>();
        for (int m : minuti) if (!nuovi.contains(m)) nuovi.add(m);
        nuovi.sort(Integer::compare);
        promemoria.clear();
        promemoria.addAll(nuovi);
        mostraPromemoria();
    }

    private void mostraPromemoria() {
        listaPromemoria.removeAllViews();
        for (int minuti : promemoria) {
            View riga = getLayoutInflater().inflate(R.layout.riga_promemoria, listaPromemoria, false);
            ((TextView) riga.findViewById(R.id.testo)).setText(testoPromemoria(this, minuti));
            riga.findViewById(R.id.btn_rimuovi).setOnClickListener(v -> {
                promemoria.remove(Integer.valueOf(minuti));
                mostraPromemoria();
            });
            listaPromemoria.addView(riga);
        }
        valNessunPromemoria.setVisibility(promemoria.isEmpty() ? View.VISIBLE : View.GONE);
        rigaAggiungiPromemoria.setVisibility(promemoria.size() < MAX_PROMEMORIA ? View.VISIBLE : View.GONE);
    }

    private void menuPromemoria() {
        PopupMenu menu = new PopupMenu(this, rigaAggiungiPromemoria);
        for (int k = 0; k < MINUTI_PROMEMORIA.length; k++) {
            if (promemoria.contains(MINUTI_PROMEMORIA[k])) continue;
            menu.getMenu().add(0, k, k, testoPromemoria(this, MINUTI_PROMEMORIA[k]));
        }
        menu.setOnMenuItemClickListener(item -> {
            List<Integer> l = new ArrayList<>(promemoria);
            l.add(MINUTI_PROMEMORIA[item.getItemId()]);
            impostaPromemoria(l);
            return true;
        });
        menu.show();
    }

    // ---- Data e ora ----

    private void aggiornaOrari() {
        valDataInizio.setText(evento.inizio.format(fmtData));
        valOraInizio.setText(evento.inizio.format(fmtOra));
        valDataFine.setText(evento.fine.format(fmtData));
        valOraInizio.setVisibility(evento.tuttoIlGiorno ? View.GONE : View.VISIBLE);
        rigaFine.setVisibility(evento.tuttoIlGiorno ? View.VISIBLE : View.GONE);
        rigaDurata.setVisibility(evento.tuttoIlGiorno ? View.GONE : View.VISIBLE);
        valDurata.setText(testoDurata(durataMinuti));
        erroreOrario.setVisibility(orariValidi() ? View.GONE : View.VISIBLE);
    }

    private boolean orariValidi() {
        if (evento.tuttoIlGiorno) return !evento.fine.toLocalDate().isBefore(evento.inizio.toLocalDate());
        return !evento.fine.isBefore(evento.inizio);
    }

    private void scegliData(String tag, LocalDate attuale) {
        if (getSupportFragmentManager().findFragmentByTag(tag) != null) return;
        MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                .setSelection(Calendario.millisUtc(attuale))
                .build();
        collegaData(p, tag);
        p.show(getSupportFragmentManager(), tag);
    }

    private void scegliOra() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_ORA_INIZIO) != null) return;
        MaterialTimePicker p = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(evento.inizio.getHour())
                .setMinute(evento.inizio.getMinute())
                .build();
        collegaOra(p);
        p.show(getSupportFragmentManager(), TAG_ORA_INIZIO);
    }

    /** Dopo una rotazione i selettori aperti tornano senza ascoltatori: li ricollego. */
    @SuppressWarnings("unchecked")
    private void ricollegaSelettori() {
        for (String tag : new String[]{TAG_DATA_INIZIO, TAG_DATA_FINE}) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
            if (f instanceof MaterialDatePicker) collegaData((MaterialDatePicker<Long>) f, tag);
        }
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_ORA_INIZIO);
        if (f instanceof MaterialTimePicker) collegaOra((MaterialTimePicker) f);
    }

    private void collegaData(MaterialDatePicker<Long> p, String tag) {
        p.addOnPositiveButtonClickListener(millis -> {
            if (millis == null || evento.inizio == null) return;
            LocalDate data = Calendario.dataUtc(millis);
            if (TAG_DATA_INIZIO.equals(tag)) {
                // Spostando l'inizio la durata resta la stessa
                Duration durata = Duration.between(evento.inizio, evento.fine);
                evento.inizio = data.atTime(evento.inizio.toLocalTime());
                evento.fine = evento.inizio.plus(durata);
            } else {
                evento.fine = data.atTime(evento.fine.toLocalTime());
            }
            aggiornaOrari();
        });
    }

    private void collegaOra(MaterialTimePicker p) {
        p.addOnPositiveButtonClickListener(v -> {
            if (evento.inizio == null) return;
            Duration durata = Duration.between(evento.inizio, evento.fine);
            evento.inizio = evento.inizio.toLocalDate().atTime(p.getHour(), p.getMinute());
            evento.fine = evento.inizio.plus(durata);
            aggiornaOrari();
        });
    }

    // ---- Linea predefinita ----

    private void proponiLineaPredefinita() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nessuna_linea_titolo)
                .setMessage(R.string.nessuna_linea_evento)
                .setPositiveButton(R.string.crea, (d, w) -> creaLineaPredefinita())
                .setNegativeButton(R.string.annulla, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void creaLineaPredefinita() {
        String nome = getString(R.string.nome_linea_predefinita);
        int colore = ContextCompat.getColor(this, R.color.linea_blu);
        final Context contesto = this;
        esecutore.execute(() -> {
            long id = Calendario.creaCalendarioLocale(getContentResolver(), nome, colore);
            List<Linee.Linea> trovate = lineeScrivibili(contesto);
            principale.post(() -> {
                if (!isDestroyed()) impostaLinee(trovate, id);
            });
        });
    }

    // ---- Salva / elimina ----

    private void salva() {
        if (salvataggioInCorso || evento.inizio == null) return;
        String titolo = inTitolo.getText() == null ? "" : inTitolo.getText().toString().trim();
        if (titolo.isEmpty()) {
            erroreTitolo.setVisibility(View.VISIBLE);
            inTitolo.requestFocus();
            return;
        }
        if (!orariValidi()) {
            erroreOrario.setVisibility(View.VISIBLE);
            return;
        }
        if (evento.id < 0 && indiceLinea < 0) {
            proponiLineaPredefinita();
            return;
        }
        evento.titolo = titolo;
        evento.luogo = inLuogo.getText().toString().trim();
        evento.note = inNote.getText().toString().trim();
        evento.promemoria = new ArrayList<>(promemoria);
        if (evento.id < 0) evento.idCalendario = linee.get(indiceLinea).id;

        salvataggioInCorso = true;
        btnSalva.setEnabled(false);
        Calendario.Evento daSalvare = evento;
        esecutore.execute(() -> {
            boolean ok;
            try {
                Calendario.salva(getContentResolver(), daSalvare);
                ok = true;
            } catch (RuntimeException e) {
                ok = false;
            }
            boolean riuscito = ok;
            principale.post(() -> {
                if (isDestroyed()) return;
                if (riuscito) {
                    if (daSalvare.idCalendario >= 0) {
                        prefs.edit().putLong(PREF_ULTIMA_LINEA, daSalvare.idCalendario).apply();
                    }
                    AggiornaWidget.richiedi(this);
                    // Con dei promemoria servono le notifiche: su Android 13+ vanno chieste
                    if (!daSalvare.promemoria.isEmpty() && Promemoria.serveRichiesta(this)) {
                        richiestaNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS);
                    } else {
                        finish();
                    }
                } else {
                    salvataggioInCorso = false;
                    btnSalva.setEnabled(true);
                    Toast.makeText(this, R.string.errore_salvataggio, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void confermaElimina() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(evento.ricorrente ? R.string.conferma_elimina_ricorrente : R.string.conferma_elimina)
                .setPositiveButton(R.string.elimina, (d, w) -> {
                    long id = evento.id;
                    esecutore.execute(() -> {
                        try {
                            Calendario.elimina(getContentResolver(), id);
                        } catch (RuntimeException ignorata) {
                            // L'evento potrebbe essere gia' sparito: torniamo comunque al tabellone
                        }
                        principale.post(() -> {
                            AggiornaWidget.richiedi(this);
                            finish();
                        });
                    });
                })
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
