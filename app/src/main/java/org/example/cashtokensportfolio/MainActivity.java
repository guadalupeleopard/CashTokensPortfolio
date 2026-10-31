package org.example.cashtokensportfolio;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.caverock.androidsvg.SVG;
import android.view.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.math.*;
import java.util.*;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

public class MainActivity extends Activity {
  LinearLayout root, items, performanceHolder; TextView total, status; int performanceDays=7; Button themeButton, chartButton, copyErrorButton; JSONArray assets = new JSONArray(); int selectedChartSlice=-1;
  int themeMode=0; // 0=Day, 1=Dark, 2=Bitcoin Cash
  boolean chartPageOpen=false;
  boolean assetHistoryOpen=false;
  long lastMainBackPress=0;
  String displayCurrency="USD";
  double currencyRate=1.0;
  final String[] currencyCodes={"USD","BCH","MXN","ARS","VND","PHP","JPY","CNY"};
  final String[] currencyNames={"US Dollar (USD)","Bitcoin Cash (BCH)","Mexican Peso (MXN)","Argentine Peso (ARS)","Vietnamese Dong (VND)","Philippine Peso (PHP)","Japanese Yen (JPY)","Chinese Yuan (CNY)"};
  final HashMap<String, Double> prices = new HashMap<>();
  final HashMap<String, String> ids = new HashMap<>();
  final HashMap<String, android.graphics.Bitmap> cashTokenIconCache = new HashMap<>();
  final HashSet<String> iconRequests = new HashSet<>();
  final String PREF="portfolio";
  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  void configureNavigationBar(){
    Window window=getWindow();
    window.setNavigationBarColor(Color.TRANSPARENT);
    if(Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
    updateNavigationBarAppearance();
  }
  void updateNavigationBarAppearance(){
    Window window=getWindow();
    int flags=View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    if(themeMode==0)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    View decor=window.getDecorView();
    decor.setBackgroundColor(pageColor());
    decor.setSystemUiVisibility(flags);
    window.setNavigationBarColor(Color.TRANSPARENT);
    if(Build.VERSION.SDK_INT>=29){
      window.setNavigationBarContrastEnforced(false);
      window.setNavigationBarDividerColor(Color.TRANSPARENT);
    }
  }
  TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(20,40,43));t.setPadding(dp(4),dp(10),dp(4),dp(10));return t;}
  void addButton(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);parent.addView(b);b.setOnClickListener(v->action.run());}
  int pageColor(){return themeMode==1?Color.BLACK:themeMode==2?Color.rgb(10,193,142):Color.WHITE;}
  int cardColor(){return themeMode==1?Color.rgb(18,18,18):themeMode==2?Color.rgb(7,151,108):Color.WHITE;}
  int textColor(){return themeMode==1?Color.WHITE:themeMode==2?Color.WHITE:Color.BLACK;}
  int buttonColor(){return themeMode==1?Color.rgb(45,45,45):themeMode==2?Color.rgb(0,105,76):Color.rgb(235,241,239);}
  void openMenu(){String current=themeMode==0?"Day":themeMode==1?"Dark":"Bitcoin Cash Green";new AlertDialog.Builder(this).setTitle("Menu").setItems(new String[]{"Background: "+current,"Currency: "+displayCurrency,"Asset Price History","Donate","About"},(dialog,which)->{if(which==0)chooseTheme();else if(which==1)chooseCurrency();else if(which==2)chooseAssetForHistory();else if(which==3)showDonate();else if(which==4)showAbout();}).show();}
  void showHistoryPage(){assetHistoryOpen=false;chartPageOpen=false;ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(16),dp(18),dp(24));page.setBackgroundColor(pageColor());scroll.addView(page);LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("← Back");back.setOnClickListener(v->build());header.addView(back);TextView title=label("Asset Price History",22);header.addView(title,new LinearLayout.LayoutParams(0,-2,1f));page.addView(header);page.addView(label("Select a CashToken to view its available historical prices.",14));for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a==null||!"CashToken".equalsIgnoreCase(a.optString("type","")))continue;Button b=new Button(this);b.setText(a.optString("name","Asset")+" ("+a.optString("type","")+")");b.setAllCaps(false);page.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->showAssetHistory(a));}setContentView(scroll);applyThemeTo(page);}
  void chooseAssetForHistory(){ArrayList<JSONObject> choices=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&"CashToken".equalsIgnoreCase(a.optString("type",""))){choices.add(a);labels.add(a.optString("name","Asset")+" ("+a.optString("type","")+")");}}if(choices.isEmpty()){Toast.makeText(this,"Add a CashToken first.",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("Choose asset price history").setItems(labels.toArray(new String[0]),(dialog,which)->{if(which>=0&&which<choices.size())showAssetHistory(choices.get(which));}).show();}

  final String DONATION_BCH="bitcoincash:qzdz6jxh5kjglqzr454x4pjmwwfw5p336uupk7adpg";
  final String DONATION_TOKEN="bitcoincash:zzdz6jxh5kjglqzr454x4pjmwwfw5p336umt9qnt7m";
  void copyDonation(String address){
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CashTrack donation address",address));
    Toast.makeText(this,"Address copied",Toast.LENGTH_SHORT).show();
  }
  android.graphics.Bitmap donationQr(String address){
    try{
      com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(address,com.google.zxing.BarcodeFormat.QR_CODE,480,480);
      android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(480,480,android.graphics.Bitmap.Config.ARGB_8888);
      for(int y=0;y<480;y++)for(int x=0;x<480;x++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
      return bitmap;
    }catch(Exception e){return null;}
  }
  void addDonationEntry(LinearLayout layout,String heading,String address){
    TextView label=new TextView(this);
    label.setText(heading);
    label.setTextSize(19);
    label.setTypeface(null,1);
    label.setGravity(Gravity.CENTER);
    label.setPadding(0,dp(16),0,dp(8));
    layout.addView(label);
    ImageView qr=new ImageView(this);
    android.graphics.Bitmap bitmap=donationQr(address);
    if(bitmap!=null)qr.setImageBitmap(bitmap);
    qr.setContentDescription("Copy "+heading+" address");
    qr.setOnClickListener(v->copyDonation(address));
    LinearLayout.LayoutParams qrParams=new LinearLayout.LayoutParams(dp(210),dp(210));
    qrParams.gravity=Gravity.CENTER_HORIZONTAL;
    layout.addView(qr,qrParams);
    TextView addressText=new TextView(this);
    addressText.setText(address);
    addressText.setTextSize(14);
    addressText.setGravity(Gravity.CENTER);
    addressText.setTextIsSelectable(false);
    addressText.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
    addressText.setPadding(dp(4),dp(10),dp(4),dp(10));
    addressText.setOnClickListener(v->copyDonation(address));
    layout.addView(addressText);
    TextView hint=new TextView(this);
    hint.setText("Tap the QR code or address to copy");
    hint.setTextSize(12);
    hint.setGravity(Gravity.CENTER);
    layout.addView(hint);
  }
  void showDonate(){
    ScrollView scroll=new ScrollView(this);
    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(dp(20),dp(8),dp(20),dp(20));
    scroll.addView(layout);
    addDonationEntry(layout,"Bitcoin Cash",DONATION_BCH);
    addDonationEntry(layout,"CashTokens",DONATION_TOKEN);
    new AlertDialog.Builder(this).setTitle("Donate to CashTrack").setView(scroll).setPositiveButton("Close",null).show();
  }
  void showAbout(){
    String version="Unknown";
    try{
      android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
      version=info.versionName+" (build "+(Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode)+")";
    }catch(Exception ignored){}
    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(dp(24),dp(12),dp(24),dp(8));
    TextView details=new TextView(this);
    details.setText("CashTrack is a CashTokens and other cryptocurrencies tracker.\n\nRelease version: "+version+"\n\nContact: francis105d1@gmail.com\n\nOfficial GitHub repository:\nhttps://github.com/guadalupeleopard/CashTokensPortfolio");
    details.setTextSize(15);
    details.setAutoLinkMask(android.text.util.Linkify.EMAIL_ADDRESSES|android.text.util.Linkify.WEB_URLS);
    details.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    layout.addView(details);
    new AlertDialog.Builder(this).setTitle("About CashTrack").setView(layout).setPositiveButton("Close",null).show();
  }
  void chooseCurrency(){new AlertDialog.Builder(this).setTitle("Display currency").setSingleChoiceItems(currencyNames,Math.max(0,Arrays.asList(currencyCodes).indexOf(displayCurrency)),(dialog,which)->{displayCurrency=currencyCodes[which];getPreferences(0).edit().putString("display_currency",displayCurrency).apply();dialog.dismiss();render();refresh();}).setNegativeButton("Cancel",null).show();}
  String currencySymbol(){switch(displayCurrency){case "BCH":return "BCH ";case "MXN":return "MX$";case "ARS":return "AR$";case "VND":return "₫";case "PHP":return "₱";case "JPY":return "¥";case "CNY":return "元";default:return "$";}}
  String formatValue(double usdValue){double value=displayCurrency.equals("BCH")?(currencyRate>0?usdValue/currencyRate:0):usdValue*currencyRate;String formatted=String.format(Locale.US,"%,.2f",value);if(displayCurrency.equals("BCH"))formatted=String.format(Locale.US,"%,.8f",value);return currencySymbol()+formatted;}
  void chooseTheme(){new AlertDialog.Builder(this).setTitle("Choose background").setItems(new String[]{"☀  Day","☾  Dark","₿  Bitcoin Cash Green"},(dialog,which)->{themeMode=which;getPreferences(0).edit().putInt("theme_mode",themeMode).apply();applyTheme();}).show();}
  void applyTheme(){updateNavigationBarAppearance();if(root==null)return;root.setBackgroundColor(pageColor());if(themeButton!=null){themeButton.setText("☰");themeButton.setContentDescription("Open menu");}applyThemeTo(root);}
  void applyThemeTo(View view){if(view instanceof TextView){((TextView)view).setTextColor(textColor());}if(view instanceof Button){view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor()));}if(view instanceof LinearLayout){LinearLayout ll=(LinearLayout)view;if(ll==root)ll.setBackgroundColor(pageColor());else if(ll==items){}else if(ll.getOrientation()==LinearLayout.VERTICAL&&ll.getChildCount()>0)ll.setBackgroundColor(cardColor());}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyThemeTo(group.getChildAt(i));}}
  @Override public void onCreate(Bundle b){super.onCreate(b);themeMode=getPreferences(0).getInt("theme_mode",0);displayCurrency=getPreferences(0).getString("display_currency","USD");if(!Arrays.asList(currencyCodes).contains(displayCurrency))displayCurrency="USD";configureNavigationBar();try{assets=new JSONArray(getPreferences(0).getString(PREF,"[]"));}catch(Exception ignored){} scheduleDailyPortfolioSnapshot(); build(); refresh();}
  void scheduleDailyPortfolioSnapshot(){
    Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(DailyPortfolioWorker.class,24,java.util.concurrent.TimeUnit.HOURS).setConstraints(constraints).build();
    WorkManager.getInstance(this).enqueueUniquePeriodicWork("cashtrack_daily_portfolio_snapshot",ExistingPeriodicWorkPolicy.KEEP,request);
  }
  void save(){getPreferences(0).edit().putString(PREF,assets.toString()).apply();}
  @Override public void onBackPressed(){
    if(assetHistoryOpen){assetHistoryOpen=false;build();return;}
    if(chartPageOpen){build();return;}
    long now=System.currentTimeMillis();
    if(now-lastMainBackPress<=1800){
      lastMainBackPress=0;
      finish();
      return;
    }
    lastMainBackPress=now;
    Toast.makeText(this,"Press Back again to close CashTrack.",Toast.LENGTH_SHORT).show();
  }

  void build(){chartPageOpen=false;assetHistoryOpen=false;ScrollView scroll=new ScrollView(this);
    scroll.setOnApplyWindowInsetsListener((view,insets)->{
      int bottom;
      if(Build.VERSION.SDK_INT>=30)bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
      else bottom=insets.getSystemWindowInsetBottom();
      view.setPadding(0,0,0,bottom);
      return insets;
    });
    root=new LinearLayout(this);root.setPadding(dp(18),dp(20),dp(18),dp(20));root.setOrientation(1);root.setBackgroundColor(Color.rgb(244,249,247));scroll.addView(root);setContentView(scroll);
    LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);ImageView appLogo=new ImageView(this);appLogo.setImageResource(R.drawable.cashtrack_icon);appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams appLogoLp=new LinearLayout.LayoutParams(dp(48),dp(48));appLogoLp.rightMargin=dp(12);header.addView(appLogo,appLogoLp);TextView heading=label("CashTokens Portfolio",26);LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(0,-2,1f);header.addView(heading,headingLp);chartButton=new Button(this);chartButton.setText("◔");chartButton.setTextSize(24);chartButton.setGravity(Gravity.CENTER);chartButton.setMinWidth(0);chartButton.setMinimumWidth(0);chartButton.setPadding(0,0,0,0);chartButton.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));chartButton.setContentDescription("Open portfolio chart");header.addView(chartButton);chartButton.setOnClickListener(v->showChart());ImageButton historyButton=new ImageButton(this);historyButton.setImageResource(R.drawable.ic_trending_up);historyButton.setScaleType(ImageView.ScaleType.FIT_CENTER);historyButton.setPadding(dp(16.5f),dp(16.5f),dp(16.5f),dp(16.5f));historyButton.setBackgroundResource(android.R.drawable.btn_default);historyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor()));historyButton.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));historyButton.setContentDescription("Open asset price history");header.addView(historyButton);historyButton.setOnClickListener(v->showHistoryPage());themeButton=new Button(this);themeButton.setText("☰");themeButton.setTextSize(24);themeButton.setGravity(Gravity.CENTER);themeButton.setMinWidth(0);themeButton.setMinimumWidth(0);themeButton.setPadding(0,0,0,0);themeButton.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));themeButton.setContentDescription("Open menu");header.addView(themeButton);themeButton.setOnClickListener(v->openMenu());root.addView(header);root.addView(label("Watch-only • Stored on this device",13));total=label("Total: —",23);root.addView(total);status=label("Prices not loaded",12);root.addView(status);copyErrorButton=new Button(this);copyErrorButton.setText("Copy Error Message");copyErrorButton.setAllCaps(false);copyErrorButton.setVisibility(View.GONE);root.addView(copyErrorButton);copyErrorButton.setOnClickListener(v->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CashTrack diagnostic message",status.getText()));Toast.makeText(this,"Full diagnostic message copied",Toast.LENGTH_SHORT).show();});
    addButton(root,"+ Add asset",this::addAssetDialog);addButton(root,"Refresh market prices",this::refresh);items=new LinearLayout(this);items.setOrientation(1);root.addView(items);performanceHolder=new LinearLayout(this);performanceHolder.setOrientation(1);root.addView(performanceHolder);render();}

  int chartAssetPreferredColor(JSONObject a){
    String type=a.optString("type","").toUpperCase(Locale.US);
    String name=a.optString("name","").toLowerCase(Locale.US);
    if(isBchChartAsset(a))return Color.rgb(12,193,142);
    if(isPiChartAsset(a))return Color.rgb(123,78,200);
    if(isUsdtChartAsset(a))return Color.rgb(255,193,7);
    if(isShibChartAsset(a))return Color.rgb(255,145,0);
    if(name.contains("shibch"))return Color.rgb(239,91,52);
    if(name.contains("moria")||name.contains("musd"))return Color.rgb(42,119,210);
    if(name.contains("ethereum")||name.equals("eth"))return Color.rgb(98,126,234);
    if(name.contains("bitcoin")||name.equals("btc"))return Color.rgb(247,147,26);
    int hash=(a.optString("type","")+":"+a.optString("id","")+":"+a.optString("name","")).hashCode();
    return Color.HSVToColor(new float[]{Math.floorMod(hash,360),0.72f,0.88f});
  }
  boolean isBchChartAsset(JSONObject a){
    return a.optString("type","").equalsIgnoreCase("BCH")||a.optString("name","").toLowerCase(Locale.US).contains("bitcoin cash");
  }
  boolean isPiChartAsset(JSONObject a){
    String type=a.optString("type","");String name=a.optString("name","").toLowerCase(Locale.US);
    return type.equalsIgnoreCase("PI")||name.equals("pi")||name.contains("pi network");
  }
  boolean isUsdtChartAsset(JSONObject a){
    String type=a.optString("type","");String name=a.optString("name","").toLowerCase(Locale.US);
    return type.equalsIgnoreCase("USDT")||name.equals("usdt")||name.contains("tether");
  }
  boolean isShibChartAsset(JSONObject a){
    String type=a.optString("type","").toUpperCase(Locale.US);String name=a.optString("name","").toLowerCase(Locale.US);
    return type.equals("SHIB")||name.equals("shib")||name.contains("shiba inu");
  }
  String chartColorKey(JSONObject a){return key(a);}
  HashMap<String,Integer> ensureUniqueChartColors(){
    final int bchGreen=Color.rgb(12,193,142), piPurple=Color.rgb(123,78,200),
      usdtYellow=Color.rgb(255,193,7), shibOrange=Color.rgb(255,145,0);
    android.content.SharedPreferences prefs=getSharedPreferences(PREF+"_chart_colors",MODE_PRIVATE);
    HashMap<String,Integer> assigned=new HashMap<>();
    HashSet<Integer> used=new HashSet<>();
    // Reserve signature colors first so BCH, Pi, USDT, and Shiba Inu keep their requested colors.
    for(int i=0;i<assets.length();i++){
      JSONObject a=assets.optJSONObject(i);if(a==null)continue;
      String k=chartColorKey(a);
      if(isBchChartAsset(a)){assigned.put(k,bchGreen);used.add(bchGreen);}
      else if(isPiChartAsset(a)){assigned.put(k,piPurple);used.add(piPurple);}
      else if(isUsdtChartAsset(a)){assigned.put(k,usdtYellow);used.add(usdtYellow);}
      else if(isShibChartAsset(a)){assigned.put(k,shibOrange);used.add(shibOrange);}
    }
    // Keep each previously saved color when it is still unique.
    for(int i=0;i<assets.length();i++){
      JSONObject a=assets.optJSONObject(i);if(a==null)continue;
      String k=chartColorKey(a);if(assigned.containsKey(k))continue;
      if(!prefs.contains(k))continue;
      int saved=prefs.getInt(k,Color.TRANSPARENT);
      if(saved!=Color.TRANSPARENT&&!used.contains(saved)){assigned.put(k,saved);used.add(saved);}
    }
    // Give every other current asset its own color, and save it so future additions
    // cannot take a color that already belongs to an existing asset.
    for(int i=0;i<assets.length();i++){
      JSONObject a=assets.optJSONObject(i);if(a==null)continue;
      String k=chartColorKey(a);if(assigned.containsKey(k))continue;
      int preferred=chartAssetPreferredColor(a);
      int chosen=Color.TRANSPARENT;
      if(preferred!=bchGreen&&preferred!=piPurple&&preferred!=usdtYellow&&preferred!=shibOrange&&!used.contains(preferred))chosen=preferred;
      if(chosen==Color.TRANSPARENT){
        int[][] alternatives={
          {Color.rgb(247,147,26),Color.rgb(230,126,34),Color.rgb(255,193,7),Color.rgb(190,100,35)},
          {Color.rgb(42,119,210),Color.rgb(52,152,219),Color.rgb(41,128,185),Color.rgb(0,188,212)},
          {Color.rgb(239,91,52),Color.rgb(231,76,60),Color.rgb(192,57,43),Color.rgb(255,112,67)},
          {Color.rgb(98,126,234),Color.rgb(103,58,183),Color.rgb(156,39,176),Color.rgb(121,85,72)},
          {Color.rgb(38,161,123),Color.rgb(46,204,113),Color.rgb(0,150,136),Color.rgb(139,195,74)},
          {Color.rgb(233,30,99),Color.rgb(0,150,136),Color.rgb(63,81,181),Color.rgb(121,134,203)}
        };
        int group=Math.floorMod((a.optString("type","")+a.optString("name","")).hashCode(),alternatives.length);
        for(int c:alternatives[group])if(!used.contains(c)&&c!=bchGreen&&c!=piPurple&&c!=usdtYellow&&c!=shibOrange){chosen=c;break;}
      }
      if(chosen==Color.TRANSPARENT){
        int[] palette={Color.rgb(255,193,7),Color.rgb(0,188,212),Color.rgb(233,30,99),Color.rgb(103,58,183),Color.rgb(121,85,72),Color.rgb(96,125,139),Color.rgb(205,220,57),Color.rgb(255,87,34),Color.rgb(63,81,181),Color.rgb(0,150,136),Color.rgb(156,39,176),Color.rgb(121,134,203)};
        for(int c:palette)if(!used.contains(c)&&c!=bchGreen&&c!=piPurple&&c!=usdtYellow&&c!=shibOrange){chosen=c;break;}
      }
      if(chosen==Color.TRANSPARENT){
        for(int hue=0;hue<360;hue++){int c=Color.HSVToColor(new float[]{hue,0.78f,0.92f});if(!used.contains(c)&&c!=bchGreen&&c!=piPurple){chosen=c;break;}}
      }
      // Continue with progressively different hues if a very large portfolio uses
      // every color in the first pass; uniqueness is more important than a fixed palette.
      if(chosen==Color.TRANSPARENT){
        for(int hue=0;hue<360;hue++){int c=Color.HSVToColor(new float[]{hue,0.45f,0.70f});if(!used.contains(c)&&c!=bchGreen&&c!=piPurple){chosen=c;break;}}
      }
      if(chosen==Color.TRANSPARENT)chosen=Color.HSVToColor(new float[]{(float)(assigned.size()*137.508%360),0.90f,0.95f});
      assigned.put(k,chosen);used.add(chosen);
    }
    android.content.SharedPreferences.Editor editor=prefs.edit();
    for(Map.Entry<String,Integer> entry:assigned.entrySet())editor.putInt(entry.getKey(),entry.getValue());
    editor.apply();
    return assigned;
  }
  void showChart(){
    chartPageOpen=true;
    ScrollView scroll=new ScrollView(this);
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(16),dp(18),dp(24));page.setBackgroundColor(pageColor());scroll.addView(page);
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setOrientation(LinearLayout.HORIZONTAL);
    Button back=new Button(this);back.setText("← Back");back.setContentDescription("Back to assets");header.addView(back);back.setOnClickListener(v->build());
    TextView title=label("Portfolio Chart",23);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1f);titleLp.leftMargin=dp(12);header.addView(title,titleLp);page.addView(header);
    page.addView(label("3D asset allocation by current known value",14));
    ArrayList<String> names=new ArrayList<>();ArrayList<Double> values=new ArrayList<>();ArrayList<Integer> colors=new ArrayList<>();double sum=0;
    HashMap<String,Integer> chartColors=ensureUniqueChartColors();
    for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a==null)continue;Double price=prices.get(key(a));double qty=a.optDouble("quantity",0);if(price==null||!Double.isFinite(price)||price<0||qty<=0)continue;double value=qty*price;if(!Double.isFinite(value)||value<=0)continue;names.add(a.optString("name","Asset")+" ("+a.optString("type","")+")");values.add(value);colors.add(chartColors.get(chartColorKey(a)));sum+=value;}
    if(values.isEmpty()){TextView empty=label("No priced assets with a positive value are available to chart yet. Add assets and refresh market prices, then return here.",16);page.addView(empty);setContentView(scroll);applyThemeTo(page);return;}
    final double chartTotal=sum;
    PieChartView pie=new PieChartView(this,values,colors,chartTotal);LinearLayout.LayoutParams pieLp=new LinearLayout.LayoutParams(-1,dp(300));pieLp.topMargin=dp(8);pieLp.gravity=Gravity.CENTER_HORIZONTAL;page.addView(pie,pieLp);
    TextView selected=label("Total charted value: "+formatValue(chartTotal),17);page.addView(selected);
    TextView instruction=label("Tap a 3D slice or an asset below. Both selections stay synchronized.",13);page.addView(instruction);
    ArrayList<LinearLayout> chartRows=new ArrayList<>();
    final Runnable[] syncRows={()->{for(int j=0;j<chartRows.size();j++){chartRows.get(j).setBackgroundColor(j==pie.selected?buttonColor():pageColor());}}};
    for(int i=0;i<names.size();i++){final int index=i;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(5),dp(8),dp(5));row.setBackgroundColor(pageColor());View swatch=new View(this);swatch.setBackgroundColor(colors.get(i));LinearLayout.LayoutParams swatchLp=new LinearLayout.LayoutParams(dp(14),dp(14));swatchLp.rightMargin=dp(10);row.addView(swatch,swatchLp);TextView name=label(names.get(i),15);LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,-2,1f);row.addView(name,nameLp);TextView percent=label(String.format(Locale.US,"%.2f%%",values.get(i)/chartTotal*100),15);row.addView(percent);row.setOnClickListener(v->{pie.setSelected(index);syncRows[0].run();});chartRows.add(row);page.addView(row);View divider=new View(this);divider.setBackgroundColor(themeMode==1?Color.DKGRAY:Color.LTGRAY);page.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
    pie.setOnSliceSelected(index->{if(index>=0&&index<names.size()){syncRows[0].run();}});
    setContentView(scroll);applyThemeTo(page);
  }

  JSONObject findAssetForChart(String displayName){
    for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&(a.optString("name","Asset")+" ("+a.optString("type","")+")").equals(displayName))return a;}
    return null;
  }
  void showAssetHistory(JSONObject asset){
    if(asset==null||!"CashToken".equalsIgnoreCase(asset.optString("type",""))){Toast.makeText(this,"Price history is available only for CashTokens.",Toast.LENGTH_SHORT).show();return;}
    assetHistoryOpen=true;chartPageOpen=true;
    ScrollView scroll=new ScrollView(this);
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(12),dp(18),dp(24));page.setBackgroundColor(pageColor());scroll.addView(page);
    LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
    Button back=new Button(this);back.setText("← Back");back.setOnClickListener(v->{assetHistoryOpen=false;showHistoryPage();});header.addView(back);
    TextView title=label(asset.optString("name","Asset")+" Price History",20);header.addView(title,new LinearLayout.LayoutParams(0,-2,1f));page.addView(header);
    page.addView(label("CashToken historical prices from CauldronSwap, quoted in BCH satoshis.",14));
    LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);page.addView(controls);
    TextView statusText=label("Loading historical prices…",14);page.addView(statusText);
    FrameLayout graphHolder=new FrameLayout(this);page.addView(graphHolder,new LinearLayout.LayoutParams(-1,dp(300)));
    final String[] interval={"Daily"};final ArrayList<Long> timestamps=new ArrayList<>();final ArrayList<Double> priceValues=new ArrayList<>();
    final HistoryGraph[] graph={null};final Runnable[] redraw={()->{}};
    for(String option:new String[]{"Daily","Weekly","Monthly"}){Button b=new Button(this);b.setText(option);controls.addView(b,new LinearLayout.LayoutParams(0,-2,1f));b.setOnClickListener(v->{interval[0]=option;for(int j=0;j<controls.getChildCount();j++)controls.getChildAt(j).setAlpha(controls.getChildAt(j)==b?1f:0.65f);redraw[0].run();});}
    new Thread(()->{
      ArrayList<Long> ts=new ArrayList<>();ArrayList<Double> ps=new ArrayList<>();String failure=null;String sourceLabel="";
      try{
        if("CashToken".equalsIgnoreCase(asset.optString("type"))){
          // Query CauldronSwap for recent history first. A bounded range avoids
          // asking the indexer to scan the token's entire chain history.
          // Some indexer deployments expose usable data through candlesticks
          // even when the history endpoint returns no points.
          String token=asset.optString("id").toLowerCase(Locale.US);
          long now=System.currentTimeMillis()/1000L;
          long startTime=now-5L*365L*24L*60L*60L;
          double scale=isCauldronStablecoin(asset)?100d:1d;
          try{
            Object result=fetchAny("https://indexer.riften.net/cauldron/price/"+token+"/history?start="+startTime+"&end="+now+"&stepsize=86400");
            if(result instanceof JSONObject){
              JSONArray points=((JSONObject)result).optJSONArray("history");
              if(points!=null)for(int i=0;i<points.length();i++){
                JSONObject p=points.optJSONObject(i);if(p==null)continue;
                long time=p.optLong("time",0);double priceUnits=p.optDouble("avg",Double.NaN);
                if(time>0&&Double.isFinite(priceUnits)&&priceUnits>=0){
                  ts.add(time*1000L);ps.add(priceUnits*scale);
                }
              }
            }
          }catch(Exception ignored){}
          if(ts.isEmpty()){
            try{
              Object result=fetchAny("https://indexer.riften.net/cauldron/price/"+token+"/candlesticks?start="+startTime+"&end="+now+"&stepsize=86400");
              if(result instanceof JSONObject){
                JSONArray points=((JSONObject)result).optJSONArray("candlesticks");
                if(points!=null)for(int i=0;i<points.length();i++){
                  JSONObject p=points.optJSONObject(i);if(p==null)continue;
                  long time=p.optLong("time",0);
                  double priceUnits=p.optDouble("close",p.optDouble("avg",Double.NaN));
                  if(time>0&&Double.isFinite(priceUnits)&&priceUnits>=0){
                    ts.add(time*1000L);ps.add(priceUnits*scale);
                  }
                }
              }
            }catch(Exception ignored){}
          }
          if(!ts.isEmpty()){
            // Ensure chronological order before drawing the graph.
            ArrayList<Integer> order=new ArrayList<>();
            for(int i=0;i<ts.size();i++)order.add(i);
            Collections.sort(order,(a,b)->Long.compare(ts.get(a),ts.get(b)));
            ArrayList<Long> sortedTs=new ArrayList<>();ArrayList<Double> sortedPs=new ArrayList<>();
            for(Integer index:order){sortedTs.add(ts.get(index));sortedPs.add(ps.get(index));}
            ts.clear();ts.addAll(sortedTs);ps.clear();ps.addAll(sortedPs);
            sourceLabel=" • live CauldronSwap API";
          }
          if(ts.isEmpty()){
            String bundledFile=bundledHistoryFile(asset);
            if(bundledFile!=null){
              loadBundledCashTokenHistory(bundledFile,ts,ps,scale);
              if(!ts.isEmpty())sourceLabel=" • bundled fallback";
            }
          }
        }
      }catch(Exception e){failure="Could not load price history: "+e.getMessage()+". CauldronSwap may be temporarily unavailable.";}
      final String err=failure;
      final String source=sourceLabel;
      runOnUiThread(()->{
        timestamps.addAll(ts);priceValues.addAll(ps);
        if(priceValues.isEmpty()){statusText.setText(err==null?"No historical price data is available for this asset.":err);return;}
        statusText.setText("Loaded "+priceValues.size()+" historical points"+(" • BCH satoshis"+source));
        graph[0]=new HistoryGraph(this,timestamps,priceValues,interval[0]);graphHolder.addView(graph[0],new FrameLayout.LayoutParams(-1,-1));
        redraw[0]=()->{if(graph[0]!=null){graph[0].interval=interval[0];graph[0].invalidate();}};
        redraw[0].run();applyThemeTo(page);
      });
    }).start();
    setContentView(scroll);applyThemeTo(page);
  }
  // Cauldron prices for these USD stablecoins are expressed in micro-BCH units; convert to satoshi-equivalent values (x100) for charting and USD conversion.
  boolean isCauldronStablecoin(JSONObject asset){
    if(asset==null)return false;
    String name=asset.optString("name","").toLowerCase(Locale.US).replaceAll("[^a-z0-9]","");
    return name.equals("paryonusd")||name.equals("paryon")||name.equals("pusd")||name.equals("moriausd")||name.equals("moria")||name.equals("musd");
  }
  String bundledHistoryFile(JSONObject asset){
    String name=asset.optString("name","").toLowerCase(Locale.US).replaceAll("[^a-z0-9]","");
    if(name.equals("shibch")||name.equals("shibainu")||name.equals("shib")||name.equals("shibainucashtoken"))return "shibch_daily.json";
    if(name.equals("furu")||name.equals("furutoken")||name.equals("furutokens"))return "furu_daily.json";
    if(name.equals("paryonusd")||name.equals("paryon")||name.equals("pusd"))return "paryonusd_daily.json";
    if(name.equals("moriausd")||name.equals("moria")||name.equals("musd"))return "moriausd_daily.json";
    return null;
  }
  void loadBundledCashTokenHistory(String file,ArrayList<Long> timestamps,ArrayList<Double> prices,double unitMultiplier)throws Exception{
    byte[] buffer=new byte[8192];ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    try(InputStream input=getAssets().open("price_history/"+file)){int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);}
    JSONObject root=new JSONObject(new String(bytes.toByteArray(),"UTF-8"));
    JSONArray points=root.optJSONArray("history");if(points==null)return;
    for(int i=0;i<points.length();i++){
      JSONObject point=points.optJSONObject(i);if(point==null)continue;
      long time=point.optLong("time",0);double price=point.optDouble("avg",Double.NaN);
      if(time>0&&Double.isFinite(price)&&price>=0){timestamps.add(time*1000L);prices.add(price*unitMultiplier);}
    }
  }
  class HistoryGraph extends View{
    final ArrayList<Long> times;final ArrayList<Double> values;String interval;
    final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    HistoryGraph(Context c,ArrayList<Long> t,ArrayList<Double> v,String i){super(c);times=new ArrayList<>(t);values=new ArrayList<>(v);interval=i;setContentDescription("Asset historical price line chart");}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(values.isEmpty())return;
      long unit=86400000L;if("Weekly".equals(interval))unit*=7;else if("Monthly".equals(interval))unit=30L*86400000L;
      TreeMap<Long,double[]> groups=new TreeMap<>();for(int i=0;i<values.size();i++){long bucket=(times.get(i)/unit)*unit;double[] a=groups.get(bucket);if(a==null){a=new double[]{0,0};groups.put(bucket,a);}a[0]+=values.get(i);a[1]++;}
      ArrayList<Double> vals=new ArrayList<>();for(double[] a:groups.values())vals.add(a[0]/a[1]);if(vals.isEmpty())return;
      double min=Collections.min(vals),max=Collections.max(vals);if(min==max){min=Math.max(0,min*0.95);max=max*1.05+0.00000001;}
      float left=dp(62),right=getWidth()-dp(12),top=dp(20),bottom=getHeight()-dp(42);if(right<=left||bottom<=top)return;
      paint.setColor(textColor());paint.setTextSize(dp(11));paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawText(String.format(Locale.US,"%.6g",max),dp(2),top+dp(4),paint);canvas.drawText(String.format(Locale.US,"%.6g",min),dp(2),bottom,paint);
      paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawLine(left,top,left,bottom,paint);canvas.drawLine(left,bottom,right,bottom,paint);
      paint.setColor(themeMode==1?Color.rgb(100,220,180):themeMode==2?Color.WHITE:Color.rgb(12,150,115));paint.setStrokeWidth(dp(2));android.graphics.Path path=new android.graphics.Path();
      for(int i=0;i<vals.size();i++){float x=left+(right-left)*(vals.size()==1?0.5f:(float)i/(vals.size()-1));float y=bottom-(float)((vals.get(i)-min)/(max-min))*(bottom-top);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}canvas.drawPath(path,paint);
      paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(textColor());String first=android.text.format.DateFormat.format("yyyy-MM-dd",groups.firstKey()).toString(),last=android.text.format.DateFormat.format("yyyy-MM-dd",groups.lastKey()).toString();canvas.drawText(first,left,bottom+dp(22),paint);canvas.drawText(last,Math.max(left,right-paint.measureText(last)),bottom+dp(22),paint);
    }
  }

  interface SliceListener{void onSelected(int index);}
  class PieChartView extends View{
    final ArrayList<Double> values;final ArrayList<Integer> colors;final double total;final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);final android.graphics.RectF oval=new android.graphics.RectF();SliceListener listener;int selected=-1;
    PieChartView(Context context,ArrayList<Double> v,ArrayList<Integer> c,double t){super(context);values=v;colors=c;total=t;setContentDescription("Interactive 3D pie chart of portfolio asset percentages");setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setOnSliceSelected(SliceListener l){listener=l;}
    void setSelected(int i){selected=i;invalidate();if(listener!=null)listener.onSelected(i);}
    int shade(int color,float factor){return Color.rgb(Math.min(255,(int)(Color.red(color)*factor)),Math.min(255,(int)(Color.green(color)*factor)),Math.min(255,(int)(Color.blue(color)*factor)));}
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);float width=Math.min(getWidth()-dp(30),getHeight()*1.18f);float height=width*0.68f;float depth=dp(18);
      if(width<=0||height<=0)return;float left=(getWidth()-width)/2f,top=(getHeight()-height-depth)/2f;
      oval.set(left,top,left+width,top+height);
      boolean isolateSmallSelection=selected>=0&&selected<values.size()&&values.get(selected)/total<0.05d;
      // Extruded side. When a selected slice is under 5%, hide the other slices
      // so the small slice can be examined clearly.
      for(int layer=(int)depth;layer>=1;layer--){
        float start=-90f;
        for(int i=0;i<values.size();i++){
          float sweep=(float)(values.get(i)/total*360d);
          if(isolateSmallSelection&&i!=selected){start+=sweep;continue;}
          if(i==selected){
            float mid=(float)Math.toRadians(start+sweep/2);
            float dx=(float)Math.cos(mid)*dp(13);
            float dy=(float)Math.sin(mid)*dp(9)-dp(13);
            canvas.save();canvas.translate(dx,dy+layer);
            paint.setColor(shade(colors.get(i),0.42f));paint.setStyle(android.graphics.Paint.Style.FILL);
            canvas.drawArc(oval,start,sweep,true,paint);canvas.restore();
          }else{
            canvas.save();canvas.translate(0,layer);
            paint.setColor(shade(colors.get(i),0.52f));paint.setStyle(android.graphics.Paint.Style.FILL);
            canvas.drawArc(oval,start,sweep,true,paint);canvas.restore();
          }
          start+=sweep;
        }
      }
      float start=-90f;
      for(int i=0;i<values.size();i++){
        float sweep=(float)(values.get(i)/total*360d);
        if(isolateSmallSelection&&i!=selected){start+=sweep;continue;}
        paint.setColor(colors.get(i));paint.setStyle(android.graphics.Paint.Style.FILL);
        if(i==selected){
          float mid=(float)Math.toRadians(start+sweep/2);
          float dx=(float)Math.cos(mid)*dp(13);
          float dy=(float)Math.sin(mid)*dp(9)-dp(13);
          canvas.save();canvas.translate(dx,dy);
          canvas.drawArc(oval,start,sweep,true,paint);canvas.restore();
        }else canvas.drawArc(oval,start,sweep,true,paint);
        start+=sweep;
      }
      if(selected>=0&&selected<values.size()){
        paint.setColor(textColor());paint.setTextAlign(android.graphics.Paint.Align.CENTER);paint.setTextSize(dp(14));
        canvas.drawText(String.format(Locale.US,"%.2f%%",values.get(selected)/total*100),getWidth()/2f,getHeight()-dp(2),paint);
      }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
      if(event.getAction()!=MotionEvent.ACTION_UP)return true;
      float width=Math.min(getWidth()-dp(30),getHeight()*1.18f),height=width*0.68f,depth=dp(18);float left=(getWidth()-width)/2f,top=(getHeight()-height-depth)/2f;
      float cx=getWidth()/2f,cy=top+height/2f;double dx=event.getX()-cx,dy=(event.getY()-cy)/0.68d;
      if(Math.sqrt(dx*dx+dy*dy)>width/2f||event.getY()<top-depth||event.getY()>top+height+depth)return true;
      double angle=Math.toDegrees(Math.atan2(dy,dx))+90;if(angle<0)angle+=360;double accumulated=0;
      for(int i=0;i<values.size();i++){accumulated+=values.get(i)/total*360d;if(angle<=accumulated){setSelected(i);break;}}
      return true;
    }
  }

  String key(JSONObject a){return a.optString("type")+":"+a.optString("id");}
  void render(){if(items==null)return;items.removeAllViews();double sum=0;boolean incomplete=false;
    for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a==null)continue;final int index=i;String k=key(a);Double price=prices.get(k);double qty=a.optDouble("quantity",0);String title=a.optString("name","Token");
      String val=price==null?"Price unavailable":formatValue(qty*price);if(price==null)incomplete=true;else sum+=qty*price;
      LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackgroundColor(cardColor());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);items.addView(row,lp);
      LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(0);titleRow.setGravity(Gravity.CENTER_VERTICAL);
      ImageView icon=new ImageView(this);int iconId;
      String assetType=a.optString("type");
      if("BCH".equalsIgnoreCase(assetType))iconId=R.drawable.ic_asset_bch;
      else if("PI".equalsIgnoreCase(assetType))iconId=R.drawable.ic_asset_pi;
      else if("USDT".equalsIgnoreCase(assetType))iconId=R.drawable.ic_asset_usdt;
      else iconId=R.drawable.ic_asset_cashtoken;
      LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(22),dp(22));iconLp.rightMargin=dp(7);
      icon.setImageResource(iconId);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);titleRow.addView(icon,iconLp);String iconMode=a.optString("icon_mode","auto");
      if("custom".equals(iconMode))loadCustomAssetIcon(icon,a.optString("icon_url",""));
      else if("default".equals(iconMode))icon.setImageResource(R.drawable.ic_asset_cashtoken);
      else if("CashToken".equalsIgnoreCase(assetType))loadCashTokenIcon(icon,a.optString("id"),iconMode,a.optString("icon_url",""));
      TextView assetTitle=label(title+" ("+assetType+")",18);assetTitle.setPadding(dp(2),dp(8),dp(4),dp(8));titleRow.addView(assetTitle);
      row.addView(titleRow);row.addView(label("Quantity: "+a.optString("quantity")+"   •   "+val,15));
      addButton(row,"Edit / Remove",()->editDialog(index));
    }
    total.setText("Known value: "+formatValue(sum)+" "+displayCurrency);if(incomplete)total.append("\n(Excludes unpriced assets)");
    if(!incomplete&&!assets.toString().equals("[]")&&Double.isFinite(sum)&&sum>=0)recordPortfolioSnapshot(sum);
    renderPerformance();
    applyTheme();
  }

  // Persist one complete USD valuation per local calendar day. Currency changes never rewrite history.
  void recordPortfolioSnapshot(double usd){
    String day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
    try{
      JSONObject history=new JSONObject(getPreferences(0).getString("portfolio_daily_usd","{}"));
      history.put(day,usd);
      getPreferences(0).edit().putString("portfolio_daily_usd",history.toString()).apply();
    }catch(Exception ignored){}
  }
  void renderPerformance(){
    if(performanceHolder==null)return;
    performanceHolder.removeAllViews();
    performanceHolder.addView(label("Portfolio Performance",21));
    LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
    for(final int days:new int[]{7,30,90,365,0}){
      Button b=new Button(this);b.setText(days==0?"ALL":days==7?"7D":days==30?"30D":days==90?"90D":"1Y");
      b.setAllCaps(false);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(2),0,dp(2),0);
      controls.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
      b.setOnClickListener(v->{performanceDays=days;renderPerformance();});
      b.setAlpha(performanceDays==days?1f:0.65f);
    }
    performanceHolder.addView(controls);
    ArrayList<Long> times=new ArrayList<>();ArrayList<Double> values=new ArrayList<>();
    try{
      JSONObject history=new JSONObject(getPreferences(0).getString("portfolio_daily_usd","{}"));
      ArrayList<String> dates=new ArrayList<>();Iterator<String> keys=history.keys();while(keys.hasNext())dates.add(keys.next());
      Collections.sort(dates);
      java.text.SimpleDateFormat parser=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);parser.setLenient(false);
      long cutoff=System.currentTimeMillis()-(long)performanceDays*86400000L;
      for(String day:dates){
        long timestamp=parser.parse(day).getTime();
        if(performanceDays!=0&&timestamp+86400000L<cutoff)continue;
        double usd=history.optDouble(day,Double.NaN);
        if(Double.isFinite(usd)&&usd>=0){times.add(timestamp);values.add(usd);}
      }
    }catch(Exception ignored){}
    if(values.isEmpty()){performanceHolder.addView(label("History starts after the first complete price refresh. Daily background snapshots run when Android permits.",13));return;}
    performanceHolder.addView(new PortfolioLineGraph(this,times,values),new LinearLayout.LayoutParams(-1,dp(230)));
    performanceHolder.addView(label("Daily snapshots • Includes deposits and withdrawals • Missing days are not estimated",12));
  }
  class PortfolioLineGraph extends View{
    final ArrayList<Long> dates;final ArrayList<Double> usd;
    final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    PortfolioLineGraph(Context c,ArrayList<Long> t,ArrayList<Double> v){super(c);dates=t;usd=v;setContentDescription("Portfolio value over time");}
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);if(usd.isEmpty())return;
      float left=dp(12),right=getWidth()-dp(82),top=dp(15),bottom=getHeight()-dp(34);
      if(right<=left||bottom<=top)return;
      double rate=displayCurrency.equals("BCH")?(currencyRate>0?1/currencyRate:0):currencyRate;
      if(rate<=0)return;
      double min=Collections.min(usd)*rate,max=Collections.max(usd)*rate;
      if(max<=min){double pad=Math.max(0.00000001,Math.abs(max)*0.05);min=Math.max(0,min-pad);max+=pad;}
      p.setColor(textColor());p.setTextSize(dp(10));p.setStyle(android.graphics.Paint.Style.FILL);
      String fmt=displayCurrency.equals("BCH")?"%.6f":"%.2f";
      canvas.drawText(String.format(Locale.US,fmt,max),right+dp(5),top+dp(5),p);
      canvas.drawText(String.format(Locale.US,fmt,min),right+dp(5),bottom,p);
      java.text.SimpleDateFormat df=new java.text.SimpleDateFormat("MMM d",Locale.US);
      canvas.drawText(df.format(new Date(dates.get(0))),left,bottom+dp(19),p);
      String last=df.format(new Date(dates.get(dates.size()-1)));
      canvas.drawText(last,Math.max(left,right-p.measureText(last)),bottom+dp(19),p);
      p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1));
      canvas.drawLine(left,bottom,right,bottom,p);
      android.graphics.Path line=new android.graphics.Path();
      long first=dates.get(0),lastTime=dates.get(dates.size()-1);
      for(int i=0;i<usd.size();i++){
        float x=first==lastTime?(left+right)/2:left+(right-left)*(float)(dates.get(i)-first)/(lastTime-first);
        float y=(float)(bottom-(usd.get(i)*rate-min)/(max-min)*(bottom-top));
        if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
      }
      p.setColor(usd.get(usd.size()-1)>=usd.get(0)?Color.rgb(10,175,115):Color.rgb(220,65,65));
      p.setStrokeWidth(dp(2.5f));canvas.drawPath(line,p);
      p.setStyle(android.graphics.Paint.Style.FILL);
      if(usd.size()==1)canvas.drawCircle((left+right)/2,(top+bottom)/2,dp(4),p);
    }
  }
  EditText input(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);return e;}
  void addAssetDialog(){String[] types={"BCH","PI","USDT","CashToken"};new AlertDialog.Builder(this).setTitle("Choose asset").setItems(types,(d,which)->assetForm(-1,types[which])).show();}
  void editDialog(int index){new AlertDialog.Builder(this).setTitle("Manage asset").setItems(new String[]{"Edit","Remove"},(d,w)->{if(w==0)assetForm(index,assets.optJSONObject(index).optString("type"));else new AlertDialog.Builder(this).setMessage("Remove this asset?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(dd,ww)->{assets.remove(index);save();render();}).show();}).show();}
  void assetForm(int index,String type){JSONObject old=index>=0?assets.optJSONObject(index):new JSONObject();LinearLayout box=new LinearLayout(this);box.setPadding(dp(18),0,dp(18),0);box.setOrientation(1);
    EditText name=input("Token name",old.optString("name",type.equals("CashToken")?"":type));EditText id=input(type.equals("CashToken")?"Input CashToken ID here":"Asset ID",old.optString("id",type.equals("CashToken")?"":type.toLowerCase(Locale.US)));if(type.equals("CashToken"))id.setHintTextColor(Color.GRAY);
    EditText qty=input("Quantity",old.optString("quantity",""));qty.setInputType(8194);box.addView(name);if(type.equals("CashToken"))box.addView(id);box.addView(qty);
    final String[] iconModes={"auto","default","custom"};
    final String[] iconLabels={"Automatic (built-in / BCMR icon)","Default app icon","Custom image URL"};
    final String[] selectedIconMode={old.optString("icon_mode","auto")};
    EditText iconUrl=input("Custom icon URL (https://…)",old.optString("icon_url",""));
    Spinner iconChoice=new Spinner(this);
    ArrayAdapter<String> iconAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,iconLabels);
    iconAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);iconChoice.setAdapter(iconAdapter);
    int selectedIndex=0;for(int m=0;m<iconModes.length;m++)if(iconModes[m].equals(selectedIconMode[0]))selectedIndex=m;
    iconChoice.setSelection(selectedIndex);
    TextView iconHint=label("Icon choice",14);
    {box.addView(iconHint);box.addView(iconChoice);if(selectedIndex==2)box.addView(iconUrl);
      iconChoice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
        public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long rowId){
          selectedIconMode[0]=iconModes[position];
          if(position==2){if(iconUrl.getParent()==null)box.addView(iconUrl);}else if(iconUrl.getParent()!=null)box.removeView(iconUrl);
        }
        public void onNothingSelected(android.widget.AdapterView<?> parent){}
      });
    }
    if(type.equals("CashToken")){
      final String[] lastLookup={""};
      id.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){
          String category=s.toString().trim().toLowerCase(Locale.US);
          if(category.matches("[a-f0-9]{64}")&&!category.equals(lastLookup[0])){
            lastLookup[0]=category;
            name.setText("Looking up token name…");
            new Thread(()->{
              String tokenName=null;
              try{tokenName=extractTokenName(fetchAny("https://bcmr.paytaca.com/api/tokens/"+category));}catch(Exception ignored){}
              final String resolved=tokenName;
              runOnUiThread(()->{
                if(!category.equals(id.getText().toString().trim().toLowerCase(Locale.US)))return;
                if(resolved!=null&&!resolved.trim().isEmpty())name.setText(resolved.trim());
                else if("Looking up token name…".contentEquals(name.getText()))name.setText("");
              });
            }).start();
          }else if(!category.matches("[a-f0-9]{64}")){lastLookup[0]="";}
        }
        public void afterTextChanged(Editable s){}
      });
    }
    new AlertDialog.Builder(this).setTitle((index>=0?"Edit ":"Add ")+type).setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{String q=qty.getText().toString().trim();BigDecimal quantity=new BigDecimal(q);if(quantity.signum()<0)throw new Exception("Quantity cannot be negative");String identifier=type.equals("CashToken")?id.getText().toString().trim().toLowerCase(Locale.US):type.toLowerCase(Locale.US);if(type.equals("CashToken")&&!identifier.matches("[a-f0-9]{64}"))throw new Exception("CashToken category ID must be 64 hexadecimal characters");if(name.getText().toString().trim().isEmpty()||"Looking up token name…".contentEquals(name.getText()))throw new Exception("Enter a token name or wait for the token lookup to finish");JSONObject obj=new JSONObject();obj.put("type",type);obj.put("name",name.getText().toString().trim());obj.put("id",identifier);obj.put("quantity",quantity.toPlainString());
      {
        String mode=selectedIconMode[0];String customUrl=iconUrl.getText().toString().trim();
        if("custom".equals(mode)&&!(customUrl.startsWith("https://")||customUrl.startsWith("http://")))throw new Exception("Enter a valid custom icon URL beginning with https:// or http://");
        obj.put("icon_mode",mode);obj.put("icon_url","custom".equals(mode)?customUrl:"");
      }
      if(index<0)assets.put(obj);else assets.put(index,obj);save();render();refresh();}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}).show();
  }

  String extractTokenName(Object value){
    if(value instanceof JSONObject){
      JSONObject o=(JSONObject)value;
      for(String field:new String[]{"name","token_name","tokenName","display_name","displayName"}){
        String candidate=o.optString(field,"").trim();
        if(!candidate.isEmpty()&&!"null".equalsIgnoreCase(candidate))return candidate;
      }
      for(String field:new String[]{"identity","snapshot","token","metadata","data","result","registry","identities"}){
        String candidate=extractTokenName(o.opt(field));
        if(candidate!=null)return candidate;
      }
      Iterator<String> keys=o.keys();
      while(keys.hasNext()){String candidate=extractTokenName(o.opt(keys.next()));if(candidate!=null)return candidate;}
    }else if(value instanceof JSONArray){
      JSONArray a=(JSONArray)value;
      for(int i=0;i<a.length();i++){String candidate=extractTokenName(a.opt(i));if(candidate!=null)return candidate;}
    }
    return null;
  }
  void refresh(){status.setText("Fetching prices…");new Thread(()->{
    StringBuilder notes=new StringBuilder();
    try{JSONObject j=fetch("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin-cash%2Cpi-network%2Ctether&vs_currencies=usd%2Cmxn%2Cars%2Cvnd%2Cphp%2Cjpy%2Ccny");
      putPrice("BCH:bch",j.optJSONObject("bitcoin-cash"));putPrice("PI:pi",j.optJSONObject("pi-network"));putPrice("USDT:usdt",j.optJSONObject("tether"));
      JSONObject bchRates=j.optJSONObject("bitcoin-cash");
      if(bchRates!=null){double bchUsd=bchRates.optDouble("usd",0);if(displayCurrency.equals("BCH"))currencyRate=bchUsd;else{String rateKey=displayCurrency.toLowerCase(Locale.US);double local=bchRates.optDouble(rateKey,0);if(bchUsd>0&&local>0)currencyRate=local/bchUsd;else if(displayCurrency.equals("USD"))currencyRate=1.0;}}
      notes.append("CoinGecko OK. ");
    }catch(Exception e){notes.append("CoinGecko unavailable. ");}
    int found=0,missing=0;
    String firstCashTokenFailure=null;
    // Main-page quotes use CauldronSwap's current-price endpoint. The API
    // price is per smallest token unit, so multiply by 10^token decimals to
    // obtain satoshis per whole token before converting to USD.
    for(int i=0;i<assets.length();i++){
      JSONObject asset=assets.optJSONObject(i);
      if(asset==null||!"CashToken".equals(asset.optString("type")))continue;
      String category=asset.optString("id").toLowerCase(Locale.US);
      String stage="current-price request";
      try{
        String url="https://indexer.riften.net/cauldron/price/"+category+"/current";
        Object response=fetchAny(url);
        Double priceUnits=extractCurrentCauldronPrice(response);
        if(priceUnits==null)throw new IOException("response has no recognized positive price field: "+summarizeJson(response));
        stage="BCMR metadata request";
        Object metadata=fetchAny("https://indexer.riften.net/bcmr/token/"+category);
        int decimals=extractCauldronTokenDecimals(metadata);
        if(decimals<0)throw new IOException("BCMR response has no decimals field: "+summarizeJson(metadata));
        if(decimals>18)throw new IOException("unsupported token decimals: "+decimals);
        stage="BCH/USD conversion";
        Double bch=prices.get("BCH:bch");
        if(bch==null||!Double.isFinite(bch)||bch<=0)throw new IOException("BCH/USD price unavailable");
        double sats=priceUnits*Math.pow(10d,decimals);
        double usd=(sats/100000000d)*bch;
        if(Double.isFinite(usd)&&usd>0){prices.put(key(asset),usd);found++;}
        else throw new IOException("calculated USD price is not positive/finite; API price="+priceUnits+", decimals="+decimals+", BCH/USD="+bch);
      }catch(Exception e){
        missing++;
        String detail=stage+": "+diagnosticMessage(e);
        if(firstCashTokenFailure==null)firstCashTokenFailure=category.substring(0,Math.min(8,category.length()))+"… "+detail;
        android.util.Log.w("CashTrackPrice","CashToken "+category+" failed at "+detail,e);
      }
    }
    notes.append("CashTokens priced from CauldronSwap: ").append(found).append("; unavailable: ").append(missing);
    if(firstCashTokenFailure!=null)notes.append(". First failure: ").append(firstCashTokenFailure);
    final String message=notes.toString();runOnUiThread(()->{status.setText(message);if(copyErrorButton!=null)copyErrorButton.setVisibility((message.contains("unavailable: 0")&&message.startsWith("CoinGecko OK."))?View.GONE:View.VISIBLE);render();});
  }).start();}

  // Resolve the category's BCMR metadata and use its published icon when available.
  // Paytaca's public BCMR indexer resolves on-chain metadata by CashToken category.
  JSONObject findIconObject(Object value){
    if(value instanceof JSONObject){JSONObject o=(JSONObject)value;
      Object uris=o.opt("uris");if(uris instanceof JSONObject){JSONObject u=(JSONObject)uris;String icon=u.optString("icon","");if(!icon.isEmpty()&&!"null".equals(icon))return u;}
      String direct=o.optString("icon","");if(!direct.isEmpty()&&direct.startsWith("http"))return o;
      for(String field:new String[]{"token","identity","snapshot","metadata","data","result","registry","identities","uris"}){JSONObject found=findIconObject(o.opt(field));if(found!=null)return found;}
      JSONArray names=o.optJSONArray("identities");if(names!=null){for(int i=0;i<names.length();i++){JSONObject found=findIconObject(names.opt(i));if(found!=null)return found;}}
    }else if(value instanceof JSONArray){JSONArray arr=(JSONArray)value;for(int i=0;i<arr.length();i++){JSONObject found=findIconObject(arr.opt(i));if(found!=null)return found;}}
    return null;
  }
  String extractIconUri(Object value){
    // BCMR indexer responses can nest snapshots under registry-specific keys.
    // Walk every object field instead of assuming a fixed response shape.
    if(value instanceof JSONObject){
      JSONObject o=(JSONObject)value;
      JSONObject u=o.optJSONObject("uris");
      if(u!=null){
        String s=u.optString("icon","");
        if(!s.isEmpty()&&!"null".equals(s))return s.trim();
      }
      String direct=o.optString("icon","");
      if(!direct.isEmpty()&&!"null".equals(direct))return direct.trim();
      Iterator<String> keys=o.keys();
      while(keys.hasNext()){
        String field=keys.next();
        if("icon".equalsIgnoreCase(field))continue;
        String found=extractIconUri(o.opt(field));
        if(found!=null)return found;
      }
    }else if(value instanceof JSONArray){
      JSONArray a=(JSONArray)value;
      for(int i=0;i<a.length();i++){
        String found=extractIconUri(a.opt(i));
        if(found!=null)return found;
      }
    }
    return null;
  }
  android.graphics.Bitmap downloadIconBitmap(String imageUrl)throws Exception{
    if(imageUrl.startsWith("ipfs://")){String path=imageUrl.substring(7);if(path.startsWith("ipfs/"))path=path.substring(5);imageUrl="https://ipfs.io/ipfs/"+path;}
    if(!(imageUrl.startsWith("https://")||imageUrl.startsWith("http://")))return null;
    HttpURLConnection conn=(HttpURLConnection)new URL(imageUrl).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);conn.setInstanceFollowRedirects(true);conn.setRequestProperty("User-Agent","CashTokensPortfolio/1.2");
    try(InputStream in=conn.getInputStream()){
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
      while((n=in.read(buf))!=-1&&bytes.size()<2097152)bytes.write(buf,0,n);
      byte[] imageBytes=bytes.toByteArray();android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
      if(bitmap==null){String svgText=new String(imageBytes,"UTF-8").replace("\\uFEFF","").trim();if(svgText.toLowerCase(Locale.US).contains("<svg")){SVG svg=SVG.getFromString(svgText);Bitmap rendered=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(rendered);svg.renderToCanvas(canvas,new android.graphics.RectF(0,0,96,96));bitmap=rendered;}}
      return bitmap;
    }finally{conn.disconnect();}
  }
  void loadCustomAssetIcon(ImageView target,String customIconUrl){
    if(customIconUrl==null||!(customIconUrl.startsWith("https://")||customIconUrl.startsWith("http://")))return;
    String cacheKey="custom:"+customIconUrl;
    android.graphics.Bitmap cached=cashTokenIconCache.get(cacheKey);if(cached!=null){target.setImageBitmap(cached);return;}
    synchronized(iconRequests){if(iconRequests.contains(cacheKey))return;iconRequests.add(cacheKey);}
    new Thread(()->{android.graphics.Bitmap bitmap=null;try{bitmap=downloadIconBitmap(customIconUrl);}catch(Exception ignored){}finally{synchronized(iconRequests){iconRequests.remove(cacheKey);}}
      if(bitmap!=null){cashTokenIconCache.put(cacheKey,bitmap);runOnUiThread(()->{if(!isFinishing())render();});}
    }).start();
  }
  void loadCashTokenIcon(ImageView target,String category,String iconMode,String customIconUrl){
    if("default".equals(iconMode)){target.setImageResource(R.drawable.ic_asset_cashtoken);return;}
    if("custom".equals(iconMode)){
      if(customIconUrl==null||!(customIconUrl.startsWith("https://")||customIconUrl.startsWith("http://")))return;
      String cacheKey="custom:"+customIconUrl;
      android.graphics.Bitmap customCached=cashTokenIconCache.get(cacheKey);if(customCached!=null){target.setImageBitmap(customCached);return;}
      synchronized(iconRequests){if(iconRequests.contains(cacheKey))return;iconRequests.add(cacheKey);}
      new Thread(()->{android.graphics.Bitmap bitmap=null;try{bitmap=downloadIconBitmap(customIconUrl);}catch(Exception ignored){}finally{synchronized(iconRequests){iconRequests.remove(cacheKey);}}
        if(bitmap!=null){cashTokenIconCache.put(cacheKey,bitmap);runOnUiThread(()->{if(!isFinishing())render();});}
      }).start();return;
    }

    if(category==null||!category.matches("(?i)[a-f0-9]{64}"))return;
    android.graphics.Bitmap cached=cashTokenIconCache.get(category);if(cached!=null){target.setImageBitmap(cached);return;}
    synchronized(iconRequests){if(iconRequests.contains(category))return;iconRequests.add(category);}
    new Thread(()->{android.graphics.Bitmap bitmap=null;try{
      String uri;
      if("b38a33f750f84c5c169a6f23cb873e6e79605021585d4f3408789689ed87f366".equalsIgnoreCase(category)){
        // Use the Moria icon URL provided for this CashToken.
        uri="https://meta.moria.money/m1.png";
      }else if("2469acc5afa4b10cb5b5c04afb89c3a3ffd61c5da9c01e26d00951cae2a02544".equalsIgnoreCase(category)){
        // Use the icon URL provided for ParyonUSD.
        uri="https://meta.riften.net/icon/2469acc5afa4b10cb5b5c04afb89c3a3ffd61c5da9c01e26d00951cae2a02544";
      }else if("666879bad15bbfb73ebea2b238f2d07ac3fac5dbec96182a5a1dd213211de012".equalsIgnoreCase(category)){
        // Use the exact SHIBCH icon URL published by TokenExplorer.cash.
        uri="https://ipfs.filebase.io/ipfs/bafybeibezwnacrpoq4beqnecpsi2znronhqjidu4aijuy4tcqbv2u2frla/666879bad15bbfb73ebea2b238f2d07ac3fac5dbec96182a5a1dd213211de012.png";
      }else if("d9ab24ed15a7846cc3d9e004aa5cb976860f13dac1ead05784ee4f4622af96ea".equalsIgnoreCase(category)){
        // Use the Furu Tokens icon URL provided by the user.
        uri="https://ipfs.filebase.io/ipfs/bafkreicqqq3tkq5tkbtg3vhbhw6urybothpoedsszuejs5baq2k25mhgvu";
      }else{
        Object meta=fetchAny("https://bcmr.paytaca.com/api/tokens/"+category.toLowerCase(Locale.US));
        uri=extractIconUri(meta);
      }
      if(uri!=null){String imageUrl=uri;
        if(imageUrl.startsWith("ipfs://")){String path=imageUrl.substring(7);if(path.startsWith("ipfs/"))path=path.substring(5);imageUrl="https://ipfs.io/ipfs/"+path;}
        if(imageUrl.startsWith("https://")||imageUrl.startsWith("http://")){
          HttpURLConnection conn=(HttpURLConnection)new URL(imageUrl).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);conn.setInstanceFollowRedirects(true);conn.setRequestProperty("User-Agent","CashTokensPortfolio/1.2");
          try(InputStream in=conn.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1&&bytes.size()<2097152)bytes.write(buf,0,n);byte[] imageBytes=bytes.toByteArray();bitmap=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);if(bitmap==null){String svgText=new String(imageBytes,"UTF-8").replace("\uFEFF","").trim();if(svgText.toLowerCase(Locale.US).contains("<svg")){SVG svg=SVG.getFromString(svgText);Bitmap rendered=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(rendered);svg.renderToCanvas(canvas,new android.graphics.RectF(0,0,96,96));bitmap=rendered;}}}finally{conn.disconnect();}
        }
      }
    }catch(Exception ignored){}finally{synchronized(iconRequests){iconRequests.remove(category);}}
      if(bitmap!=null){cashTokenIconCache.put(category,bitmap);runOnUiThread(()->{if(!isFinishing())render();});}
    }).start();
    target.setTag(category);
  }

  // Read decimals only from CauldronSwap's own BCMR endpoint. A missing
  // decimals field is treated as unavailable instead of silently guessing.
  int fetchCauldronTokenDecimals(String category)throws Exception{
    Object metadata=fetchAny("https://indexer.riften.net/bcmr/token/"+category);
    return extractCauldronTokenDecimals(metadata);
  }
  int extractCauldronTokenDecimals(Object data){
    if(data instanceof JSONObject){
      JSONObject o=(JSONObject)data;
      JSONObject token=o.optJSONObject("token");
      if(token!=null&&token.has("decimals")&&!token.isNull("decimals")){
        int decimals=token.optInt("decimals",-1);
        if(decimals>=0)return decimals;
      }
      if(o.has("decimals")&&!o.isNull("decimals")){
        int decimals=o.optInt("decimals",-1);
        if(decimals>=0)return decimals;
      }
      for(String field:new String[]{"data","result","metadata","identity","snapshot","registry","token"}){
        int decimals=extractCauldronTokenDecimals(o.opt(field));
        if(decimals>=0)return decimals;
      }
    }else if(data instanceof JSONArray){
      JSONArray a=(JSONArray)data;
      for(int i=0;i<a.length();i++){
        int decimals=extractCauldronTokenDecimals(a.opt(i));
        if(decimals>=0)return decimals;
      }
    }
    return -1;
  }

  Double extractCurrentCauldronPrice(Object data){
    if(data instanceof JSONObject){
      JSONObject o=(JSONObject)data;
      // Prefer explicit current-price fields. The generic "price" field is
      // accepted only from this current-price endpoint, never from history.
      for(String field:new String[]{"currentPrice","current_price","price","priceSats","price_sats","avg"}){
        if(!o.isNull(field)){
          Object raw=o.opt(field);
          double n=Double.NaN;
          if(raw instanceof Number)n=((Number)raw).doubleValue();
          else if(raw instanceof String)try{n=Double.parseDouble((String)raw);}catch(Exception ignored){}
          if(Double.isFinite(n)&&n>0)return n;
        }
      }
      for(String field:new String[]{"data","result","current","token","priceInfo","price_info"}){
        Double n=extractCurrentCauldronPrice(o.opt(field));
        if(n!=null)return n;
      }
    }else if(data instanceof JSONArray){
      JSONArray a=(JSONArray)data;
      for(int i=0;i<a.length();i++){
        Double n=extractCurrentCauldronPrice(a.opt(i));
        if(n!=null)return n;
      }
    }
    return null;
  }
  Double extractLatestCauldronHistoryPrice(Object data,long minimumTimestamp){
    if(!(data instanceof JSONObject))return null;
    JSONArray points=((JSONObject)data).optJSONArray("history");
    if(points==null)return null;
    Double latestPrice=null;long latestTs=-1;
    for(int i=0;i<points.length();i++){
      JSONObject point=points.optJSONObject(i);
      if(point==null||point.isNull("avg"))continue;
      double price=point.optDouble("avg",Double.NaN);
      long ts=point.optLong("time",-1);
      if(ts>=minimumTimestamp&&ts<=System.currentTimeMillis()/1000L&&Double.isFinite(price)&&price>0&&ts>=latestTs){
        latestPrice=price;latestTs=ts;
      }
    }
    return latestPrice;
  }
  Double extractLatestHistorySats(Object data){
    if(!(data instanceof JSONObject))return null;
    JSONArray points=((JSONObject)data).optJSONArray("points");
    if(points==null)return null;
    Double latestPrice=null; long latestTs=-1;
    for(int i=0;i<points.length();i++){
      JSONObject point=points.optJSONObject(i);
      if(point==null||point.isNull("priceSats"))continue;
      double price=point.optDouble("priceSats",Double.NaN);
      long ts=point.optLong("ts",-1);
      if(Double.isFinite(price)&&price>=0&&ts>=latestTs){latestPrice=price;latestTs=ts;}
    }
    return latestPrice;
  }
  JSONObject findToken(Object data,String id){
    if(data instanceof JSONArray){JSONArray arr=(JSONArray)data;for(int i=0;i<arr.length();i++){JSONObject t=findToken(arr.opt(i),id);if(t!=null)return t;}}
    if(data instanceof JSONObject){JSONObject o=(JSONObject)data;
      if(id.equalsIgnoreCase(o.optString("category"))||id.equalsIgnoreCase(o.optString("token_id"))||id.equalsIgnoreCase(o.optString("category_id")))return o;
      for(String field:new String[]{"tokens","data","results","items"}){JSONObject t=findToken(o.opt(field),id);if(t!=null)return t;}
    }return null;
  }
  Double number(JSONObject obj,String name){if(obj==null||obj.isNull(name))return null;try{double n=Double.parseDouble(obj.getString(name));return Double.isFinite(n)&&n>=0?n:null;}catch(Exception e){return null;}}
  Double extractUsd(JSONObject t){
    for(String field:new String[]{"price_usd","usd_price","priceUsd"}){Double n=number(t,field);if(n!=null)return n;}
    JSONObject market=t.optJSONObject("market");if(market!=null){Double n=extractUsd(market);if(n!=null)return n;}
    // A generic 'price' field has ambiguous units; intentionally not used.
    return null;
  }
  Double extractSats(JSONObject t){
    for(String field:new String[]{"price_sats","priceSats","price_satoshis"}){Double n=number(t,field);if(n!=null)return n;}
    JSONObject market=t.optJSONObject("market");if(market!=null)return extractSats(market);
    return null;
  }
  String readHttpBody(HttpURLConnection c,int code)throws IOException{
    InputStream stream=code>=400?c.getErrorStream():c.getInputStream();
    if(stream==null)return "";
    ByteArrayOutputStream b=new ByteArrayOutputStream();
    try(InputStream in=stream){byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);}
    return b.toString("UTF-8");
  }
  String briefException(Throwable e){
    if(e==null)return "unknown";
    StringBuilder out=new StringBuilder(e.getClass().getSimpleName());
    String message=e.getMessage();
    if(message!=null&&!message.trim().isEmpty())out.append(": ").append(message.trim());
    String text=out.toString();
    return text.length()>320?text.substring(0,320)+"…":text;
  }
  String diagnosticMessage(Throwable e){
    if(e==null)return "unknown error";
    String message=e.getMessage();
    // Keep both connection attempts intact; don't let repetitive suppressed TLS errors
    // hide the IPv4-only retry result from the Copy Error Message output.
    if(message!=null&&message.startsWith("Network attempts failed;")){
      return message.length()>1000?message.substring(0,1000)+"…":message;
    }
    StringBuilder out=new StringBuilder(e.getClass().getSimpleName());
    if(message!=null&&!message.trim().isEmpty())out.append(": ").append(message.trim());
    Throwable cause=e.getCause();
    if(cause!=null&&cause!=e)out.append(" | caused by ").append(briefException(cause));
    String text=out.toString();
    return text.length()>700?text.substring(0,700)+"…":text;
  }
  String summarizeJson(Object value){
    if(value==null||value==JSONObject.NULL)return "null";
    String summary=String.valueOf(value);
    return summary.length()>180?summary.substring(0,180)+"…":summary;
  }
  // The indexer can resolve to both IPv4 and IPv6, but some networks have
  // broken IPv6 routes. Try IPv4-only first; if unavailable or unsuccessful,
  // fall back to the normal dual-stack client (including IPv6-only networks).
  private static final okhttp3.OkHttpClient NETWORK_CLIENT =
      new okhttp3.OkHttpClient.Builder()
          .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
          .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
          .followRedirects(true)
          .build();

  private static final okhttp3.OkHttpClient IPV4_CLIENT =
      new okhttp3.OkHttpClient.Builder()
          .dns(hostname -> {
            List<InetAddress> addresses = new ArrayList<>();
            for (InetAddress address : okhttp3.Dns.SYSTEM.lookup(hostname)) {
              if (address instanceof java.net.Inet4Address) addresses.add(address);
            }
            if (addresses.isEmpty()) throw new UnknownHostException("No IPv4 address available for " + hostname);
            return addresses;
          })
          .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
          .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
          .followRedirects(true)
          .build();

  Object fetchAny(String url)throws Exception{
    okhttp3.Request request=new okhttp3.Request.Builder().url(url)
        .header("Accept","application/json")
        .header("User-Agent","CashTrack/1.0.8")
        .build();
    okhttp3.Response response;
    String connectionPath="IPv4-first";
    try {
      response = IPV4_CLIENT.newCall(request).execute();
    } catch (IOException ipv4Failure) {
      connectionPath="dual-stack fallback";
      try {
        response = NETWORK_CLIENT.newCall(request).execute();
      } catch (IOException dualStackFailure) {
        throw new IOException("Network attempts failed; IPv4-first: "+briefException(ipv4Failure)
            +"; dual-stack fallback: "+briefException(dualStackFailure), dualStackFailure);
      }
    }
    try(okhttp3.Response closeableResponse=response){
      String body=closeableResponse.body()!=null?closeableResponse.body().string():"";
      String contentType=closeableResponse.header("Content-Type","not provided");
      String finalUrl=closeableResponse.request().url().toString();
      if(!closeableResponse.isSuccessful())throw new IOException("HTTP "+closeableResponse.code()
          +" via "+connectionPath+"; URL="+finalUrl+"; Content-Type="+contentType
          +(body.isEmpty()?"":"; body: "+body.substring(0,Math.min(220,body.length()))));
      try{return new JSONTokener(body).nextValue();}
      catch(JSONException e){throw new IOException("Invalid JSON via "+connectionPath+"; HTTP "+closeableResponse.code()
          +"; URL="+finalUrl+"; Content-Type="+contentType+"; body: "
          +body.substring(0,Math.min(220,body.length())),e);}
    }
  }
  void putPrice(String key,JSONObject data){if(data!=null&&data.has("usd"))prices.put(key,data.optDouble("usd"));}
  JSONObject fetch(String url)throws Exception{
    Object value=fetchAny(url);
    if(!(value instanceof JSONObject))throw new IOException("Expected JSON object");
    return (JSONObject)value;
  }
}
