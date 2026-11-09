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
  private ScrollView answer;
  private Button translate;
  private String active="";
  private int page=1;
  private boolean listing=false;
  private android.content.SharedPreferences prefs;
  private final int teal=Color.rgb(0,97,103);

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(teal);
    prefs=getSharedPreferences("cases",MODE_PRIVATE);
    seen.addAll(prefs.getStringSet("seen",new HashSet<>()));

    LinearLayout root=col();
    root.setBackgroundColor(Color.WHITE);
    setContentView(root);

    TextView title=txt("کیس‌خوان فارسی رادیولوژی",19);
    title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    title.setTextColor(Color.WHITE);
    title.setBackgroundColor(teal);
    title.setPadding(dp(18),0,dp(18),0);
    root.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));

    Button toggle=btnAlt("فیلترها و ابزارها  ▲");
    LinearLayout.LayoutParams toggleP=new LinearLayout.LayoutParams(-1,dp(48));
    toggleP.setMargins(dp(8),dp(6),dp(8),dp(2));
    root.addView(toggle,toggleP);

    ScrollView panelScroll=new ScrollView(this);
    panelScroll.setFillViewport(false);
    panelScroll.setVerticalScrollBarEnabled(true);
    LinearLayout panel=col();
    panel.setPadding(dp(10),dp(4),dp(10),dp(6));
    panelScroll.addView(panel);
    root.addView(panelScroll,new LinearLayout.LayoutParams(-1,dp(255)));

    TextView organLabel=txt("انتخاب ارگان یا دستگاه بدن",13);
    organLabel.setPadding(dp(5),0,dp(5),dp(3));
    panel.addView(organLabel);
    organ=dropdown(organs);
    panel.addView(organ,new LinearLayout.LayoutParams(-1,dp(52)));

    TextView modeLabel=txt("انتخاب نوع تصویربرداری",13);
    modeLabel.setPadding(dp(5),dp(8),dp(5),dp(3));
    panel.addView(modeLabel);
    modality=dropdown(modes);
    panel.addView(modality,new LinearLayout.LayoutParams(-1,dp(52)));

    Button search=btn("جست‌وجوی کیس‌ها");
    Button read=btnAlt("خواندن فهرست");
    addButtonPair(panel,search,read);

    Button more=btnAlt("صفحه بعد");
    Button history=btnAlt("تاریخچه کیس‌ها");
    addButtonPair(panel,more,history);

    Button key=btnAlt("تنظیم کلید ترجمه Gemini");
    LinearLayout.LayoutParams keyP=new LinearLayout.LayoutParams(-1,dp(49));
    keyP.setMargins(dp(4),dp(4),dp(4),dp(4));
    panel.addView(key,keyP);

    LinearLayout mainActions=row();
    Button next=btn("کیس بعدی");
    translate=btn("ترجمه فارسی");
    addButtonPair(mainActions,next,translate);
    root.addView(mainActions);

    info=txt("از فیلترها شروع کن، سپس جست‌وجو و خواندن فهرست را بزن.",12);
    info.setPadding(dp(14),dp(7),dp(14),dp(7));
    info.setMaxLines(2);
    info.setMinHeight(dp(44));
    info.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    info.setBackgroundColor(0xffedf5f5);
    root.addView(info,new LinearLayout.LayoutParams(-1,-2));

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
              message("کیس باز شد و در تاریخچه ثبت شد؛ برای مشاهده پاسخ فارسی، ترجمه را بزن.");
            }else message("بارگذاری کیس تأیید نشد؛ در تاریخچه ثبت نشد.");
          });
        } else if(url.contains("/quizzes")){listing=true;message("فهرست باز شد؛ «خواندن فهرست» را بزن.");}
      }
    });
    root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

    translation=txt("",14);
    translation.setTextIsSelectable(true);
    translation.setPadding(dp(14),dp(10),dp(14),dp(10));
    answer=new ScrollView(this);
    answer.setBackgroundColor(0xfff4f7f7);
    answer.addView(translation);
    answer.setVisibility(View.GONE);
    root.addView(answer,new LinearLayout.LayoutParams(-1,0,0.6f));

    toggle.setOnClickListener(v->{
      boolean open=panelScroll.getVisibility()!=View.VISIBLE;
      panelScroll.setVisibility(open?View.VISIBLE:View.GONE);
      toggle.setText(open?"فیلترها و ابزارها  ▲":"فیلترها و ابزارها  ▼");
    });
    search.setOnClickListener(v->{page=1;openList();});
    more.setOnClickListener(v->{page++;openList();});
    read.setOnClickListener(v->{collect();panelScroll.setVisibility(View.GONE);toggle.setText("فیلترها و ابزارها  ▼");});
    next.setOnClickListener(v->nextCase());
    history.setOnClickListener(v->showHistory());
    key.setOnClickListener(v->showKey());
    translate.setOnClickListener(v->translateCurrent());
  }

  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

  private Spinner dropdown(String[] values){
    Spinner sp=new Spinner(this);
    sp.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
      private void format(TextView t){
        t.setTextSize(15);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setPadding(dp(12),0,dp(12),0);
      }
      @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
        TextView t=(TextView)super.getView(position,convertView,parent);
        format(t);return t;
      }
      @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){
        TextView t=(TextView)super.getDropDownView(position,convertView,parent);
        format(t);t.setMinHeight(dp(48));return t;
      }
    };
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp.setAdapter(adapter);
    sp.setBackgroundColor(0xffedf5f5);
    return sp;
  }

  private void addButtonPair(LinearLayout parent,Button first,Button second){
    LinearLayout bar=row();
    LinearLayout.LayoutParams one=new LinearLayout.LayoutParams(0,dp(52),1);
    one.setMargins(dp(4),dp(3),dp(4),dp(3));
    LinearLayout.LayoutParams two=new LinearLayout.LayoutParams(0,dp(52),1);
    two.setMargins(dp(4),dp(3),dp(4),dp(3));
    bar.addView(first,one);
    bar.addView(second,two);
    parent.addView(bar,new LinearLayout.LayoutParams(-1,-2));
  }

  private Button btnAlt(String s){
    Button b=btn(s);
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xffe4f2f2);
    bg.setCornerRadius(dp(11));
    b.setBackground(bg);
    b.setTextColor(teal);
    return b;
  }
  private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(1);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return x;}
  private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(0);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return x;}
  private TextView txt(String s,int sp){TextView x=new TextView(this);x.setText(s);x.setTextSize(sp);x.setTextDirection(View.TEXT_DIRECTION_RTL);x.setTextColor(Color.DKGRAY);return x;}
  private Button btn(String s){
    Button b=new Button(this);
    b.setAllCaps(false);
    b.setText(s);
    b.setTextSize(14);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(8),0,dp(8),0);
    b.setMinHeight(dp(48));
    b.setTextColor(Color.WHITE);
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(teal);
    bg.setCornerRadius(dp(11));
    b.setBackground(bg);
    return b;
  }
  private void message(String s){info.setText(s);}
  private String normalize(String url){try{Uri u=Uri.parse(url);String p=u.getPath();if(p==null)return "";if(p.matches("/cases/[^/]+/?")||p.matches("/quizzes/[0-9]+/?"))return p.replaceAll("/$","");}catch(Exception ignored){}return "";}
  private void openList(){
    links.clear();active="";translation.setText("");answer.setVisibility(View.GONE);
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
    for(String u:links){if(!seen.contains(normalize(u))){active=normalize(u);web.loadUrl(u);translation.setText("");answer.setVisibility(View.GONE);return;}}
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
            runOnUiThread(()->{if(url.equals(web.getUrl())){translation.setText(fa);answer.setVisibility(View.VISIBLE);message("ترجمه فارسی آماده شد؛ با متن اصلی مقایسه کن.");}});
          }catch(Exception err){runOnUiThread(()->message("خطا در ترجمه: "+err.getMessage()));}
        });
      }catch(Exception err){message("متن صفحه قابل خواندن نیست.");}
    });
  }
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){worker.shutdownNow();if(web!=null)web.destroy();super.onDestroy();}
}
