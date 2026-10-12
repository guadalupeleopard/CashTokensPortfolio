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
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.math.*;
import java.util.*;

public class MainActivity extends Activity {
  LinearLayout root, items; TextView total, status; JSONArray assets = new JSONArray();
  final HashMap<String, Double> prices = new HashMap<>();
  final HashMap<String, String> ids = new HashMap<>();
  final HashMap<String, android.graphics.Bitmap> cashTokenIconCache = new HashMap<>();
  final HashSet<String> iconRequests = new HashSet<>();
  final String PREF="portfolio";
  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(20,40,43));t.setPadding(dp(4),dp(10),dp(4),dp(10));return t;}
  void addButton(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);parent.addView(b);b.setOnClickListener(v->action.run());}
  @Override public void onCreate(Bundle b){super.onCreate(b);try{assets=new JSONArray(getPreferences(0).getString(PREF,"[]"));}catch(Exception ignored){} build(); refresh();}
  void save(){getPreferences(0).edit().putString(PREF,assets.toString()).apply();}
  void build(){ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setPadding(dp(18),dp(20),dp(18),dp(20));root.setOrientation(1);root.setBackgroundColor(Color.rgb(244,249,247));scroll.addView(root);setContentView(scroll);
    root.addView(label("CashTokens Portfolio",26));root.addView(label("Watch-only • Stored on this device",13));total=label("Total: —",23);root.addView(total);status=label("Prices not loaded",12);root.addView(status);
    addButton(root,"+ Add asset",this::addAssetDialog);addButton(root,"Refresh market prices",this::refresh);items=new LinearLayout(this);items.setOrientation(1);root.addView(items);render();}
  String key(JSONObject a){return a.optString("type")+":"+a.optString("id");}
  void render(){if(items==null)return;items.removeAllViews();double sum=0;boolean incomplete=false;
    for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a==null)continue;final int index=i;String k=key(a);Double price=prices.get(k);double qty=a.optDouble("quantity",0);String title=a.optString("name","Token");
      String val=price==null?"Price unavailable":String.format(Locale.US,"$%,.4f",qty*price);if(price==null)incomplete=true;else sum+=qty*price;
      LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);items.addView(row,lp);
      LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(0);titleRow.setGravity(Gravity.CENTER_VERTICAL);
      ImageView icon=new ImageView(this);int iconId;
      String assetType=a.optString("type");
      if("BCH".equalsIgnoreCase(assetType))iconId=R.drawable.ic_asset_bch;
      else if("PI".equalsIgnoreCase(assetType))iconId=R.drawable.ic_asset_pi;
      else if("USDT".equalsIgnoreCase(assetType))iconId=R.drawable.ic_asset_usdt;
      else iconId=R.drawable.ic_asset_cashtoken;
      LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(22),dp(22));iconLp.rightMargin=dp(7);
      icon.setImageResource(iconId);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);titleRow.addView(icon,iconLp);if("CashToken".equalsIgnoreCase(assetType))loadCashTokenIcon(icon,a.optString("id"));
      TextView assetTitle=label(title+" ("+assetType+")",18);assetTitle.setPadding(dp(2),dp(8),dp(4),dp(8));titleRow.addView(assetTitle);
      row.addView(titleRow);row.addView(label("Quantity: "+a.optString("quantity")+"   •   "+val,15));
      addButton(row,"Edit / Remove",()->editDialog(index));
    }
    total.setText(String.format(Locale.US,"Known value: $%,.2f USD",sum));if(incomplete)total.append("\n(Excludes unpriced assets)");
  }
  EditText input(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);return e;}
  void addAssetDialog(){String[] types={"BCH","PI","USDT","CashToken"};new AlertDialog.Builder(this).setTitle("Choose asset").setItems(types,(d,which)->assetForm(-1,types[which])).show();}
  void editDialog(int index){new AlertDialog.Builder(this).setTitle("Manage asset").setItems(new String[]{"Edit","Remove"},(d,w)->{if(w==0)assetForm(index,assets.optJSONObject(index).optString("type"));else new AlertDialog.Builder(this).setMessage("Remove this asset?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(dd,ww)->{assets.remove(index);save();render();}).show();}).show();}
  void assetForm(int index,String type){JSONObject old=index>=0?assets.optJSONObject(index):new JSONObject();LinearLayout box=new LinearLayout(this);box.setPadding(dp(18),0,dp(18),0);box.setOrientation(1);
    EditText name=input("Token name",old.optString("name",type.equals("CashToken")?"":type));EditText id=input(type.equals("CashToken")?"CashToken category ID (64 hex chars)":"Asset ID",old.optString("id",type.toLowerCase(Locale.US)));
    EditText qty=input("Quantity",old.optString("quantity",""));qty.setInputType(8194);box.addView(name);if(type.equals("CashToken"))box.addView(id);box.addView(qty);
    new AlertDialog.Builder(this).setTitle("Add "+type).setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{String q=qty.getText().toString().trim();BigDecimal quantity=new BigDecimal(q);if(quantity.signum()<0)throw new Exception("Quantity cannot be negative");String identifier=type.equals("CashToken")?id.getText().toString().trim().toLowerCase(Locale.US):type.toLowerCase(Locale.US);if(type.equals("CashToken")&&!identifier.matches("[a-f0-9]{64}"))throw new Exception("CashToken category ID must be 64 hexadecimal characters");if(name.getText().toString().trim().isEmpty())throw new Exception("Enter a token name");JSONObject obj=new JSONObject();obj.put("type",type);obj.put("name",name.getText().toString().trim());obj.put("id",identifier);obj.put("quantity",quantity.toPlainString());if(index<0)assets.put(obj);else assets.put(index,obj);save();render();refresh();}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}).show();
  }
  void refresh(){status.setText("Fetching prices…");new Thread(()->{
    StringBuilder notes=new StringBuilder();
    try{JSONObject j=fetch("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin-cash%2Cpi-network%2Ctether&vs_currencies=usd");
      putPrice("BCH:bch",j.optJSONObject("bitcoin-cash"));putPrice("PI:pi",j.optJSONObject("pi-network"));putPrice("USDT:usdt",j.optJSONObject("tether"));notes.append("CoinGecko OK. ");
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
    if(value instanceof JSONObject){JSONObject o=(JSONObject)value;JSONObject u=o.optJSONObject("uris");if(u!=null){String s=u.optString("icon","");if(!s.isEmpty()&&!"null".equals(s))return s;}
      String direct=o.optString("icon","");if(direct.startsWith("http")||direct.startsWith("ipfs://"))return direct;
      for(String field:new String[]{"token","identity","snapshot","metadata","data","result","registry","identities","tokens"}){String s=extractIconUri(o.opt(field));if(s!=null)return s;}
    }else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){String s=extractIconUri(a.opt(i));if(s!=null)return s;}}
    return null;
  }
  void loadCashTokenIcon(ImageView target,String category){
    if(category==null||!category.matches("(?i)[a-f0-9]{64}"))return;
    android.graphics.Bitmap cached=cashTokenIconCache.get(category);if(cached!=null){target.setImageBitmap(cached);return;}
    synchronized(iconRequests){if(iconRequests.contains(category))return;iconRequests.add(category);}
    new Thread(()->{android.graphics.Bitmap bitmap=null;try{
      Object meta=fetchAny("https://bcmr.paytaca.com/api/tokens/"+category.toLowerCase(Locale.US));
      String uri=extractIconUri(meta);
      if(uri!=null){String imageUrl=uri;
        if(imageUrl.startsWith("ipfs://")){String path=imageUrl.substring(7);if(path.startsWith("ipfs/"))path=path.substring(5);imageUrl="https://ipfs.io/ipfs/"+path;}
        if(imageUrl.startsWith("https://")||imageUrl.startsWith("http://")){
          HttpURLConnection conn=(HttpURLConnection)new URL(imageUrl).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);conn.setInstanceFollowRedirects(true);conn.setRequestProperty("User-Agent","CashTokensPortfolio/1.2");
          try(InputStream in=conn.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1&&bytes.size()<2097152)bytes.write(buf,0,n);byte[] imageBytes=bytes.toByteArray();bitmap=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);if(bitmap==null){String svgText=new String(imageBytes,"UTF-8");if(svgText.contains("<svg")){SVG svg=SVG.getFromString(svgText);Bitmap rendered=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(rendered);svg.renderToCanvas(canvas,new android.graphics.RectF(0,0,96,96));bitmap=rendered;}}}finally{conn.disconnect();}
        }
      }
    }catch(Exception ignored){}finally{synchronized(iconRequests){iconRequests.remove(category);}}
      if(bitmap!=null){cashTokenIconCache.put(category,bitmap);android.graphics.Bitmap result=bitmap;runOnUiThread(()->{if(target.getTag()==null||category.equals(target.getTag()))target.setImageBitmap(result);});}
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
