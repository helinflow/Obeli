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
import android.widget.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_MODEL = 41;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private EditText prompt;
    private TextView modelStatus, status;
    private Button loadButton, sendButton;
    private String modelPath;
    private boolean loaded = false;
    private static native String nativeLoadModel(String path);
    private static native String nativeGenerate(String prompt);
    private static native void nativeUnloadModel();
    static { System.loadLibrary("obeli"); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,10,12));
        getWindow().setNavigationBarColor(Color.rgb(10,10,12));
        buildUi();
        modelPath = getPreferences(MODE_PRIVATE).getString("model_path", null);
        if (modelPath != null && new File(modelPath).exists()) {
            modelStatus.setText("Modelo encontrado: " + new File(modelPath).getName() + "\nToque em Carregar modelo");
        } else modelStatus.setText("Nenhum modelo GGUF selecionado");
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(dp(4),dp(5),dp(4),dp(5)); return t;
    }
    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,10,12)); root.setPadding(dp(16),dp(12),dp(16),dp(10));
        TextView title=text("Obeli",25,Color.WHITE); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        root.addView(text("IA local • GGUF • sem nuvem",13,0xffb5b5bd));
        modelStatus=text("Preparando armazenamento…",13,Color.WHITE);
        modelStatus.setPadding(dp(10),dp(12),dp(10),dp(12));
        modelStatus.setBackgroundColor(0xff1b1b20); root.addView(modelStatus);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose=new Button(this); choose.setText("Escolher GGUF"); actions.addView(choose,new LinearLayout.LayoutParams(0,dp(52),1));
        loadButton=new Button(this); loadButton.setText("Carregar"); actions.addView(loadButton,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(actions);
        status=text("Pronto para trabalhar offline.",12,0xffb5b5bd); root.addView(status);
        ScrollView scroll=new ScrollView(this); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(0,dp(8),0,dp(8)); scroll.addView(messages);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        prompt=new EditText(this); prompt.setTextColor(Color.WHITE); prompt.setHintTextColor(0xff888890); prompt.setHint("Escreva uma mensagem…");
        prompt.setBackgroundColor(0xff202027); prompt.setMinLines(2); prompt.setMaxLines(5); root.addView(prompt);
        sendButton=new Button(this); sendButton.setText("Enviar"); root.addView(sendButton);
        setContentView(root);
        choose.setOnClickListener(v -> pickModel());
        loadButton.setOnClickListener(v -> loadModel());
        sendButton.setOnClickListener(v -> sendMessage());
    }
    private void pickModel() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_MODEL);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_MODEL || result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        worker.execute(() -> {
            try {
                String name=getName(uri); if(!name.toLowerCase().endsWith(".gguf")) throw new IOException("Selecione um arquivo .gguf");
                File dir=new File(getFilesDir(),"models"); if(!dir.exists()&&!dir.mkdirs()) throw new IOException("Não foi possível criar a pasta de modelos");
                File target=new File(dir,name.replaceAll("[^a-zA-Z0-9._-]","_"));
                try(InputStream in=getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(target)) {
                    if(in==null) throw new IOException("Não foi possível abrir o arquivo");
                    byte[] buf=new byte[1024*1024]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
                }
                modelPath=target.getAbsolutePath(); getPreferences(MODE_PRIVATE).edit().putString("model_path",modelPath).apply();
                runOnUiThread(() -> { modelStatus.setText("Modelo importado: "+target.getName()); status.setText("Arquivo salvo no armazenamento privado do Obeli."); });
            } catch(Exception e) { runOnUiThread(() -> status.setText("Falha ao importar: "+e.getMessage())); }
        });
    }
    private String getName(Uri uri) {
        String name="modelo.gguf";
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)) {
            if(c!=null && c.moveToFirst()) { int ix=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(ix>=0) name=c.getString(ix); }
        } catch(Exception ignored) {}
        return name;
    }
    private void loadModel() {
        if(modelPath==null || !new File(modelPath).exists()) { status.setText("Escolha um arquivo GGUF primeiro."); return; }
        loadButton.setEnabled(false); status.setText("Carregando modelo local… modelos grandes podem demorar.");
        worker.execute(() -> {
            String result=nativeLoadModel(modelPath);
            runOnUiThread(() -> { loaded="OK".equals(result); loadButton.setEnabled(true);
                status.setText(loaded?"Modelo carregado. Conversa 100% local.":result);
                modelStatus.setText(loaded?"Modelo ativo: "+new File(modelPath).getName(): "Falha ao carregar: "+new File(modelPath).getName());
            });
        });
    }
    private void addMessage(String who,String value) {
        TextView t=text(who+"\n"+value,14,Color.WHITE); t.setBackgroundColor(0xff202027);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(5),0,dp(5)); messages.addView(t,p);
    }
    private void sendMessage() {
        String value=prompt.getText().toString().trim();
        if(value.isEmpty()) return;
        if(!loaded) { status.setText("Carregue um modelo GGUF antes de conversar."); return; }
        prompt.setText(""); addMessage("Você",value); sendButton.setEnabled(false); status.setText("Gerando resposta no aparelho…");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(prompt.getWindowToken(),0);
        worker.execute(() -> {
            String answer=nativeGenerate(value);
            runOnUiThread(() -> { addMessage("Obeli",answer); sendButton.setEnabled(true); status.setText("Resposta gerada localmente."); });
        });
    }
    @Override protected void onDestroy() {
        if(isFinishing()) worker.execute(() -> nativeUnloadModel());
        worker.shutdown(); super.onDestroy();
    }
}