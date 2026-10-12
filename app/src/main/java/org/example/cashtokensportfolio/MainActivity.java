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
  final HashMap<String, Double> prices = new HashMap<>();
  final HashMap<String, String> ids = new HashMap<>();
  final HashMap<String, android.graphics.Bitmap> cashTokenIconCache = new HashMap<>();
  final HashSet<String> iconRequests = new HashSet<>();
  final String PREF="portfolio";
  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(20,40,43));t.setPadding(dp(4),dp(10),dp(4),dp(10));return t;}
  void addButton(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);parent.addView(b);b.setOnClickListener(v->action.run());}
  int pageColor(){return themeMode==1?Color.BLACK:themeMode==2?Color.rgb(10,193,142):Color.WHITE;}
  int cardColor(){return themeMode==1?Color.rgb(18,18,18):themeMode==2?Color.rgb(7,151,108):Color.WHITE;}
  int textColor(){return themeMode==1?Color.WHITE:themeMode==2?Color.WHITE:Color.BLACK;}
  int buttonColor(){return themeMode==1?Color.rgb(45,45,45):themeMode==2?Color.rgb(0,105,76):Color.rgb(235,241,239);}
  void openMenu(){String current=themeMode==0?"Day":themeMode==1?"Dark":"Bitcoin Cash Green";new AlertDialog.Builder(this).setTitle("Menu").setItems(new String[]{"Background: "+current},(dialog,which)->{if(which==0)chooseTheme();}).show();}
  void chooseTheme(){new AlertDialog.Builder(this).setTitle("Choose background").setItems(new String[]{"☀  Day","☾  Dark","₿  Bitcoin Cash Green"},(dialog,which)->{themeMode=which;getPreferences(0).edit().putInt("theme_mode",themeMode).apply();applyTheme();}).show();}
  void applyTheme(){if(root==null)return;root.setBackgroundColor(pageColor());if(themeButton!=null){themeButton.setText("☰");themeButton.setContentDescription("Open menu");}applyThemeTo(root);}
  void applyThemeTo(View view){if(view instanceof TextView){((TextView)view).setTextColor(textColor());}if(view instanceof Button){view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor()));}if(view instanceof LinearLayout){LinearLayout ll=(LinearLayout)view;if(ll==root)ll.setBackgroundColor(pageColor());else if(ll==items){}else if(ll.getOrientation()==LinearLayout.VERTICAL&&ll.getChildCount()>0)ll.setBackgroundColor(cardColor());}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyThemeTo(group.getChildAt(i));}}
  @Override public void onCreate(Bundle b){super.onCreate(b);themeMode=getPreferences(0).getInt("theme_mode",0);try{assets=new JSONArray(getPreferences(0).getString(PREF,"[]"));}catch(Exception ignored){} build(); refresh();}
  void save(){getPreferences(0).edit().putString(PREF,assets.toString()).apply();}
  void build(){ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setPadding(dp(18),dp(20),dp(18),dp(20));root.setOrientation(1);root.setBackgroundColor(Color.rgb(244,249,247));scroll.addView(root);setContentView(scroll);
    LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);ImageView appLogo=new ImageView(this);appLogo.setImageResource(R.drawable.cashtrack_icon);appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams appLogoLp=new LinearLayout.LayoutParams(dp(48),dp(48));appLogoLp.rightMargin=dp(12);header.addView(appLogo,appLogoLp);TextView heading=label("CashTokens Portfolio",26);LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(0,-2,1f);header.addView(heading,headingLp);themeButton=new Button(this);themeButton.setText("☰");themeButton.setTextSize(22);themeButton.setMinWidth(0);themeButton.setMinimumWidth(0);themeButton.setPadding(dp(10),dp(4),dp(10),dp(4));themeButton.setContentDescription("Open menu");header.addView(themeButton);themeButton.setOnClickListener(v->openMenu());root.addView(header);root.addView(label("Watch-only • Stored on this device",13));total=label("Total: —",23);root.addView(total);status=label("Prices not loaded",12);root.addView(status);
    addButton(root,"+ Add asset",this::addAssetDialog);addButton(root,"Refresh market prices",this::refresh);items=new LinearLayout(this);items.setOrientation(1);root.addView(items);render();}
  String key(JSONObject a){return a.optString("type")+":"+a.optString("id");}
