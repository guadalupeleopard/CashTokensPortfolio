package org.example.cashtokensportfolio;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyPortfolioWorker extends Worker {
    private final Context app;

    public DailyPortfolioWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        app = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences prefs = app.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
            JSONArray assets = new JSONArray(prefs.getString("portfolio", "[]"));
            if (assets.length() == 0) return Result.success();

            JSONObject market = fetchJson("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin-cash%2Cpi-network%2Ctether&vs_currencies=usd");
            JSONObject bch = market.optJSONObject("bitcoin-cash");
            if (bch == null || bch.optDouble("usd", 0) <= 0) return Result.retry();
            double bchUsd = bch.optDouble("usd", 0);

            java.util.HashMap<String, Double> prices = new java.util.HashMap<>();
            putPrice(prices, "BCH:bch", market.optJSONObject("bitcoin-cash"));
            putPrice(prices, "PI:pi", market.optJSONObject("pi-network"));
            putPrice(prices, "USDT:usdt", market.optJSONObject("tether"));

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null || !"CashToken".equals(asset.optString("type"))) continue;
                String category = asset.optString("id", "").toLowerCase(Locale.US);
                if (category.isEmpty()) return Result.retry();
                Object response = fetchAny("https://indexer.riften.net/cauldron/price/" + category + "/current");
                Double priceUnits = extractCurrentPrice(response);
                int decimals = fetchDecimals(category);
                if (priceUnits == null || decimals < 0 || decimals > 18) return Result.retry();
                double usd = (priceUnits * Math.pow(10d, decimals) / 100000000d) * bchUsd;
                if (!Double.isFinite(usd) || usd <= 0) return Result.retry();
                prices.put("CashToken:" + category, usd);
            }

            double totalUsd = 0;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) return Result.retry();
                String key = asset.optString("type") + ":" + asset.optString("id");
                Double price = prices.get(key);
                double quantity;
                try { quantity = Double.parseDouble(asset.optString("quantity", "0")); }
                catch (Exception e) { return Result.retry(); }
                if (price == null || !Double.isFinite(quantity) || quantity < 0) return Result.retry();
                totalUsd += quantity * price;
                if (!Double.isFinite(totalUsd)) return Result.retry();
            }

            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            JSONObject history = new JSONObject(prefs.getString("portfolio_daily_usd", "{}"));
            history.put(day, totalUsd);
            prefs.edit().putString("portfolio_daily_usd", history.toString()).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void putPrice(java.util.Map<String, Double> prices, String key, JSONObject obj) {
        if (obj != null) {
            double value = obj.optDouble("usd", 0);
            if (Double.isFinite(value) && value > 0) prices.put(key, value);
        }
    }

    private JSONObject fetchJson(String url) throws Exception {
        Object value = fetchAny(url);
        if (!(value instanceof JSONObject)) throw new IOException("Expected JSON object");
        return (JSONObject) value;
    }

    private Object fetchAny(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "CashTrack/1.0.4");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            return new JSONTokener(out.toString("UTF-8")).nextValue();
        } finally {
            connection.disconnect();
        }
    }

    private int fetchDecimals(String category) throws Exception {
        return extractDecimals(fetchAny("https://indexer.riften.net/bcmr/token/" + category));
    }

    private int extractDecimals(Object value) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            JSONObject token = obj.optJSONObject("token");
            if (token != null && token.has("decimals") && !token.isNull("decimals")) {
                int decimals = token.optInt("decimals", -1);
                if (decimals >= 0) return decimals;
            }
            if (obj.has("decimals") && !obj.isNull("decimals")) {
                int decimals = obj.optInt("decimals", -1);
                if (decimals >= 0) return decimals;
            }
            for (String field : new String[]{"data", "result", "metadata", "identity", "snapshot", "registry", "token"}) {
                int decimals = extractDecimals(obj.opt(field));
                if (decimals >= 0) return decimals;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                int decimals = extractDecimals(array.opt(i));
                if (decimals >= 0) return decimals;
            }
        }
        return -1;
    }

    private Double extractCurrentPrice(Object value) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            for (String field : new String[]{"currentPrice", "current_price", "price", "priceSats", "price_sats", "avg"}) {
                Object raw = obj.opt(field);
                if (raw instanceof Number) {
                    double n = ((Number) raw).doubleValue();
                    if (Double.isFinite(n) && n > 0) return n;
                } else if (raw instanceof String) {
                    try {
                        double n = Double.parseDouble((String) raw);
                        if (Double.isFinite(n) && n > 0) return n;
                    } catch (Exception ignored) {}
                }
            }
            for (String field : new String[]{"data", "result", "current", "token", "priceInfo", "price_info"}) {
                Double price = extractCurrentPrice(obj.opt(field));
                if (price != null) return price;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Double price = extractCurrentPrice(array.opt(i));
                if (price != null) return price;
            }
        }
        return null;
    }
}
