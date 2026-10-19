package com.calendarioanno.app;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Impostazioni (l'ingranaggio del tabellone): nome e icona dell'app, icona sulla home, widget, lingua. */
public class ImpostazioniActivity extends AppCompatActivity {

    private static final String STATO_ICONA = "icona";

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());
    private EditText inNomeApp;
    private RadioGroup gruppoLingua;
    private final List<View> tessere = new ArrayList<>();
    private final List<String> nomiTessere = new ArrayList<>();
    private ImageView anteprimaFoto;
    private String iconaScelta;

    private com.google.android.material.materialswitch.MaterialSwitch swNotifiche;
    private TextView valPromemoriaPredefinito;
    private boolean mostrandoNotifiche;

    private final ActivityResultLauncher<String> richiestaNotifiche =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concesso -> {
                if (!concesso && Promemoria.serveRichiesta(this)) apriImpostazioniNotifiche();
                mostraNotifiche();
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> sceltaFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::fotoScelta);

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Preferenze.conLingua(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_impostazioni);

        View root = findViewById(R.id.root);
        View scorrimento = findViewById(R.id.scorrimento);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, 0);
            scorrimento.setPadding(0, 0, 0, Math.max(barre.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        ((TextView) findViewById(R.id.striscia_nome)).setText(Preferenze.nomeApp(this));
        findViewById(R.id.btn_chiudi).setOnClickListener(v -> finish());
        inNomeApp = findViewById(R.id.in_nome_app);
        gruppoLingua = findViewById(R.id.gruppo_lingua);

        iconaScelta = savedInstanceState != null
                ? savedInstanceState.getString(STATO_ICONA, Preferenze.icona(this)) : Preferenze.icona(this);
        if (savedInstanceState == null) {
            String nome = Preferenze.di(this).getString("nome_app", null);
            inNomeApp.setText(nome == null ? "" : nome);
            String lingua = Preferenze.lingua(this);
            gruppoLingua.check(Preferenze.LINGUA_ITALIANO.equals(lingua) ? R.id.lingua_it
                    : Preferenze.LINGUA_SISTEMA.equals(lingua) ? R.id.lingua_sistema : R.id.lingua_en);
        }
        preparaIcone();

        findViewById(R.id.btn_home).setOnClickListener(v -> {
            if (!Icone.aggiungiAllaHome(this, nomeScritto(), iconaScelta)) {
                Toast.makeText(this, R.string.home_non_supportato, Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.btn_widget_piccolo).setOnClickListener(v -> fissaWidget(WidgetPiccolo.class));
        findViewById(R.id.btn_widget_medio).setOnClickListener(v -> fissaWidget(WidgetMedio.class));
        findViewById(R.id.btn_widget_grande).setOnClickListener(v -> fissaWidget(WidgetGrande.class));
        findViewById(R.id.btn_widget_giornata).setOnClickListener(v -> fissaWidget(WidgetGiornata.class));
        findViewById(R.id.btn_widget_giornata_larga).setOnClickListener(v -> fissaWidget(WidgetGiornataLarga.class));
        findViewById(R.id.btn_salva).setOnClickListener(v -> salva());

        // Notifiche di promemoria: valgono subito, senza "Salva"
        swNotifiche = findViewById(R.id.sw_notifiche);
        valPromemoriaPredefinito = findViewById(R.id.val_promemoria_predefinito);
        swNotifiche.setOnCheckedChangeListener((b, attive) -> {
            if (mostrandoNotifiche) return;
            Preferenze.impostaNotifiche(this, attive);
            if (attive && Promemoria.serveRichiesta(this)) {
                richiestaNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        View.OnClickListener predefinito = v -> menuPromemoriaPredefinito();
        findViewById(R.id.riga_promemoria_predefinito).setOnClickListener(predefinito);
        valPromemoriaPredefinito.setOnClickListener(predefinito);
        findViewById(R.id.btn_prova_notifica).setOnClickListener(v -> provaNotifica());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mostraWidget();
        mostraNotifiche();
    }

    // ---- Widget sulla home ----

    private void mostraWidget() {
        LinearLayout lista = findViewById(R.id.lista_widget);
        lista.removeAllViews();
        List<int[]> widget = AggiornaWidget.elenco(this);
        for (int[] w : widget) {
            View riga = getLayoutInflater().inflate(R.layout.riga_widget, lista, false);
            AggiornaWidget.Tipo tipo = AggiornaWidget.Tipo.values()[w[1]];
            ((TextView) riga.findViewById(R.id.nome)).setText(getString(R.string.widget_numero, getString(tipo.nome), w[0]));
            riga.setOnClickListener(v -> startActivity(new Intent(this, ConfiguraWidgetActivity.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, w[0])));
            lista.addView(riga);
        }
        findViewById(R.id.nessun_widget).setVisibility(widget.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- Notifiche ----

    private void mostraNotifiche() {
        mostrandoNotifiche = true;
        swNotifiche.setChecked(Preferenze.notificheAttive(this));
        mostrandoNotifiche = false;
        int minuti = Preferenze.promemoriaPredefinito(this);
        valPromemoriaPredefinito.setText(minuti < 0 ? getString(R.string.nessun_promemoria)
                : EventoActivity.testoPromemoria(this, minuti));
        boolean bloccate = Preferenze.notificheAttive(this) && !Promemoria.possoNotificare(this) && !Promemoria.serveRichiesta(this);
        ((TextView) findViewById(R.id.aiuto_notifiche)).setText(bloccate ? R.string.notifiche_bloccate : R.string.notifiche_aiuto);
    }

    private void menuPromemoriaPredefinito() {
        androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(this, valPromemoriaPredefinito);
        menu.getMenu().add(0, -1, 0, R.string.nessun_promemoria);
        for (int k = 0; k < EventoActivity.MINUTI_PROMEMORIA.length; k++) {
            menu.getMenu().add(0, k, k + 1, EventoActivity.testoPromemoria(this, EventoActivity.MINUTI_PROMEMORIA[k]));
        }
        menu.setOnMenuItemClickListener(item -> {
            int k = item.getItemId();
            Preferenze.impostaPromemoriaPredefinito(this, k < 0 ? -1 : EventoActivity.MINUTI_PROMEMORIA[k]);
            mostraNotifiche();
            return true;
        });
        menu.show();
    }

    private void provaNotifica() {
        if (Promemoria.possoNotificare(this)) {
            Promemoria.prova(this);
        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            richiestaNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            apriImpostazioniNotifiche();
        }
    }

    private void apriImpostazioniNotifiche() {
        if (Build.VERSION.SDK_INT >= 26) {
            startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } else {
            // Android 6 e 7: le notifiche si gestiscono dalla scheda dell'app
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getPackageName(), null)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdown();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATO_ICONA, iconaScelta);
    }

    private String nomeScritto() {
        String nome = inNomeApp.getText().toString().trim();
        return nome.isEmpty() ? getString(R.string.app_name) : nome;
    }

    // ---- Icone ----

    private void preparaIcone() {
        LinearLayout riga = findViewById(R.id.scelta_icone);
        for (String nome : Icone.NOMI) {
            ImageView iv = immagineIcona();
            iv.setImageResource(Icone.risorsa(nome));
            aggiungiTessera(riga, iv, nome);
        }
        // La foto vale solo per l'icona sulla home
        anteprimaFoto = immagineIcona();
        mostraFoto(Icone.foto(this));
        aggiungiTessera(riga, anteprimaFoto, Icone.FOTO);
        evidenziaIcona();
    }

    private ImageView immagineIcona() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        float raggio = dp(14);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), raggio);
            }
        });
        iv.setClipToOutline(true);
        return iv;
    }

    private void mostraFoto(Bitmap foto) {
        if (foto != null) {
            anteprimaFoto.setImageBitmap(foto);
            anteprimaFoto.setBackground(null);
            anteprimaFoto.setPadding(0, 0, 0, 0);
        } else {
            anteprimaFoto.setImageResource(R.drawable.ic_foto);
            anteprimaFoto.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.inchiostro_tenue)));
            anteprimaFoto.setBackgroundColor(ContextCompat.getColor(this, R.color.carta_filetto));
            anteprimaFoto.setPadding(dp(14), dp(14), dp(14), dp(14));
        }
    }

    private void aggiungiTessera(LinearLayout riga, ImageView iv, String nome) {
        FrameLayout tessera = new FrameLayout(this);
        tessera.setBackgroundResource(R.drawable.bg_scelta);
        tessera.setPadding(dp(5), dp(5), dp(5), dp(5));
        tessera.addView(iv, new FrameLayout.LayoutParams(dp(56), dp(56)));
        tessera.setContentDescription(nome);
        tessera.setOnClickListener(v -> {
            if (Icone.FOTO.equals(nome) && Icone.foto(this) == null) {
                sceltaFoto.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                return;
            }
            if (Icone.FOTO.equals(nome) && Icone.FOTO.equals(iconaScelta)) {
                // Secondo tocco sulla foto: se ne sceglie un'altra
                sceltaFoto.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                return;
            }
            iconaScelta = nome;
            evidenziaIcona();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(66), dp(66));
        lp.setMarginEnd(dp(8));
        riga.addView(tessera, lp);
        tessere.add(tessera);
        nomiTessere.add(nome);
    }

    private void evidenziaIcona() {
        for (int k = 0; k < tessere.size(); k++) tessere.get(k).setSelected(nomiTessere.get(k).equals(iconaScelta));
        ((TextView) findViewById(R.id.aiuto_icona)).setText(
                Icone.FOTO.equals(iconaScelta) ? R.string.icona_foto_aiuto : R.string.icona_aiuto);
    }

    private void fotoScelta(Uri uri) {
        if (uri == null) return;
        esecutore.execute(() -> {
            boolean ok;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ok = in != null && Icone.salvaFoto(this, in);
            } catch (Exception e) {
                ok = false;
            }
            Bitmap foto = ok ? Icone.foto(this) : null;
            principale.post(() -> {
                if (isDestroyed() || foto == null) return;
                mostraFoto(foto);
                iconaScelta = Icone.FOTO;
                evidenziaIcona();
            });
        });
    }

    // ---- Widget ----

    private void fissaWidget(Class<?> provider) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported()) {
            mgr.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } else {
            Toast.makeText(this, R.string.widget_non_supportato, Toast.LENGTH_LONG).show();
        }
    }

    // ---- Salva ----

    private void salva() {
        Preferenze.impostaNomeApp(this, inNomeApp.getText().toString());
        // L'icona nell'elenco app cambia solo per le varianti pronte (la foto resta per la home)
        String vecchiaIcona = Preferenze.icona(this);
        Preferenze.impostaIcona(this, iconaScelta);
        if (!Icone.FOTO.equals(iconaScelta) && !iconaScelta.equals(vecchiaIcona)) {
            Icone.applica(this, iconaScelta);
        }

        int scelta = gruppoLingua.getCheckedRadioButtonId();
        String lingua = scelta == R.id.lingua_it ? Preferenze.LINGUA_ITALIANO
                : scelta == R.id.lingua_sistema ? Preferenze.LINGUA_SISTEMA : Preferenze.LINGUA_INGLESE;
        boolean linguaCambiata = !lingua.equals(Preferenze.lingua(this));
        Preferenze.impostaLingua(this, lingua);
        AggiornaWidget.richiedi(this);

        if (linguaCambiata) {
            // Riparte da capo perche' tutte le schermate prendano la nuova lingua
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
