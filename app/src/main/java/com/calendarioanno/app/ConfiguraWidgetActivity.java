package com.calendarioanno.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Personalizzazione di un widget: dimensione del testo, righe, sfondo (colore e trasparenza),
 * colore del testo, cosa mostrare. Si apre quando si aggiunge un widget, tenendolo premuto
 * (matita, Android 12+) o dalle impostazioni dell'app. L'anteprima e' il widget vero.
 */
public class ConfiguraWidgetActivity extends AppCompatActivity {

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());

    private int idWidget = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AggiornaWidget.Tipo tipo;
    private OpzioniWidget opzioni;

    private FrameLayout anteprima;
    private MaterialButtonToggleGroup gruppoScala;
    private Slider sliderRighe, sliderOpacita;
    private TextView valRighe, valOpacita;
    private MaterialSwitch swPallini, swData, swTuttoGiorno, swPiede, swPassati;
    private final List<DrawablePallino> palliniSfondo = new ArrayList<>();
    private final List<DrawablePallino> palliniTesto = new ArrayList<>();
    private int generazione;
    private boolean aggiornamentoInCorso;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Preferenze.conLingua(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extra = getIntent().getExtras();
        if (extra != null) idWidget = extra.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, idWidget);
        // Se si esce senza salvare, il launcher non aggiunge il widget
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, idWidget));
        if (idWidget == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_configura_widget);
        View root = findViewById(R.id.root);
        View scorrimento = findViewById(R.id.scorrimento);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(barre.left, barre.top, barre.right, 0);
            scorrimento.setPadding(0, 0, 0, barre.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        tipo = AggiornaWidget.Tipo.di(this, idWidget);
        opzioni = OpzioniWidget.carica(this, idWidget, tipo);

        ((TextView) findViewById(R.id.striscia_nome)).setText(getString(tipo.nome));
        findViewById(R.id.btn_chiudi).setOnClickListener(v -> finish());
        anteprima = findViewById(R.id.anteprima);
        gruppoScala = findViewById(R.id.gruppo_scala);
        sliderRighe = findViewById(R.id.slider_righe);
        sliderOpacita = findViewById(R.id.slider_opacita);
        valRighe = findViewById(R.id.val_righe);
        valOpacita = findViewById(R.id.val_opacita);
        swPallini = findViewById(R.id.sw_pallini);
        swData = findViewById(R.id.sw_data);
        swTuttoGiorno = findViewById(R.id.sw_tutto_giorno);
        swPiede = findViewById(R.id.sw_piede);
        swPassati = findViewById(R.id.sw_passati);
        boolean giornata = tipo == AggiornaWidget.Tipo.GIORNATA || tipo == AggiornaWidget.Tipo.GIORNATA_LARGA;
        swPassati.setVisibility(giornata ? View.VISIBLE : View.GONE);
        sliderRighe.setValueTo(tipo.maxRighe);

        // Misura dell'anteprima simile a quella del widget sulla home
        ViewGroup.LayoutParams lp = anteprima.getLayoutParams();
        lp.height = dp(tipo == AggiornaWidget.Tipo.GIORNATA ? 380 : tipo == AggiornaWidget.Tipo.GRANDE ? 320 : 170);
        if (tipo == AggiornaWidget.Tipo.PICCOLO || tipo == AggiornaWidget.Tipo.GIORNATA) lp.width = dp(170);
        anteprima.setLayoutParams(lp);

        for (int k = 0; k < OpzioniWidget.NOMI_SCALE.length; k++) {
            MaterialButton b = (MaterialButton) LayoutInflater.from(this).inflate(R.layout.voce_scala, gruppoScala, false);
            b.setId(View.generateViewId());
            b.setText(OpzioniWidget.NOMI_SCALE[k]);
            b.setTag(k);
            gruppoScala.addView(b);
        }
        preparaColori(findViewById(R.id.colori_sfondo), OpzioniWidget.COLORI_SFONDO, palliniSfondo, true);
        preparaColori(findViewById(R.id.colori_testo), OpzioniWidget.COLORI_TESTO, palliniTesto, false);

        mostraOpzioni();

        gruppoScala.addOnButtonCheckedListener((g, idBottone, scelto) -> {
            if (!scelto || aggiornamentoInCorso) return;
            opzioni.scala = (int) findViewById(idBottone).getTag();
            ridisegna();
        });
        sliderRighe.addOnChangeListener((s, valore, utente) -> {
            if (!utente) return;
            opzioni.righe = Math.round(valore);
            valRighe.setText(String.valueOf(opzioni.righe));
            ridisegna();
        });
        sliderOpacita.addOnChangeListener((s, valore, utente) -> {
            if (!utente) return;
            opzioni.opacita = Math.round(valore);
            valOpacita.setText(getString(R.string.percentuale, opzioni.opacita));
            ridisegna();
        });
        swPallini.setOnCheckedChangeListener((b, v) -> {
            opzioni.pallini = v;
            ridisegna();
        });
        swData.setOnCheckedChangeListener((b, v) -> {
            opzioni.data = v;
            ridisegna();
        });
        swTuttoGiorno.setOnCheckedChangeListener((b, v) -> {
            opzioni.tuttoGiorno = v;
            ridisegna();
        });
        swPiede.setOnCheckedChangeListener((b, v) -> {
            opzioni.piede = v;
            ridisegna();
        });
        swPassati.setOnCheckedChangeListener((b, v) -> {
            opzioni.passati = v;
            ridisegna();
        });
        findViewById(R.id.btn_ripristina).setOnClickListener(v -> {
            opzioni = OpzioniWidget.predefinite(tipo);
            mostraOpzioni();
        });
        findViewById(R.id.btn_salva).setOnClickListener(v -> salva());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdownNow();
    }

    /** Mette nei controlli i valori di {@link #opzioni} (all'avvio e dopo "ripristina"). */
    private void mostraOpzioni() {
        aggiornamentoInCorso = true;
        for (int k = 0; k < gruppoScala.getChildCount(); k++) {
            View b = gruppoScala.getChildAt(k);
            if ((int) b.getTag() == opzioni.scala) gruppoScala.check(b.getId());
        }
        sliderRighe.setValue(opzioni.righe);
        valRighe.setText(String.valueOf(opzioni.righe));
        sliderOpacita.setValue(opzioni.opacita);
        valOpacita.setText(getString(R.string.percentuale, opzioni.opacita));
        swPallini.setChecked(opzioni.pallini);
        swData.setChecked(opzioni.data);
        swTuttoGiorno.setChecked(opzioni.tuttoGiorno);
        swPiede.setChecked(opzioni.piede);
        swPassati.setChecked(opzioni.passati);
        for (int k = 0; k < palliniSfondo.size(); k++) palliniSfondo.get(k).setSelezionato(k == opzioni.coloreSfondo);
        for (int k = 0; k < palliniTesto.size(); k++) palliniTesto.get(k).setSelezionato(k == opzioni.coloreTesto);
        aggiornamentoInCorso = false;
        ridisegna();
    }

    private void preparaColori(LinearLayout riga, int[] colori, List<DrawablePallino> pallini, boolean sfondo) {
        for (int k = 0; k < colori.length; k++) {
            int indice = k;
            DrawablePallino d = new DrawablePallino(colori[k], "", -1);
            pallini.add(d);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setPadding(dp(4), dp(4), dp(4), dp(4));
            iv.setOnClickListener(v -> {
                if (sfondo) opzioni.coloreSfondo = indice;
                else opzioni.coloreTesto = indice;
                for (DrawablePallino p : pallini) p.setSelezionato(p == d);
                ridisegna();
            });
            riga.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
    }

    /** Ridisegna l'anteprima col widget vero (i dati del calendario si leggono in background). */
    private void ridisegna() {
        if (aggiornamentoInCorso) return;
        final int gen = ++generazione;
        final OpzioniWidget copia = opzioni.copia();
        final Context app = getApplicationContext();
        esecutore.execute(() -> {
            RemoteViews rv = AggiornaWidget.costruisci(app, idWidget, tipo, copia);
            principale.post(() -> {
                if (isDestroyed() || gen != generazione || rv == null) return;
                anteprima.removeAllViews();
                try {
                    // Contesto dell'applicazione: quello dell'activity farebbe creare viste AppCompat,
                    // che i RemoteViews rifiutano
                    anteprima.addView(rv.apply(getApplicationContext(), anteprima));
                } catch (RuntimeException e) {
                    // anteprima non disponibile: le opzioni si salvano comunque
                    android.util.Log.w("ConfiguraWidget", "Anteprima non disponibile", e);
                }
            });
        });
    }

    private void salva() {
        opzioni.salva(this, idWidget);
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        final Context app = getApplicationContext();
        final int id = idWidget;
        new Thread(() -> {
            AggiornaWidget.aggiorna(app, mgr, id, tipo);
            AggiornaWidget.programma(app);
        }).start();
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, idWidget));
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
