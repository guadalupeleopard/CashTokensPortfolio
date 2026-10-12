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

public class MainActivity extends Activity {
  LinearLayout root, items; TextView total, status; Button themeButton; JSONArray assets = new JSONArray();
  int themeMode=0; // 0=Day, 1=Dark, 2=Bitcoin Cash
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
  void openMenu(){String current=themeMode==0?"Day":themeMode==1?"Dark":"Bitcoin Cash Green";new AlertDialog.Builder(this).setTitle("Menu").setItems(new String[]{"Background: "+current,"Currency: "+displayCurrency},(dialog,which)->{if(which==0)chooseTheme();else if(which==1)chooseCurrency();}).show();}
  void chooseCurrency(){new AlertDialog.Builder(this).setTitle("Display currency").setSingleChoiceItems(currencyNames,Math.max(0,Arrays.asList(currencyCodes).indexOf(displayCurrency)),(dialog,which)->{displayCurrency=currencyCodes[which];getPreferences(0).edit().putString("display_currency",displayCurrency).apply();dialog.dismiss();render();refresh();}).setNegativeButton("Cancel",null).show();}
  String currencySymbol(){switch(displayCurrency){case "BCH":return "BCH ";case "MXN":return "MX$";case "ARS":return "AR$";case "VND":return "₫";case "PHP":return "₱";case "JPY":return "¥";case "CNY":return "元";default:return "$";}}
  String formatValue(double usdValue){double value=displayCurrency.equals("BCH")?(currencyRate>0?usdValue/currencyRate:0):usdValue*currencyRate;String formatted=String.format(Locale.US,"%,.2f",value);if(displayCurrency.equals("BCH"))formatted=String.format(Locale.US,"%,.8f",value);return currencySymbol()+formatted;}
  void chooseTheme(){new AlertDialog.Builder(this).setTitle("Choose background").setItems(new String[]{"☀  Day","☾  Dark","₿  Bitcoin Cash Green"},(dialog,which)->{themeMode=which;getPreferences(0).edit().putInt("theme_mode",themeMode).apply();applyTheme();}).show();}
  void applyTheme(){updateNavigationBarAppearance();if(root==null)return;root.setBackgroundColor(pageColor());if(themeButton!=null){themeButton.setText("☰");themeButton.setContentDescription("Open menu");}applyThemeTo(root);}
  void applyThemeTo(View view){if(view instanceof TextView){((TextView)view).setTextColor(textColor());}if(view instanceof Button){view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor()));}if(view instanceof LinearLayout){LinearLayout ll=(LinearLayout)view;if(ll==root)ll.setBackgroundColor(pageColor());else if(ll==items){}else if(ll.getOrientation()==LinearLayout.VERTICAL&&ll.getChildCount()>0)ll.setBackgroundColor(cardColor());}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyThemeTo(group.getChildAt(i));}}
  @Override public void onCreate(Bundle b){super.onCreate(b);themeMode=getPreferences(0).getInt("theme_mode",0);displayCurrency=getPreferences(0).getString("display_currency","USD");if(!Arrays.asList(currencyCodes).contains(displayCurrency))displayCurrency="USD";configureNavigationBar();try{assets=new JSONArray(getPreferences(0).getString(PREF,"[]"));}catch(Exception ignored){} build(); refresh();}
  void save(){getPreferences(0).edit().putString(PREF,assets.toString()).apply();}
  void build(){ScrollView scroll=new ScrollView(this);
    scroll.setOnApplyWindowInsetsListener((view,insets)->{
      int bottom;
      if(Build.VERSION.SDK_INT>=30)bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
      else bottom=insets.getSystemWindowInsetBottom();
      view.setPadding(0,0,0,bottom);
      return insets;
    });
    root=new LinearLayout(this);root.setPadding(dp(18),dp(20),dp(18),dp(20));root.setOrientation(1);root.setBackgroundColor(Color.rgb(244,249,247));scroll.addView(root);setContentView(scroll);
    LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);ImageView appLogo=new ImageView(this);appLogo.setImageResource(R.drawable.cashtrack_icon);appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams appLogoLp=new LinearLayout.LayoutParams(dp(48),dp(48));appLogoLp.rightMargin=dp(12);header.addView(appLogo,appLogoLp);TextView heading=label("CashTokens Portfolio",26);LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(0,-2,1f);header.addView(heading,headingLp);themeButton=new Button(this);themeButton.setText("☰");themeButton.setTextSize(22);themeButton.setMinWidth(0);themeButton.setMinimumWidth(0);themeButton.setPadding(dp(10),dp(4),dp(10),dp(4));themeButton.setContentDescription("Open menu");header.addView(themeButton);themeButton.setOnClickListener(v->openMenu());root.addView(header);root.addView(label("Watch-only • Stored on this device",13));total=label("Total: —",23);root.addView(total);status=label("Prices not loaded",12);root.addView(status);
    addButton(root,"+ Add asset",this::addAssetDialog);addButton(root,"Refresh market prices",this::refresh);items=new LinearLayout(this);items.setOrientation(1);root.addView(items);render();}
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
    applyTheme();
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
      if(bchRates!=null){double bchUsd=bchRates.optDouble("usd",0);if(displayCurrency.equals("BCH"))currencyRate=bchUsd;else{String rateKey=displayCurrency.toLowerCase(Locale.US);double local=bchRates.optDouble(rateKey,0);if(bchUsd>0&&local>0)currencyRate=local/bchUsd;}}
      notes.append("CoinGecko OK. ");
    }catch(Exception e){notes.append("CoinGecko unavailable. ");}
    int found=0,missing=0;
    // TokenStork's directory endpoint does not expose live venue prices.
    // Its per-category history endpoint returns priceSats points for Cauldron/Fex.
    // Match the exact category ID in the URL; convert BCH satoshis to USD using BCH/USD.
    for(int i=0;i<assets.length();i++){
      JSONObject asset=assets.optJSONObject(i);
      if(asset==null||!"CashToken".equals(asset.optString("type")))continue;
      String category=asset.optString("id").toLowerCase(Locale.US);
      try{
        String url="https://tokenstork.com/api/tokens/"+category+"/history";
        Object response=fetchAny(url);
        Double sats=extractLatestHistorySats(response);
        Double bch=prices.get("BCH:bch");
        if(sats!=null&&bch!=null){
          double usd=sats/100000000d*bch;
          if(Double.isFinite(usd)&&usd>=0){prices.put(key(asset),usd);found++;}else missing++;
        }else missing++;
      }catch(Exception e){missing++;}
    }
    notes.append("CashTokens priced: ").append(found).append("; unavailable: ").append(missing);
    final String message=notes.toString();runOnUiThread(()->{status.setText(message);render();});
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
  Object fetchAny(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","CashTokensPortfolio/1.1");try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);}return new JSONTokener(b.toString("UTF-8")).nextValue();}finally{c.disconnect();}}
  void putPrice(String key,JSONObject data){if(data!=null&&data.has("usd"))prices.put(key,data.optDouble("usd"));}
  JSONObject fetch(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","CashTokensPortfolio/1.0");try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);}return new JSONObject(b.toString("UTF-8"));}finally{c.disconnect();}}
}
