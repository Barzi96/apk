package com.barzi.radiocasefa;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  private final String base="https://radiopaedia.org";
  private final String[] organs={"همه ارگان‌ها","مغز و اعصاب","قلب","قفسه سینه","گوارش","کبد و صفرا","اسکلتی عضلانی","سر و گردن","ستون فقرات","کودکان","پستان"};
  private final String[] paths={"","central-nervous-system","cardiac","chest","gastrointestinal","hepatobiliary","musculoskeletal","head-neck","spine","paediatrics","breast"};
  private final String[] modes={"همه مدالیتی‌ها","سی‌تی‌اسکن","ام‌آر‌آی","رادیوگرافی","سونوگرافی","آنژیوگرافی"};
  private final String[] vals={"","CT","MRI","X-ray","Ultrasound","DSA (angiography)"};
  private final ArrayList<String> links=new ArrayList<>();
  private final Set<String> seen=new HashSet<>();
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private Spinner organ,modality;
  private WebView web;
  private TextView info,translation;
  private Button translate;
  private String active="";
  private int page=1;
  private boolean listing=false;
  private android.content.SharedPreferences prefs;
  private final int teal=Color.rgb(0,97,103);

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    prefs=getSharedPreferences("cases",MODE_PRIVATE);
    seen.addAll(prefs.getStringSet("seen",new HashSet<>()));
    LinearLayout root=col();
    root.setBackgroundColor(Color.WHITE);
    setContentView(root);
    TextView title=txt("کیس‌خوان فارسی رادیولوژی",20);
    title.setTextColor(Color.WHITE);
    title.setBackgroundColor(teal);
    title.setPadding(18,18,18,18);
    root.addView(title);
    LinearLayout pick=row();
    organ=new Spinner(this); modality=new Spinner(this);
    organ.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,organs));
    modality.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes));
    pick.addView(organ,new LinearLayout.LayoutParams(0,55,1));
    pick.addView(modality,new LinearLayout.LayoutParams(0,55,1));
    root.addView(pick);
    LinearLayout actions=row();
    Button search=btn("جست‌وجو"),read=btn("خواندن فهرست"),next=btn("کیس جدید");
    actions.addView(search,new LinearLayout.LayoutParams(0,52,1));
    actions.addView(read,new LinearLayout.LayoutParams(0,52,1));
    actions.addView(next,new LinearLayout.LayoutParams(0,52,1));
    root.addView(actions);
    LinearLayout actions2=row();
    Button more=btn("صفحه بعد"),history=btn("تاریخچه"),key=btn("کلید Gemini");
    translate=btn("ترجمه");
    for(Button x:new Button[]{more,history,key,translate})
      actions2.addView(x,new LinearLayout.LayoutParams(0,48,1));
    root.addView(actions2);
    info=txt("ارگان و مدالیتی را انتخاب کن.",12);
    info.setPadding(12,8,12,8);
    root.addView(info);
    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setBuiltInZoomControls(true);
    web.getSettings().setDisplayZoomControls(false);
    web.getSettings().setAllowFileAccess(false);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){
        Uri u=r.getUrl();
        if(!"https".equals(u.getScheme())) return true;
        if("radiopaedia.org".equals(u.getHost())||"www.radiopaedia.org".equals(u.getHost()))return false;
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
        return true;
      }
      @Override public void onPageFinished(WebView w,String url){
        if(!url.startsWith(base))return;
        if(url.contains("/cases/")||url.matches(".*?/quizzes/[0-9]+.*")){
          listing=false;
          active=normalize(url);
          String js="(function(){var t=document.body?document.body.innerText:'';return !!document.querySelector('h1')&&t.length>100&&!t.includes('unsanctioned scraping by bots');})()";
          w.evaluateJavascript(js,v->{
            if("true".equals(v)&&url.equals(w.getUrl())&&!active.isEmpty()){
              seen.add(active);
              prefs.edit().putStringSet("seen",new HashSet<>(seen)).apply();
              message("کیس باز شد و در تاریخچه ثبت شد. پاسخ سایت را آشکار کن، سپس ترجمه را بزن.");
            }else message("بارگذاری کیس تأیید نشد؛ در تاریخچه ثبت نشد.");
          });
        } else if(url.contains("/quizzes")){listing=true;message("فهرست باز شد؛ «خواندن فهرست» را بزن.");}
      }
    });
    root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    translation=txt("",14);
    translation.setTextIsSelectable(true);
    ScrollView answer=new ScrollView(this);
    answer.addView(translation);
    root.addView(answer,new LinearLayout.LayoutParams(-1,0,0.55f));
    search.setOnClickListener(v->{page=1;openList();});
    more.setOnClickListener(v->{page++;openList();});
    read.setOnClickListener(v->collect());
    next.setOnClickListener(v->nextCase());
    history.setOnClickListener(v->showHistory());
    key.setOnClickListener(v->showKey());
    translate.setOnClickListener(v->translateCurrent());
  }
  private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(1);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return x;}
  private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(0);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return x;}
  private TextView txt(String s,int sp){TextView x=new TextView(this);x.setText(s);x.setTextSize(sp);x.setTextDirection(View.TEXT_DIRECTION_RTL);x.setTextColor(Color.DKGRAY);return x;}
  private Button btn(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(11);return b;}
  private void message(String s){info.setText(s);}
  private String normalize(String url){try{Uri u=Uri.parse(url);String p=u.getPath();if(p==null)return "";if(p.matches("/cases/[^/]+/?")||p.matches("/quizzes/[0-9]+/?"))return p.replaceAll("/$","");}catch(Exception ignored){}return "";}
  private void openList(){
    links.clear();active="";translation.setText("");
    String url=base+"/quizzes";
    int i=organ.getSelectedItemPosition();
    if(i>0)url+="/"+paths[i];
    url+="?lang=us&sort=random&page="+page;
    int m=modality.getSelectedItemPosition();
    if(m>0)try{url+="&modality="+URLEncoder.encode(vals[m],"UTF-8");}catch(Exception ignored){}
    listing=true;web.loadUrl(url);message("در حال باز کردن فهرست...");
  }
  private void collect(){
    if(!listing){message("ابتدا فهرست را باز کن.");return;}
    String js="(function(){var a=[],s={};document.querySelectorAll('a[href]').forEach(function(e){try{var u=new URL(e.href);if(!/^(www\\.)?radiopaedia\\.org$/.test(u.hostname))return;var p=u.pathname;if(!(/^\\/cases\\/[^\\/]+\\/?$/.test(p)||/^\\/quizzes\\/\\d+\\/?$/.test(p)))return;if(s[p])return;s[p]=true;a.push('https://radiopaedia.org'+p)}catch(e){}});return JSON.stringify(a.slice(0,100));})()";
    web.evaluateJavascript(js,value->{
      try{
        JSONArray a=new JSONArray(new JSONTokener(value).nextValue().toString());
        links.clear();Set<String> local=new HashSet<>();
        for(int n=0;n<a.length();n++){String u=a.getString(n);String id=normalize(u);if(!id.isEmpty()&&local.add(id))links.add(u);}
        int remaining=0;for(String u:links)if(!seen.contains(normalize(u)))remaining++;
        message(links.size()+" کیس یافت شد؛ "+remaining+" کیس جدید.");
      }catch(Exception ex){message("خواندن فهرست ناموفق بود؛ ممکن است سایت دسترسی را محدود کرده باشد.");}
    });
  }
  private void nextCase(){
    for(String u:links){if(!seen.contains(normalize(u))){active=normalize(u);web.loadUrl(u);translation.setText("");return;}}
    message("کیس جدیدی در این صفحه نیست؛ «صفحه بعد» را بزن.");
  }
  private void showHistory(){
    if(seen.isEmpty()){message("هنوز کیسی دیده نشده.");return;}
    String[] a=seen.toArray(new String[0]);Arrays.sort(a);
    new AlertDialog.Builder(this).setTitle("دیده‌شده‌ها: "+a.length)
      .setItems(a,(d,i)->{web.loadUrl(base+a[i]);})
      .setNegativeButton("بستن",null)
      .setNeutralButton("پاک‌کردن تاریخچه",(d,w)->new AlertDialog.Builder(this).setMessage("همه موارد دیده‌شده پاک شوند؟").setPositiveButton("بله",(d2,w2)->{seen.clear();prefs.edit().remove("seen").apply();}).setNegativeButton("خیر",null).show())
      .show();
  }
  private void showKey(){
    EditText field=new EditText(this);field.setSingleLine(true);field.setHint("کلید Gemini API");
    field.setText(prefs.getString("key",""));
    new AlertDialog.Builder(this).setTitle("کلید ترجمه Gemini").setView(field).setPositiveButton("ذخیره",(d,w)->prefs.edit().putString("key",field.getText().toString().trim()).apply()).setNegativeButton("انصراف",null).show();
  }
  private void translateCurrent(){
    if(active.isEmpty()){message("ابتدا یک کیس باز کن.");return;}
    String key=prefs.getString("key","");if(key.isEmpty()){showKey();return;}
    String url=web.getUrl();
    String js="(function(){return ((document.body&&document.body.innerText)||'').slice(0,14000)})()";
    web.evaluateJavascript(js,v->{
      try{
        String source=new JSONTokener(v).nextValue().toString();
        if(source.length()<100||source.contains("unsanctioned scraping by bots")){message("متن کیس در دسترس نیست.");return;}
        message("در حال ترجمه...");
        worker.execute(()->{
          try{
            JSONObject body=new JSONObject();
            JSONObject part=new JSONObject().put("text","Translate the following radiology case to accurate Persian, preserving medical terms and indicating uncertainty. Do not fabricate anything. Present clinical details, findings, diagnosis and differential. Only translate text actually present: "+source);
            body.put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(part))));
            URL endpoint=new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
            HttpURLConnection c=(HttpURLConnection)endpoint.openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("x-goog-api-key",key);
            try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}
            int status=c.getResponseCode();
            InputStream stream=status>=200&&status<300?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1)bytes.write(buf,0,n);c.disconnect();
            if(status<200||status>=300)throw new IOException("HTTP "+status+": "+bytes.toString("UTF-8").substring(0,Math.min(150,bytes.size())));
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            String fa=response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
            runOnUiThread(()->{if(url.equals(web.getUrl())){translation.setText(fa);message("ترجمه فارسی آماده شد؛ با متن اصلی مقایسه کن.");}});
          }catch(Exception err){runOnUiThread(()->message("خطا در ترجمه: "+err.getMessage()));}
        });
      }catch(Exception err){message("متن صفحه قابل خواندن نیست.");}
    });
  }
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){worker.shutdownNow();if(web!=null)web.destroy();super.onDestroy();}
}
