package com.calendarioanno.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Le schede del video: BOARD (il tabellone delle partenze del giorno) e LINES; ADD apre il modulo. */
public class MainActivity extends AppCompatActivity {

    private static final String[] PERMESSI = {
            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
    };
    private static final String PREF_PERMESSI_CHIESTI = "permessi_chiesti";
    private static final String STATO_SCHEDA = "scheda";
    private static final String STATO_GIORNO = "giorno";
    private static final String STATO_TUTTO_GIORNO = "tutto_giorno";
    private static final String STATO_PARTITI = "partiti";

    private static final int SCHEDA_BOARD = 0;
    private static final int SCHEDA_LINES = 1;
    private static final long INTERVALLO_CONTO_ALLA_ROVESCIA = 30_000;

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());
    private DateTimeFormatter fmtOra, fmtTitolo, fmtBreve;

    private View avvisoPermesso, paginaTabellone, paginaLinee;
    private View navBoard, navLines, navAdd;
    private Button btnPermesso;
    private TextView strisciaNome;
    // Board
    private TextView dataTabellone, pillolaTuttoGiorno, vuotoTabelloneTesto, btnOggi;
    private LinearLayout lineeGiorno, listaTabellone;
    private NestedScrollView scorrimentoTabellone;
    private View vuotoTabellone;
    // Lines
    private TextView dataLinee, nessunaLinea;
    private LinearLayout listaLinee;

    private int scheda = SCHEDA_BOARD;
    private LocalDate giorno = LocalDate.now();
    private boolean mostraTuttoGiorno, mostraPartiti;
    private LocalDate oggiVisto = LocalDate.now();

    private List<Calendario.Istanza> istanzeGiorno = Collections.emptyList();
    private List<Linee.Linea> linee = Collections.emptyList();
    private Map<Long, Linee.Linea> perId = Collections.emptyMap();
    private Map<Long, Integer> conteggiOggi = Collections.emptyMap();
    /** Aumenta a ogni caricamento: le risposte vecchie vengono scartate. */
    private int generazione;

    private GestureDetector gestiTabellone;
    private final Rect rettangolo = new Rect();

    private final ContentObserver osservatore = new ContentObserver(principale) {
        @Override
        public void onChange(boolean selfChange) {
            ricarica();
            AggiornaWidget.richiedi(MainActivity.this);
        }
    };

    /** Ogni 30 secondi: conto alla rovescia del tabellone e cambio di giorno a mezzanotte. */
    private final Runnable battito = new Runnable() {
        @Override
        public void run() {
            LocalDate oggi = LocalDate.now();
            if (!oggi.equals(oggiVisto)) {
                if (giorno.equals(oggiVisto)) giorno = oggi;
                oggiVisto = oggi;
                ricarica();
            } else if (scheda == SCHEDA_BOARD && giorno.equals(oggi)) {
                mostraTabellone();
            }
            principale.postDelayed(this, INTERVALLO_CONTO_ALLA_ROVESCIA);
        }
    };

    private final ActivityResultLauncher<String[]> richiestaPermessi =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                Preferenze.di(this).edit().putBoolean(PREF_PERMESSI_CHIESTI, true).apply();
                aggiornaPermesso();
                AggiornaWidget.richiedi(this);
            });

    private final ActivityResultLauncher<Intent> sceltaGiorno =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    vaiAlGiorno(LocalDate.ofEpochDay(r.getData().getLongExtra(AnnoActivity.EXTRA_GIORNO,
                            giorno.toEpochDay())));
                }
            });

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Preferenze.conLingua(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main);
        Locale locale = Preferenze.locale(this);
        fmtOra = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);
        fmtTitolo = DateTimeFormatter.ofPattern(getString(R.string.formato_titolo), locale);
        fmtBreve = DateTimeFormatter.ofPattern(getString(R.string.formato_breve), locale);

        View root = findViewById(R.id.root);
        View barraNav = findViewById(R.id.barra_nav);
        strisciaNome = findViewById(R.id.striscia_nome);
        avvisoPermesso = findViewById(R.id.avviso_permesso);
        btnPermesso = findViewById(R.id.btn_permesso);
        paginaTabellone = findViewById(R.id.pagina_tabellone);
        paginaLinee = findViewById(R.id.pagina_linee);
        navBoard = findViewById(R.id.nav_board);
        navLines = findViewById(R.id.nav_lines);
        navAdd = findViewById(R.id.nav_add);
        dataTabellone = findViewById(R.id.data_tabellone);
        pillolaTuttoGiorno = findViewById(R.id.pillola_tutto_giorno);
        vuotoTabelloneTesto = findViewById(R.id.vuoto_tabellone_testo);
        btnOggi = findViewById(R.id.btn_oggi);
        lineeGiorno = findViewById(R.id.linee_giorno);
        listaTabellone = findViewById(R.id.lista_tabellone);
        scorrimentoTabellone = findViewById(R.id.scorrimento_tabellone);
        vuotoTabellone = findViewById(R.id.vuoto_tabellone);
        dataLinee = findViewById(R.id.data_linee);
        nessunaLinea = findViewById(R.id.nessuna_linea);
        listaLinee = findViewById(R.id.lista_linee);

        // Edge-to-edge: spazio per barra di stato in alto e barra di navigazione sotto le schede
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(barre.left, barre.top, barre.right, 0);
            barraNav.setPadding(0, 0, 0, barre.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState != null) {
            scheda = savedInstanceState.getInt(STATO_SCHEDA, SCHEDA_BOARD);
            giorno = LocalDate.ofEpochDay(savedInstanceState.getLong(STATO_GIORNO, giorno.toEpochDay()));
            mostraTuttoGiorno = savedInstanceState.getBoolean(STATO_TUTTO_GIORNO);
            mostraPartiti = savedInstanceState.getBoolean(STATO_PARTITI);
        }

        preparaVoce(navBoard, R.drawable.ic_tabellone, R.string.nav_board);
        preparaVoce(navLines, R.drawable.ic_linee, R.string.nav_lines);
        preparaVoce(navAdd, R.drawable.ic_aggiungi, R.string.nav_add);
        navBoard.setOnClickListener(v -> {
            // Toccare di nuovo BOARD riporta a oggi
            if (scheda == SCHEDA_BOARD) vaiAlGiorno(LocalDate.now());
            else mostraScheda(SCHEDA_BOARD);
        });
        navLines.setOnClickListener(v -> mostraScheda(SCHEDA_LINES));
        navAdd.setOnClickListener(v -> nuovoEvento(scheda == SCHEDA_BOARD ? giorno : LocalDate.now()));

        // Board
        dataTabellone.setOnClickListener(v -> sceltaGiorno.launch(new Intent(this, AnnoActivity.class)
                .putExtra(AnnoActivity.EXTRA_GIORNO, giorno.toEpochDay())));
        btnOggi.setOnClickListener(v -> vaiAlGiorno(LocalDate.now()));
        pillolaTuttoGiorno.setOnClickListener(v -> {
            mostraTuttoGiorno = !mostraTuttoGiorno;
            mostraTabellone();
        });
        View.OnClickListener apriImpostazioni = v -> startActivity(new Intent(this, ImpostazioniActivity.class));
        findViewById(R.id.btn_impostazioni).setOnClickListener(apriImpostazioni);
        findViewById(R.id.btn_impostazioni_linee).setOnClickListener(apriImpostazioni);
        gestiTabellone = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                float dp = getResources().getDisplayMetrics().density;
                if (Math.abs(vx) < 600 * dp || Math.abs(vx) < Math.abs(vy) * 1.5f) return false;
                cambiaGiorno(vx < 0 ? 1 : -1);
                return true;
            }
        });

        // Lines
        findViewById(R.id.btn_aggiungi_linea).setOnClickListener(v -> {
            if (haPermesso()) startActivity(new Intent(this, LineaActivity.class));
            else chiediPermesso();
        });

        btnPermesso.setOnClickListener(v -> chiediPermesso());

        mostraScheda(scheda);
        if (!haPermesso() && !Preferenze.di(this).getBoolean(PREF_PERMESSI_CHIESTI, false)) {
            richiestaPermessi.launch(PERMESSI);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        strisciaNome.setText(Preferenze.nomeApp(this));
        aggiornaPermesso();
        principale.postDelayed(battito, INTERVALLO_CONTO_ALLA_ROVESCIA);
    }

    @Override
    protected void onStop() {
        super.onStop();
        principale.removeCallbacks(battito);
        getContentResolver().unregisterContentObserver(osservatore);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATO_SCHEDA, scheda);
        out.putLong(STATO_GIORNO, giorno.toEpochDay());
        out.putBoolean(STATO_TUTTO_GIORNO, mostraTuttoGiorno);
        out.putBoolean(STATO_PARTITI, mostraPartiti);
    }

    /** Scorrimento di lato sul tabellone: cambia giorno anche se il dito parte da una riga. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (scheda == SCHEDA_BOARD && gestiTabellone != null) {
            scorrimentoTabellone.getGlobalVisibleRect(rettangolo);
            if (ev.getActionMasked() != MotionEvent.ACTION_DOWN
                    || rettangolo.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                gestiTabellone.onTouchEvent(ev);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---- Barra in basso ----

    private void preparaVoce(View voce, int icona, int etichetta) {
        ((ImageView) voce.findViewById(R.id.icona)).setImageResource(icona);
        ((TextView) voce.findViewById(R.id.etichetta)).setText(etichetta);
    }

    private void evidenzia(View voce, boolean attiva) {
        int colore = ContextCompat.getColor(this, attiva ? R.color.giallo : R.color.grigio);
        voce.findViewById(R.id.indicatore).setVisibility(attiva ? View.VISIBLE : View.INVISIBLE);
        ((ImageView) voce.findViewById(R.id.icona)).setImageTintList(ColorStateList.valueOf(colore));
        ((TextView) voce.findViewById(R.id.etichetta)).setTextColor(colore);
        voce.setSelected(attiva);
    }

    private void mostraScheda(int s) {
        scheda = s;
        paginaTabellone.setVisibility(s == SCHEDA_BOARD ? View.VISIBLE : View.GONE);
        paginaLinee.setVisibility(s == SCHEDA_LINES ? View.VISIBLE : View.GONE);
        evidenzia(navBoard, s == SCHEDA_BOARD);
        evidenzia(navLines, s == SCHEDA_LINES);
        evidenzia(navAdd, false);
        if (s == SCHEDA_BOARD) mostraTabellone();
        else mostraLinee();
    }

    // ---- Permessi ----

    private boolean haPermesso() {
        for (String p : PERMESSI) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** Rifiutato "per sempre": il sistema non mostra piu' la richiesta, serve andare nelle impostazioni. */
    private boolean permessoBloccato() {
        return Preferenze.di(this).getBoolean(PREF_PERMESSI_CHIESTI, false)
                && !shouldShowRequestPermissionRationale(PERMESSI[0]);
    }

    private void aggiornaPermesso() {
        boolean ok = haPermesso();
        avvisoPermesso.setVisibility(ok ? View.GONE : View.VISIBLE);
        getContentResolver().unregisterContentObserver(osservatore);
        if (ok) {
            getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI, true, osservatore);
            ricarica();
        } else {
            btnPermesso.setText(permessoBloccato() ? R.string.permesso_impostazioni : R.string.permesso_consenti);
            mostraTabellone();
        }
    }

    private void chiediPermesso() {
        if (permessoBloccato()) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } else {
            richiestaPermessi.launch(PERMESSI);
        }
    }

    // ---- Dati ----

    private void ricarica() {
        if (!haPermesso()) return;
        final LocalDate g = giorno;
        final int gen = ++generazione;
        final Context contesto = this;
        esecutore.execute(() -> {
            List<Linee.Linea> lin;
            List<Calendario.Istanza> delGiorno, diOggi;
            try {
                lin = Linee.da(contesto, Calendario.calendari(getContentResolver(), false));
                Set<Long> nascoste = Linee.nascoste(contesto);
                delGiorno = Calendario.istanze(getContentResolver(), g, g.plusDays(1), nascoste);
                LocalDate oggi = LocalDate.now();
                diOggi = g.equals(oggi) ? null : Calendario.istanze(getContentResolver(), oggi, oggi.plusDays(1), null);
            } catch (SecurityException e) {
                return;
            }
            Map<Long, Integer> conteggi = new HashMap<>();
            for (Calendario.Istanza i : diOggi != null ? diOggi : delGiorno) {
                conteggi.merge(i.idCalendario, 1, Integer::sum);
            }
            principale.post(() -> {
                if (isDestroyed() || gen != generazione) return;
                linee = lin;
                perId = Linee.mappa(lin);
                conteggiOggi = conteggi;
                istanzeGiorno = delGiorno;
                mostraTabellone();
                mostraLinee();
            });
        });
    }

    /** Colore e codice della linea di un evento (se la linea non c'e' piu', quelli del calendario). */
    private int colore(Calendario.Istanza i) {
        Linee.Linea l = perId.get(i.idCalendario);
        return l != null ? l.colore : i.colore;
    }

    private String codice(Calendario.Istanza i) {
        Linee.Linea l = perId.get(i.idCalendario);
        return l != null ? l.codice : DrawablePallino.iniziale(i.nomeCalendario);
    }

    // ---- Board ----

    private void cambiaGiorno(int delta) {
        vaiAlGiorno(giorno.plusDays(delta));
        listaTabellone.setTranslationX(delta * 40 * getResources().getDisplayMetrics().density);
        listaTabellone.setAlpha(0f);
        listaTabellone.animate().translationX(0).alpha(1f).setDuration(200).start();
    }

    private void vaiAlGiorno(LocalDate g) {
        if (scheda != SCHEDA_BOARD) mostraScheda(SCHEDA_BOARD);
        if (!g.equals(giorno)) {
            giorno = g;
            mostraPartiti = false;
            istanzeGiorno = Collections.emptyList();
        }
        mostraTabellone();
        ricarica();
        scorrimentoTabellone.scrollTo(0, 0);
    }

    /** "Wednesday, ⌄ / Sep 30": la freccia sta dopo la prima riga, come nel video. */
    private CharSequence titoloData() {
        String testo = giorno.format(fmtTitolo);
        testo = testo.substring(0, 1).toUpperCase(Preferenze.locale(this)) + testo.substring(1);
        int a_capo = testo.indexOf('\n');
        SpannableStringBuilder sb = new SpannableStringBuilder(testo);
        Drawable freccia = ContextCompat.getDrawable(this, R.drawable.ic_giu);
        if (freccia != null && a_capo > 0) {
            freccia = freccia.mutate();
            freccia.setTint(ContextCompat.getColor(this, R.color.bianco));
            int lato = Math.round(dataTabellone.getTextSize() * 0.7f);
            freccia.setBounds(0, 0, lato, lato);
            sb.insert(a_capo, "  ");
            int allineamento = android.os.Build.VERSION.SDK_INT >= 29 ? ImageSpan.ALIGN_CENTER : ImageSpan.ALIGN_BASELINE;
            sb.setSpan(new ImageSpan(freccia, allineamento), a_capo + 1, a_capo + 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void mostraTabellone() {
        LocalDate oggi = LocalDate.now();
        boolean eOggi = giorno.equals(oggi);
        dataTabellone.setText(titoloData());
        btnOggi.setVisibility(eOggi ? View.GONE : View.VISIBLE);

        List<Calendario.Istanza> giornata = new ArrayList<>(istanzeGiorno);
        giornata.sort(Calendario.ORDINE);
        List<Calendario.Istanza> tuttoGiorno = new ArrayList<>();
        List<Calendario.Istanza> attivi = new ArrayList<>();
        List<Calendario.Istanza> partiti = new ArrayList<>();
        long adesso = System.currentTimeMillis();
        for (Calendario.Istanza i : giornata) {
            if (i.tuttoIlGiorno) tuttoGiorno.add(i);
            else if (eOggi && i.fine <= adesso) partiti.add(i);
            else attivi.add(i);
        }

        // Pallini delle linee che hanno eventi in questo giorno
        lineeGiorno.removeAllViews();
        Map<Long, Calendario.Istanza> perLinea = new LinkedHashMap<>();
        for (Calendario.Istanza i : giornata) perLinea.putIfAbsent(i.idCalendario, i);
        int lato = dp(26);
        for (Calendario.Istanza i : perLinea.values()) {
            if (lineeGiorno.getChildCount() >= 7) break;
            ImageView pallino = new ImageView(this);
            pallino.setImageDrawable(new DrawablePallino(colore(i), codice(i), -1));
            pallino.setContentDescription(i.nomeCalendario);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(lato, lato);
            lp.setMarginEnd(dp(10));
            lineeGiorno.addView(pallino, lp);
        }

        pillolaTuttoGiorno.setVisibility(tuttoGiorno.isEmpty() ? View.GONE : View.VISIBLE);
        pillolaTuttoGiorno.setText(getString(R.string.tutto_il_giorno_n, tuttoGiorno.size()));
        pillolaTuttoGiorno.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                mostraTuttoGiorno ? R.color.giallo : R.color.cerchio_scuro)));
        pillolaTuttoGiorno.setTextColor(ContextCompat.getColor(this,
                mostraTuttoGiorno ? R.color.inchiostro : R.color.bianco));

        listaTabellone.removeAllViews();
        if (mostraTuttoGiorno) {
            for (Calendario.Istanza i : tuttoGiorno) listaTabellone.addView(rigaPartenza(i, eOggi, adesso, false));
        }
        boolean primaInCorso = true;
        for (Calendario.Istanza i : attivi) {
            boolean inCorso = eOggi && i.inizio <= adesso && adesso < i.fine;
            listaTabellone.addView(rigaPartenza(i, eOggi, adesso, inCorso && primaInCorso));
            if (inCorso) primaInCorso = false;
        }
        if (!partiti.isEmpty()) {
            listaTabellone.addView(rigaPartiti(partiti.size()));
            if (mostraPartiti) {
                for (Calendario.Istanza i : partiti) listaTabellone.addView(rigaPartenza(i, eOggi, adesso, false));
            }
        }

        boolean vuoto = attivi.isEmpty() && (tuttoGiorno.isEmpty() || !mostraTuttoGiorno) && !mostraPartiti;
        vuotoTabellone.setVisibility(vuoto && haPermesso() ? View.VISIBLE : View.GONE);
        vuotoTabelloneTesto.setText(eOggi ? R.string.nessuna_partenza_oggi : R.string.nessuna_partenza_giorno);
    }

    private View rigaPartiti(int quanti) {
        TextView t = new TextView(this);
        t.setText(mostraPartiti ? getString(R.string.nascondi_partiti) : getString(R.string.mostra_partiti, quanti));
        t.setAllCaps(true);
        t.setLetterSpacing(0.1f);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.inchiostro_tenue));
        t.setPadding(dp(62), dp(14), dp(16), dp(14));
        TypedValue sfondo = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, sfondo, true);
        t.setBackgroundResource(sfondo.resourceId);
        t.setOnClickListener(v -> {
            mostraPartiti = !mostraPartiti;
            mostraTabellone();
        });
        return t;
    }

    /**
     * Una riga del tabellone. {@code gialla}: il primo evento in corso, evidenziato come nel video;
     * gli altri in corso hanno l'etichetta LIVE e la barra, su fondo chiaro.
     */
    private View rigaPartenza(Calendario.Istanza i, boolean eOggi, long adesso, boolean gialla) {
        View riga = LayoutInflater.from(this).inflate(R.layout.riga_partenza, listaTabellone, false);
        ImageView pallino = riga.findViewById(R.id.pallino);
        TextView etichetta = riga.findViewById(R.id.etichetta);
        TextView titolo = riga.findViewById(R.id.titolo);
        TextView luogo = riga.findViewById(R.id.luogo);
        TextView orario = riga.findViewById(R.id.orario);
        TextView numero = riga.findViewById(R.id.numero);
        TextView unita = riga.findViewById(R.id.unita);
        LinearProgressIndicator avanzamento = riga.findViewById(R.id.avanzamento);

        DrawablePallino d = new DrawablePallino(colore(i), codice(i), -1);
        d.setContorno(gialla && DrawablePallino.chiaro(colore(i)));
        pallino.setImageDrawable(d);
        titolo.setText(TextUtils.isEmpty(i.titolo) ? getString(R.string.senza_titolo) : i.titolo);
        if (!TextUtils.isEmpty(i.luogo)) {
            luogo.setText(i.luogo);
            luogo.setVisibility(View.VISIBLE);
        }

        ZoneId zona = ZoneId.systemDefault();
        String oraInizio = LocalDateTime.ofInstant(Instant.ofEpochMilli(i.inizio), zona).format(fmtOra);
        String oraFine = LocalDateTime.ofInstant(Instant.ofEpochMilli(i.fine), zona).format(fmtOra);

        if (i.tuttoIlGiorno) {
            numero.setVisibility(View.GONE);
            unita.setText(R.string.tutto_il_giorno);
            orario.setText(i.primoGiorno.equals(i.ultimoGiorno) ? getString(R.string.tutto_il_giorno)
                    : getString(R.string.intervallo, i.primoGiorno.format(fmtBreve), i.ultimoGiorno.format(fmtBreve)));
        } else {
            orario.setText(oraInizio);
            boolean iniziaPrima = i.primoGiorno.isBefore(giorno);
            boolean continuaDopo = i.ultimoGiorno.isAfter(giorno);
            if (eOggi && i.inizio <= adesso && adesso < i.fine) {
                // In corso: etichetta LIVE, minuti alla fine e barra di avanzamento
                if (gialla) {
                    riga.setBackgroundColor(ContextCompat.getColor(this, R.color.giallo));
                    // Il grigio del luogo sul giallo si legge poco
                    luogo.setTextColor(ContextCompat.getColor(this, R.color.inchiostro_su_giallo));
                }
                etichetta.setVisibility(View.VISIBLE);
                long rimasti = minutiArrotondati(i.fine - adesso);
                numero.setText(rimasti < 60 ? String.valueOf(rimasti) : oreMinuti(rimasti));
                unita.setText(rimasti < 60 ? R.string.min_rimasti : R.string.ore_rimaste);
                avanzamento.setVisibility(View.VISIBLE);
                avanzamento.setProgress((int) ((adesso - i.inizio) * 1000 / Math.max(1, i.fine - i.inizio)));
            } else if (eOggi && adesso < i.inizio) {
                long mancano = minutiArrotondati(i.inizio - adesso);
                numero.setText(mancano < 60 ? String.valueOf(mancano) : oreMinuti(mancano));
                unita.setText(mancano < 60 ? R.string.min : R.string.ore);
            } else if (eOggi) {
                numero.setText(oraInizio);
                unita.setText(R.string.partito);
                riga.findViewById(R.id.contenuto).setAlpha(0.5f);
            } else if (iniziaPrima) {
                numero.setText(continuaDopo ? "—" : oraFine);
                unita.setText(continuaDopo ? R.string.giorno_intero : R.string.fino_alle);
            } else {
                numero.setText(oraInizio);
                unita.setText(durataBreve(minutiArrotondati(i.fine - i.inizio)));
            }
        }

        riga.findViewById(R.id.contenuto).setOnClickListener(v -> startActivity(
                new Intent(this, EventoActivity.class).putExtra(EventoActivity.EXTRA_ID_EVENTO, i.idEvento)));
        return riga;
    }

    private static long minutiArrotondati(long millis) {
        return Math.max(0, (millis + 59_999) / 60_000);
    }

    private static String oreMinuti(long minuti) {
        return String.format(Locale.ROOT, "%d:%02d", minuti / 60, minuti % 60);
    }

    private String durataBreve(long minuti) {
        if (minuti < 60) return getString(R.string.durata_min, (int) minuti);
        return minuti % 60 == 0 ? getString(R.string.durata_ore, (int) (minuti / 60))
                : getString(R.string.durata_ore_min, (int) (minuti / 60), (int) (minuti % 60));
    }

    // ---- Lines ----

    private void mostraLinee() {
        dataLinee.setText(LocalDate.now().format(fmtBreve));
        listaLinee.removeAllViews();
        for (Linee.Linea l : linee) {
            View riga = LayoutInflater.from(this).inflate(R.layout.riga_linea, listaLinee, false);
            ((ImageView) riga.findViewById(R.id.pallino)).setImageDrawable(new DrawablePallino(l.colore, l.codice, -1));
            ((TextView) riga.findViewById(R.id.nome)).setText(l.nome);
            int quanti = conteggiOggi.getOrDefault(l.id, 0);
            ((TextView) riga.findViewById(R.id.conteggio)).setText(l.nascosta ? getString(R.string.nascosta)
                    : quanti == 0 ? getString(R.string.nessuno_oggi) : getString(R.string.eventi_oggi, quanti));
            View contenuto = riga.findViewById(R.id.contenuto);
            contenuto.setAlpha(l.nascosta ? 0.4f : 1f);
            contenuto.setOnClickListener(v -> modificaLinea(l));
            View altro = riga.findViewById(R.id.btn_altro);
            altro.setContentDescription(l.nome);
            altro.setOnClickListener(v -> menuLinea(altro, l));
            listaLinee.addView(riga);
        }
        nessunaLinea.setVisibility(linee.isEmpty() && haPermesso() ? View.VISIBLE : View.GONE);
    }

    private void modificaLinea(Linee.Linea l) {
        startActivity(new Intent(this, LineaActivity.class).putExtra(LineaActivity.EXTRA_ID_LINEA, l.id));
    }

    private void menuLinea(View ancora, Linee.Linea l) {
        PopupMenu menu = new PopupMenu(this, ancora);
        menu.getMenu().add(0, 1, 0, R.string.modifica_linea);
        menu.getMenu().add(0, 2, 1, l.nascosta ? R.string.mostra_linea : R.string.nascondi_linea);
        if (l.creataDaApp) menu.getMenu().add(0, 3, 2, R.string.elimina_linea);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                modificaLinea(l);
            } else if (item.getItemId() == 2) {
                Linee.impostaNascosta(this, l.id, !l.nascosta);
                ricarica();
                AggiornaWidget.richiedi(this);
            } else {
                confermaEliminaLinea(this, esecutore, l, null);
            }
            return true;
        });
        menu.show();
    }

    /** Usata anche da LineaActivity: elimina una linea creata dall'app, dopo conferma. */
    static void confermaEliminaLinea(AppCompatActivity a, ExecutorService esecutore, Linee.Linea l, Runnable dopo) {
        new MaterialAlertDialogBuilder(a)
                .setMessage(a.getString(R.string.conferma_elimina_linea, l.nome))
                .setPositiveButton(R.string.elimina, (d, w) -> esecutore.execute(() -> {
                    Calendario.InfoCalendario info = new Calendario.InfoCalendario();
                    info.id = l.id;
                    info.account = l.account;
                    info.tipoAccount = CalendarContract.ACCOUNT_TYPE_LOCAL;
                    try {
                        if (l.creataDaApp) Calendario.eliminaCalendario(a.getContentResolver(), info);
                        Linee.dimentica(a, l.id);
                    } catch (RuntimeException ignorata) {
                        // gia' eliminata altrove: il ricaricamento sistemera' l'elenco
                    }
                    if (dopo != null) a.runOnUiThread(dopo);
                }))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    // ---- Varie ----

    private void nuovoEvento(LocalDate g) {
        if (!haPermesso()) {
            chiediPermesso();
            return;
        }
        startActivity(new Intent(this, EventoActivity.class).putExtra(EventoActivity.EXTRA_GIORNO, g.toEpochDay()));
    }

    private int dp(int valore) {
        return Math.round(valore * getResources().getDisplayMetrics().density);
    }
}
