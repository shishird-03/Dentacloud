package com.dentacloud.app.ui.lab;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.dentacloud.app.BuildConfig;
import com.dentacloud.app.R;
import com.google.firebase.FirebaseApp;
import com.google.android.material.button.MaterialButton;

import java.lang.ref.WeakReference;

/**
 * Part B #2, lab-only: AsyncTask counting 1 to 1000 with a progress bar. AsyncTask is deprecated and
 * banned everywhere else in the app (CLAUDE.md); real background work uses ExecutorService and WorkManager.
 */
@SuppressWarnings("deprecation")
public class AsyncTaskLabFragment extends Fragment {
    private ProgressBar bar;
    private TextView label;
    private MaterialButton start;
    private Counter task;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        int pad = getResources().getDimensionPixelSize(R.dimen.space_4);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        TextView intro = new TextView(requireContext());
        intro.setText(R.string.lab_intro);
        bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        label = new TextView(requireContext());
        label.setText(getString(R.string.lab_count, 0));
        start = new MaterialButton(requireContext());
        start.setText(R.string.lab_start);
        start.setOnClickListener(v -> {
            if (task != null) task.cancel(true);
            task = new Counter(this);
            task.execute(1000);
        });

        root.addView(intro);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(label);
        root.addView(start);
        // Only when Crashlytics is live (Firebase configured, not a debug build): proves reports arrive.
        if (!BuildConfig.DEBUG && !FirebaseApp.getApps(requireContext()).isEmpty()) {
            MaterialButton crash = new MaterialButton(requireContext());
            crash.setText(R.string.lab_test_crash);
            crash.setOnClickListener(v -> {
                throw new RuntimeException("Crashlytics test crash");
            });
            root.addView(crash);
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        if (task != null) task.cancel(true); // never outlive the screen
        super.onDestroyView();
    }

    /** Static with a weak reference so a running task can't leak the fragment. */
    private static class Counter extends AsyncTask<Integer, Integer, Integer> {
        private final WeakReference<AsyncTaskLabFragment> ref;

        Counter(AsyncTaskLabFragment f) {
            ref = new WeakReference<>(f);
        }

        @Override
        protected void onPreExecute() {
            AsyncTaskLabFragment f = ref.get();
            if (f != null) f.start.setEnabled(false);
        }

        @Override
        protected Integer doInBackground(Integer... max) {
            for (int i = 1; i <= max[0] && !isCancelled(); i++) {
                publishProgress(i);
                try {
                    Thread.sleep(3); // about 3 seconds in total, so the progress is visible
                } catch (InterruptedException e) {
                    return i;
                }
            }
            return max[0];
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            AsyncTaskLabFragment f = ref.get();
            if (f == null || f.bar == null) return;
            f.bar.setProgress(values[0]);
            f.label.setText(f.getString(R.string.lab_count, values[0]));
        }

        @Override
        protected void onPostExecute(Integer result) {
            AsyncTaskLabFragment f = ref.get();
            if (f == null || f.label == null) return;
            f.label.setText(f.getString(R.string.lab_done, result));
            f.start.setEnabled(true);
        }
    }
}
