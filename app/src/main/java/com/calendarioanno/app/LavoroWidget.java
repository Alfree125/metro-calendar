package com.calendarioanno.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Parte quando cambia il calendario (anche da altre app): ridisegna i widget e si riprogramma. Da Android 7. */
public class LavoroWidget extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            AggiornaWidget.aggiornaTutti(this);
            if (AggiornaWidget.ciSonoWidget(this)) AggiornaWidget.programmaLavoro(this);
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
