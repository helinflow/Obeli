package io.github.helinflow.obeli;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.widget.*;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_MODEL = 41;
    private static final String HISTORY_KEY = "conversation_v1";
    private static final int BG = Color.rgb(10,10,10);
    private static final int SURFACE = Color.rgb(18,18,18);
    private static final int CARD = Color.rgb(28,27,31);
    private static final int HIGH = Color.rgb(37,35,42);
    private static final int PRIMARY = Color.rgb(208,188,255);
    private static final int TEXT = Color.rgb(245,240,250);
    private static final int MUTED = Color.rgb(202,196,208);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<String[]> conversation = new ArrayList<>();
    private LinearLayout messages, root;
    private ScrollView chatScroll;
    private EditText prompt;
    private TextView modelStatus, status, welcomeTitle;
    private Button loadButton, sendButton;
    private String modelPath;
    private boolean loaded = false;
    private boolean generating = false;

    private static native String nativeLoadModel(String path);
    private static native String nativeGenerate(String prompt);
    private static native void nativeUnloadModel();
    static { System.loadLibrary("obeli"); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        restoreConversation();
        modelPath = getPreferences(MODE_PRIVATE).getString("model_path", null);
        if (modelPath != null && new File(modelPath).exists()) {
            modelStatus.setText("MODELO DISPONÍVEL  •  " + new File(modelPath).getName());
            status.setText("Pronto para carregar. Seus arquivos ficam no aparelho.");
        } else {
            modelStatus.setText("NENHUM MODELO SELECIONADO");
            status.setText("Importe um arquivo GGUF para começar.");
        }
        updateWelcome();
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable shape(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setFontFeatureSettings("kern");
        return t;
    }

    private RippleDrawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x33ffffff), shape(color, radius, 0), null);
    }

    private TextView action(String value, int color, int textColor, int radius) {
        TextView t = label(value, 14, textColor);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        t.setBackground(ripple(color, radius));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(8), dp(16), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(5), 0, dp(12));
        TextView mark = label("◈", 27, PRIMARY);
        mark.setTypeface(null, Typeface.BOLD);
        top.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(42)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Obeli", 22, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        brand.addView(title);
        TextView subtitle = label("INTELIGÊNCIA LOCAL", 9, MUTED);
        subtitle.setLetterSpacing(.12f);
        brand.addView(subtitle);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView newChat = action("+  Novo", HIGH, TEXT, 24);
        top.addView(newChat);
        newChat.setOnClickListener(v -> newConversation());
        root.addView(top);

        LinearLayout modelCard = new LinearLayout(this);
        modelCard.setOrientation(LinearLayout.VERTICAL);
        modelCard.setPadding(dp(15), dp(12), dp(15), dp(12));
        modelCard.setBackground(shape(CARD, 22, 0));
        LinearLayout modelRow = new LinearLayout(this);
        modelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView modelIcon = label("◉", 19, PRIMARY);
        modelRow.addView(modelIcon, new LinearLayout.LayoutParams(dp(30), dp(32)));
        LinearLayout modelInfo = new LinearLayout(this);
        modelInfo.setOrientation(LinearLayout.VERTICAL);
        TextView modelTitle = label("MODELO LOCAL", 9, MUTED);
        modelTitle.setLetterSpacing(.1f);
        modelInfo.addView(modelTitle);
        modelStatus = label("Verificando armazenamento…", 12, TEXT);
        modelStatus.setMaxLines(1);
        modelStatus.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        modelInfo.addView(modelStatus);
        modelRow.addView(modelInfo, new LinearLayout.LayoutParams(0, -2, 1));
        TextView choose = action("Escolher GGUF", HIGH, TEXT, 18);
        modelRow.addView(choose);
        modelCard.addView(modelRow);
        LinearLayout loadRow = new LinearLayout(this);
        loadRow.setGravity(Gravity.CENTER_VERTICAL);
        status = label("Inicializando…", 11, MUTED);
        loadRow.addView(status, new LinearLayout.LayoutParams(0, dp(38), 1));
        loadButton = new Button(this);
        loadButton.setText("Carregar");
        loadButton.setAllCaps(false);
        loadButton.setTextSize(12);
        loadButton.setTextColor(Color.rgb(33,21,54));
        loadButton.setPadding(dp(12), 0, dp(12), 0);
        loadButton.setBackground(shape(PRIMARY, 18, 0));
        loadRow.addView(loadButton, new LinearLayout.LayoutParams(-2, dp(38)));
        modelCard.addView(loadRow);
        root.addView(modelCard);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(18), 0, dp(18));
        chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(12), dp(8), dp(8), dp(8));
        composer.setBackground(shape(HIGH, 26, 0));
        prompt = new EditText(this);
        prompt.setTextColor(TEXT);
        prompt.setHintTextColor(0xff938f99);
        prompt.setHint("Pergunte qualquer coisa…");
        prompt.setTextSize(15);
        prompt.setMinLines(1);
        prompt.setMaxLines(5);
        prompt.setGravity(Gravity.TOP | Gravity.START);
        prompt.setBackgroundColor(Color.TRANSPARENT);
        prompt.setPadding(dp(5), dp(5), dp(5), dp(8));
        prompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        prompt.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        composer.addView(prompt, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView offline = label("●  OFFLINE", 9, MUTED);
        offline.setLetterSpacing(.08f);
        controls.addView(offline, new LinearLayout.LayoutParams(0, dp(38), 1));
        sendButton = new Button(this);
        sendButton.setText("↑");
        sendButton.setTextSize(21);
        sendButton.setTextColor(Color.rgb(33,21,54));
        sendButton.setPadding(0, 0, 0, dp(3));
        sendButton.setMinWidth(dp(48));
        sendButton.setBackground(shape(PRIMARY, 18, 0));
        controls.addView(sendButton, new LinearLayout.LayoutParams(dp(48), dp(42)));
        composer.addView(controls);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(8), 0, 0);
        root.addView(composer, cp);
        TextView privacy = label("Suas conversas e modelos permanecem neste dispositivo.", 10, 0xff77717e);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(7), 0, 0);
        root.addView(privacy);

        setContentView(root);
        choose.setOnClickListener(v -> pickModel());
        loadButton.setOnClickListener(v -> loadModel());
        sendButton.setOnClickListener(v -> sendMessage());
        prompt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void updateWelcome() {
        if (!conversation.isEmpty()) return;
        messages.removeAllViews();
        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(dp(4), dp(22), dp(4), dp(10));
        TextView orb = label("✳", 43, PRIMARY);
        intro.addView(orb);
        welcomeTitle = label("Uma IA que fica
com você.", 29, TEXT);
        welcomeTitle.setTypeface(null, Typeface.BOLD);
        welcomeTitle.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.setMargins(0, dp(8), 0, dp(8));
        intro.addView(welcomeTitle, tp);
        TextView desc = label("Converse com modelos abertos, direto no seu aparelho. Sem conta. Sem nuvem.", 14, MUTED);
        desc.setGravity(Gravity.TOP);
        desc.setLineSpacing(dp(3), 1f);
        intro.addView(desc);
        TextView suggestions = label("EXPERIMENTE", 10, MUTED);
        suggestions.setLetterSpacing(.12f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(36));
        sp.setMargins(0, dp(24), 0, 0);
        intro.addView(suggestions, sp);
        addSuggestion(intro, "✦  Explique um conceito difícil de forma simples");
        addSuggestion(intro, "⌘  Ajude a escrever ou revisar um texto");
        addSuggestion(intro, "◌  Dê ideias para um projeto novo");
        messages.addView(intro);
    }

    private void addSuggestion(LinearLayout parent, String value) {
        TextView chip = label(value, 12, TEXT);
        chip.setPadding(dp(13), dp(12), dp(13), dp(12));
        chip.setBackground(ripple(CARD, 17));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(4), 0, dp(4));
        parent.addView(chip, p);
        chip.setOnClickListener(v -> {
            String clean = value.replaceFirst("^[^ ]+  ", "");
            prompt.setText(clean);
            prompt.setSelection(prompt.length());
            prompt.requestFocus();
        });
    }

    private void addMessage(String who, String value) {
        boolean user = "Você".equals(who);
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(14), dp(11), dp(14), dp(11));
        block.setBackground(shape(user ? 0xff332d41 : CARD, 20, 0));
        TextView heading = label(user ? "VOCÊ" : "OBELI", 9, user ? 0xffeaddff : PRIMARY);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setLetterSpacing(.1f);
        block.addView(heading);
        TextView body = label(value, 14, TEXT);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(3), 1.04f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(0, dp(5), 0, 0);
        block.addView(body, bp);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(5), 0, dp(7));
        if (user) p.setMargins(dp(28), dp(5), 0, dp(7));
        messages.addView(block, p);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void newConversation() {
        if (generating) return;
        conversation.clear();
        getPreferences(MODE_PRIVATE).edit().remove(HISTORY_KEY).apply();
        updateWelcome();
        status.setText(loaded ? "Nova conversa. Modelo pronto." : "Nova conversa. Carregue um modelo.");
    }

    private void saveConversation() {
        try {
            JSONArray array = new JSONArray();
            for (String[] item : conversation) {
                JSONObject row = new JSONObject();
                row.put("role", item[0]);
                row.put("text", item[1]);
                array.put(row);
            }
            getPreferences(MODE_PRIVATE).edit().putString(HISTORY_KEY, array.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void restoreConversation() {
        String saved = getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String role = row.optString("role", "");
                String value = row.optString("text", "");
                if (("Você".equals(role) || "Obeli".equals(role)) && !value.isEmpty()) {
                    conversation.add(new String[]{role, value});
                    addMessage(role, value);
                }
            }
        } catch (Exception ignored) {
            getPreferences(MODE_PRIVATE).edit().remove(HISTORY_KEY).apply();
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
        status.setText("Importando modelo para o armazenamento do Obeli…");
        loadButton.setEnabled(false);
        worker.execute(() -> {
            try {
                String name = getName(uri);
                if (!name.toLowerCase().endsWith(".gguf")) throw new IOException("Selecione um arquivo com extensão .gguf.");
                File dir = new File(getFilesDir(), "models");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta de modelos.");
                File target = new File(dir, name.replaceAll("[^a-zA-Z0-9._-]", "_"));
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(target)) {
                    if (in == null) throw new IOException("Não foi possível abrir o arquivo.");
                    byte[] buffer = new byte[256 * 1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                }
                modelPath = target.getAbsolutePath();
                getPreferences(MODE_PRIVATE).edit().putString("model_path", modelPath).apply();
                runOnUiThread(() -> {
                    modelStatus.setText("MODELO DISPONÍVEL  •  " + target.getName());
                    status.setText("Importado. Toque em Carregar para iniciar.");
                    loadButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Falha ao importar: " + e.getMessage());
                    loadButton.setEnabled(true);
                });
            }
        });
    }

    private String getName(Uri uri) {
        String name = "modelo.gguf";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = c.getString(index);
            }
        } catch (Exception ignored) { }
        return name;
    }

    private void loadModel() {
        if (generating) return;
        if (modelPath == null || !new File(modelPath).exists()) {
            status.setText("Escolha um arquivo GGUF primeiro.");
            return;
        }
        loadButton.setEnabled(false);
        status.setText("Carregando modelo local… isso pode levar alguns minutos.");
        worker.execute(() -> {
            String result = nativeLoadModel(modelPath);
            runOnUiThread(() -> {
                loaded = "OK".equals(result);
                loadButton.setEnabled(true);
                loadButton.setText(loaded ? "Carregado" : "Tentar novamente");
                status.setText(loaded ? "Modelo ativo. Inferência no próprio aparelho." : result);
                modelStatus.setText(loaded ? "MODELO ATIVO  •  " + new File(modelPath).getName()
                        : "FALHA AO CARREGAR  •  " + new File(modelPath).getName());
            });
        });
    }

    private void sendMessage() {
        String value = prompt.getText().toString().trim();
        if (value.isEmpty() || generating) return;
        if (!loaded) {
            status.setText("Carregue um modelo GGUF antes de conversar.");
            return;
        }
        prompt.setText("");
        if (conversation.isEmpty()) messages.removeAllViews();
        conversation.add(new String[]{"Você", value});
        addMessage("Você", value);
        saveConversation();
        generating = true;
        sendButton.setEnabled(false);
        sendButton.setAlpha(.55f);
        status.setText("Gerando resposta local…");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(prompt.getWindowToken(), 0);
        worker.execute(() -> {
            String answer = nativeGenerate(value);
            runOnUiThread(() -> {
                conversation.add(new String[]{"Obeli", answer});
                addMessage("Obeli", answer);
                saveConversation();
                generating = false;
                sendButton.setEnabled(true);
                sendButton.setAlpha(1f);
                status.setText("Resposta gerada no dispositivo.");
            });
        });
    }

    @Override protected void onDestroy() {
        if (isFinishing()) worker.execute(() -> nativeUnloadModel());
        worker.shutdown();
        super.onDestroy();
    }
}
