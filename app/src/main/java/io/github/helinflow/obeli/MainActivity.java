package io.github.helinflow.obeli;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_MODEL = 41;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText prompt;
    private TextView modelStatus, status;
    private Button loadButton, sendButton;
    private String modelPath;
    private boolean loaded = false;

    private static native String nativeLoadModel(String path);
    private static native String nativeGenerate(String prompt);
    private static native void nativeUnloadModel();
    static { System.loadLibrary("obeli"); }

    private final int bg = 0xff101113;
    private final int surface = 0xff1b1c1f;
    private final int surfaceRaised = 0xff242529;
    private final int fg = 0xfff2f1f3;
    private final int muted = 0xffa7a6ad;
    private final int accent = 0xffd7d4dc;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        buildUi();
        modelPath = getPreferences(MODE_PRIVATE).getString("model_path", null);
        refreshModelLabel();
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable shape(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setBackground(shape(surfaceRaised, 18));
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(18), dp(10), dp(18), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Obeli", 24, fg);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button modelButton = button("Modelo  ⌄");
        modelButton.setTextSize(13);
        header.addView(modelButton, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(header);

        modelStatus = text("Preparando…", 12, muted);
        modelStatus.setSingleLine(true);
        modelStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        modelStatus.setPadding(dp(12), dp(8), dp(12), dp(8));
        modelStatus.setBackground(shape(surface, 14));
        LinearLayout.LayoutParams modelStatusParams = new LinearLayout.LayoutParams(-1, -2);
        modelStatusParams.setMargins(0, dp(10), 0, dp(8));
        root.addView(modelStatus, modelStatusParams);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(10), 0, dp(14));
        chatScroll.addView(messages);
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(5), dp(5), dp(5));
        composer.setBackground(shape(surface, 24));

        prompt = new EditText(this);
        prompt.setTextColor(fg);
        prompt.setHintTextColor(muted);
        prompt.setHint("Pergunte alguma coisa…");
        prompt.setTextSize(15);
        prompt.setSingleLine(false);
        prompt.setMinLines(1);
        prompt.setMaxLines(5);
        prompt.setBackgroundColor(Color.TRANSPARENT);
        prompt.setPadding(dp(10), dp(8), dp(8), dp(8));
        composer.addView(prompt, new LinearLayout.LayoutParams(0, -2, 1));

        sendButton = button("↑");
        sendButton.setTextSize(22);
        sendButton.setGravity(Gravity.CENTER);
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(-1, -2);
        composerParams.setMargins(0, dp(8), 0, dp(2));
        root.addView(composer, composerParams);

        status = text("As conversas são processadas no dispositivo.", 11, muted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(7), 0, 0);
        root.addView(status);

        setContentView(root);
        modelButton.setOnClickListener(v -> showModelMenu());
        sendButton.setOnClickListener(v -> sendMessage());
        showWelcome();
    }

    private void showWelcome() {
        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(dp(12), dp(20), dp(12), dp(20));

        TextView mark = text("◉", 34, accent);
        mark.setGravity(Gravity.CENTER);
        welcome.addView(mark);

        TextView heading = text("Sua IA. No seu dispositivo.", 20, fg);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(12), 0, dp(6));
        welcome.addView(heading, hp);

        TextView subtitle = text("Importe um modelo GGUF para começar a conversar offline.", 14, muted);
        subtitle.setGravity(Gravity.CENTER);
        welcome.addView(subtitle);

        Button importButton = button("Adicionar modelo");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(46));
        bp.setMargins(0, dp(18), 0, 0);
        welcome.addView(importButton, bp);
        importButton.setOnClickListener(v -> pickModel());

        messages.addView(welcome, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showModelMenu() {
        String[] options = {"Adicionar modelo GGUF", "Carregar modelo", "Informações do modelo"};
        new AlertDialog.Builder(this)
            .setTitle("Modelos")
            .setItems(options, (dialog, which) -> {
                if (which == 0) pickModel();
                else if (which == 1) loadModel();
                else showModelInfo();
            })
            .setNegativeButton("Fechar", null)
            .show();
    }

    private void showModelInfo() {
        String info = modelPath != null && new File(modelPath).exists()
            ? "Arquivo: " + new File(modelPath).getName() + "\nEstado: " + (loaded ? "Carregado" : "Não carregado")
            : "Nenhum modelo adicionado.";
        new AlertDialog.Builder(this).setTitle("Modelo atual").setMessage(info)
            .setPositiveButton("OK", null).show();
    }

    private void refreshModelLabel() {
        if (modelPath != null && new File(modelPath).exists()) {
            modelStatus.setText((loaded ? "● " : "○ ") + new File(modelPath).getName());
        } else {
            modelStatus.setText("Nenhum modelo selecionado");
        }
    }

    private void pickModel() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_MODEL);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_MODEL || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        worker.execute(() -> {
            try {
                String name = getName(uri);
                if (!name.toLowerCase().endsWith(".gguf")) throw new IOException("Selecione um arquivo .gguf");
                File dir = new File(getFilesDir(), "models");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta de modelos");
                File target = new File(dir, name.replaceAll("[^a-zA-Z0-9._-]", "_"));
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(target)) {
                    if (in == null) throw new IOException("Não foi possível abrir o arquivo");
                    byte[] buf = new byte[1024 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                modelPath = target.getAbsolutePath();
                getPreferences(MODE_PRIVATE).edit().putString("model_path", modelPath).apply();
                runOnUiThread(() -> {
                    refreshModelLabel();
                    status.setText("Modelo importado para o armazenamento privado.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Falha ao importar: " + e.getMessage()));
            }
        });
    }

    private String getName(Uri uri) {
        String name = "modelo.gguf";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) name = c.getString(ix);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private void loadModel() {
        if (modelPath == null || !new File(modelPath).exists()) {
            status.setText("Adicione um modelo GGUF primeiro.");
            return;
        }
        sendButton.setEnabled(false);
        status.setText("Carregando modelo local…");
        worker.execute(() -> {
            String result = nativeLoadModel(modelPath);
            runOnUiThread(() -> {
                loaded = "OK".equals(result);
                sendButton.setEnabled(true);
                status.setText(loaded ? "Modelo pronto. Você pode conversar offline." : result);
                refreshModelLabel();
            });
        });
    }

    private void addMessage(String who, String value) {
        TextView t = text(value, 15, fg);
        t.setTextIsSelectable(true);
        t.setPadding(dp(14), dp(11), dp(14), dp(11));
        t.setBackground(shape(who.equals("Você") ? surfaceRaised : surface, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(5), 0, dp(5));
        messages.addView(t, p);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        String value = prompt.getText().toString().trim();
        if (value.isEmpty()) return;
        if (!loaded) {
            status.setText("Adicione e carregue um modelo antes de conversar.");
            return;
        }
        prompt.setText("");
        addMessage("Você", value);
        sendButton.setEnabled(false);
        status.setText("Gerando resposta no dispositivo…");
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(prompt.getWindowToken(), 0);
        worker.execute(() -> {
            String answer = nativeGenerate(value);
            runOnUiThread(() -> {
                addMessage("Obeli", answer);
                sendButton.setEnabled(true);
                status.setText("Resposta gerada localmente.");
            });
        });
    }

    @Override protected void onDestroy() {
        if (isFinishing()) worker.execute(() -> nativeUnloadModel());
        worker.shutdown();
        super.onDestroy();
    }
}