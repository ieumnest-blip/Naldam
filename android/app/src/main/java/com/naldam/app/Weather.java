package com.naldam.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 위젯에 보여줄 날씨를 가져와서 저장해 둬요.
 * 날담 앱과 똑같이: 기상청(날담 중계 서버) 값을 먼저 쓰고, 없으면 Open-Meteo 값을 써요.
 */
final class Weather {
    static final String PREFS = "naldam_widget";
    private static final String KMA_PROXY = "https://naldam.ii-iji.workers.dev";

    // ---- 저장된 값 ----
    String place = "";       // 예: 대구광역시 달서구
    String placeShort = "";  // 예: 달서구
    double lat, lon;
    double temp = Double.NaN, feels = Double.NaN, tmax = Double.NaN, tmin = Double.NaN;
    double wind = Double.NaN, windDir = Double.NaN;
    int code = -1;
    String cond = "";
    boolean kma = false;
    long time = 0;           // 받아온 시각 (ms)

    private static final Map<Integer, String> WMO = new HashMap<>();
    static {
        Object[][] m = {{0,"맑음"},{1,"대체로 맑음"},{2,"구름 조금"},{3,"흐림"},{45,"안개"},{48,"서리 안개"},
            {51,"약한 이슬비"},{53,"이슬비"},{55,"강한 이슬비"},{56,"어는 이슬비"},{57,"어는 이슬비"},
            {61,"약한 비"},{63,"비"},{65,"강한 비"},{66,"어는 비"},{67,"강한 어는 비"},
            {71,"약한 눈"},{73,"눈"},{75,"많은 눈"},{77,"싸락눈"},{80,"약한 소나기"},{81,"소나기"},{82,"강한 소나기"},
            {85,"소낙눈"},{86,"강한 소낙눈"},{95,"뇌우"},{96,"우박 동반 뇌우"},{99,"강한 우박 뇌우"}};
        for (Object[] p : m) WMO.put((Integer) p[0], (String) p[1]);
    }

    /** 날씨 코드 → 그림 종류 (앱의 kindOf 와 같음) */
    static String kindOf(int code) {
        if (code < 0) return "none";
        if (code <= 1) return "sun";
        if (code == 2) return "part";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if (code >= 95) return "storm";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        return "rain";
    }

    /** 기상청 강수형태(pty)·하늘상태(sky) → [코드, 이름] (앱의 kmaCond 와 같음) */
    private static Object[] kmaCond(int pty, int sky) {
        switch (pty) {
            case 1: return new Object[]{63, "비"};
            case 2: return new Object[]{66, "비/눈"};
            case 3: return new Object[]{73, "눈"};
            case 4: return new Object[]{80, "소나기"};
            case 5: return new Object[]{51, "빗방울"};
            case 6: return new Object[]{66, "빗방울눈날림"};
            case 7: return new Object[]{71, "눈날림"};
        }
        switch (sky) {
            case 1: return new Object[]{0, "맑음"};
            case 3: return new Object[]{2, "구름많음"};
            case 4: return new Object[]{3, "흐림"};
        }
        return null;
    }

    private static boolean inKorea(double lat, double lon) {
        return lat >= 33 && lat <= 38.9 && lon >= 124.5 && lon <= 131.9;
    }

    /** 인터넷에서 새로 받아오기 (백그라운드 스레드에서 불러야 해요) */
    static Weather fetch(Context ctx, double lat, double lon) throws Exception {
        final Weather w = new Weather();
        w.lat = lat;
        w.lon = lon;
        final JSONObject[] om = new JSONObject[1];
        final JSONObject[] km = new JSONObject[1];
        final String[] names = new String[2];

        String omUrl = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
            + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m"
            + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&wind_speed_unit=ms&timezone=Asia%%2FSeoul",
            lat, lon);
        Thread t1 = new Thread(() -> { try { om[0] = getJson(omUrl); } catch (Exception ignored) { } });
        Thread t2 = new Thread(() -> {
            if (!inKorea(lat, lon)) return;
            try {
                JSONObject j = getJson(String.format(Locale.US, "%s/?lat=%.4f&lon=%.4f", KMA_PROXY, lat, lon));
                if (!j.has("error")) km[0] = j;
            } catch (Exception ignored) { }
        });
        Thread t3 = new Thread(() -> {
            String[] n = placeName(ctx, lat, lon);
            names[0] = n[0];
            names[1] = n[1];
        });
        t1.start(); t2.start(); t3.start();
        t1.join(9000); t2.join(9000); t3.join(6000);

        if (om[0] == null && km[0] == null) throw new Exception("no data");

        if (om[0] != null) {
            JSONObject c = om[0].optJSONObject("current");
            if (c != null) {
                w.temp = c.optDouble("temperature_2m", Double.NaN);
                w.feels = c.optDouble("apparent_temperature", Double.NaN);
                w.code = c.optInt("weather_code", -1);
                w.wind = c.optDouble("wind_speed_10m", Double.NaN);
                w.windDir = c.optDouble("wind_direction_10m", Double.NaN);
            }
            JSONObject d = om[0].optJSONObject("daily");
            if (d != null) {
                if (d.optJSONArray("temperature_2m_max") != null) w.tmax = d.optJSONArray("temperature_2m_max").optDouble(0, Double.NaN);
                if (d.optJSONArray("temperature_2m_min") != null) w.tmin = d.optJSONArray("temperature_2m_min").optDouble(0, Double.NaN);
            }
            String nm = WMO.get(w.code);
            w.cond = nm != null ? nm : "";
        }
        if (km[0] != null) {
            JSONObject n = km[0].optJSONObject("now");
            JSONObject t = km[0].optJSONObject("today");
            if (n != null) {
                if (!n.isNull("temp") && n.has("temp")) { w.temp = n.optDouble("temp", w.temp); if (Double.isNaN(w.feels)) w.feels = w.temp; }
                if (n.has("wind") && !n.isNull("wind")) w.wind = n.optDouble("wind", w.wind);
                if (n.has("windDir") && !n.isNull("windDir")) w.windDir = n.optDouble("windDir", w.windDir);
            }
            if (t != null) {
                if (t.has("tmax") && !t.isNull("tmax")) w.tmax = t.optDouble("tmax", w.tmax);
                if (t.has("tmin") && !t.isNull("tmin")) w.tmin = t.optDouble("tmin", w.tmin);
            }
            int pty = n != null ? n.optInt("pty", 0) : 0;
            int sky = t != null ? t.optInt("sky", 0) : 0;
            Object[] kc = kmaCond(pty, sky);
            if (kc != null) { w.code = (Integer) kc[0]; w.cond = (String) kc[1]; }
            if (!Double.isNaN(w.tmax) && w.temp > w.tmax) w.tmax = w.temp;
            if (!Double.isNaN(w.tmin) && w.temp < w.tmin) w.tmin = w.temp;
            w.kma = true;
        }
        w.place = names[0] != null ? names[0] : String.format(Locale.US, "%.2f, %.2f", lat, lon);
        w.placeShort = names[1] != null ? names[1] : w.place;
        w.time = System.currentTimeMillis();
        return w;
    }

    /** 위도·경도 → [긴 이름, 짧은 이름]  예: ["대구광역시 달서구 월성동", "달서구 월성동"] */
    @SuppressWarnings("deprecation")
    private static String[] placeName(Context ctx, double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return new String[2];
            List<Address> list = new Geocoder(ctx, Locale.KOREA).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return new String[2];
            Address a = list.get(0);
            String big = a.getAdminArea();
            String mid = a.getSubLocality() != null ? a.getSubLocality() : a.getLocality();
            String small = a.getThoroughfare();
            if (small != null && !small.matches(".*(동|읍|면|리|가)$")) small = null; // 도로 이름은 빼고 동네 이름만
            StringBuilder full = new StringBuilder();
            StringBuilder shorter = new StringBuilder();
            for (String s : new String[]{big, mid, small}) {
                if (s == null || s.isEmpty() || full.indexOf(s) >= 0) continue;
                if (full.length() > 0) full.append(' ');
                full.append(s);
            }
            for (String s : new String[]{mid, small}) {
                if (s == null || s.isEmpty() || shorter.indexOf(s) >= 0) continue;
                if (shorter.length() > 0) shorter.append(' ');
                shorter.append(s);
            }
            String f = full.length() > 0 ? full.toString() : null;
            String sh = shorter.length() > 0 ? shorter.toString() : f;
            return new String[]{f, sh};
        } catch (Exception e) {
            return new String[2];
        }
    }

    private static JSONObject getJson(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestProperty("User-Agent", "Naldam-Android-Widget");
        try {
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while (in != null && (r = in.read(buf)) > 0) bo.write(buf, 0, r);
            if (in != null) in.close();
            return new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
    }

    // ---- 저장 / 불러오기 ----
    void save(Context ctx) {
        try {
            JSONObject j = new JSONObject();
            j.put("place", place); j.put("placeShort", placeShort);
            j.put("lat", lat); j.put("lon", lon);
            putD(j, "temp", temp); putD(j, "feels", feels); putD(j, "tmax", tmax); putD(j, "tmin", tmin);
            putD(j, "wind", wind); putD(j, "windDir", windDir);
            j.put("code", code); j.put("cond", cond); j.put("kma", kma); j.put("time", time);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("weather", j.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static void putD(JSONObject j, String k, double v) throws Exception {
        if (!Double.isNaN(v)) j.put(k, v);
    }

    static Weather load(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("weather", null);
        if (s == null) return null;
        try {
            JSONObject j = new JSONObject(s);
            Weather w = new Weather();
            w.place = j.optString("place"); w.placeShort = j.optString("placeShort", w.place);
            w.lat = j.optDouble("lat"); w.lon = j.optDouble("lon");
            w.temp = j.optDouble("temp", Double.NaN); w.feels = j.optDouble("feels", Double.NaN);
            w.tmax = j.optDouble("tmax", Double.NaN); w.tmin = j.optDouble("tmin", Double.NaN);
            w.wind = j.optDouble("wind", Double.NaN); w.windDir = j.optDouble("windDir", Double.NaN);
            w.code = j.optInt("code", -1); w.cond = j.optString("cond"); w.kma = j.optBoolean("kma");
            w.time = j.optLong("time");
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 문장 (앱과 같은 말투) ----
    private static final Object[][] WIND_SCALE = {
        {0.3, "고요", "바람이 거의 없어"},
        {1.6, "실바람", "연기가 살짝 흔들리는 정도야"},
        {3.4, "남실바람", "얼굴에 바람이 살랑살랑 느껴져"},
        {5.5, "산들바람", "머리카락이 살랑 날려"},
        {8.0, "건들바람", "먼지가 날리고 좀 쌀쌀해"},
        {10.8, "흔들바람", "나무가 흔들려, 꽤 춥게 느껴질 거야"},
        {13.9, "된바람", "우산 쓰기 힘들어!"},
        {17.2, "센바람", "걷기도 힘들 정도야"},
        {Double.MAX_VALUE, "큰바람", "오늘은 밖에 나가지 마!"}};

    static String[] windFeel(double ms) {
        if (Double.isNaN(ms)) return null;
        for (Object[] w : WIND_SCALE) if (ms < (Double) w[0]) return new String[]{(String) w[1], (String) w[2]};
        return null;
    }

    private static final String[] DIR = {"북","북북동","북동","동북동","동","동남동","남동","남남동","남","남남서","남서","서남서","서","서북서","북서","북북서"};

    static String dirName(double deg) {
        if (Double.isNaN(deg)) return null;
        return DIR[(int) Math.round((deg % 360) / 22.5) % 16];
    }

    /** 앱 홈 화면의 한마디와 같아요 */
    String chatLine() {
        String k = kindOf(code);
        double f = Double.isNaN(feels) ? temp : feels;
        if (k.equals("rain") || k.equals("storm")) return "우산 꼭 챙겨요 ☂";
        if (k.equals("snow")) return "따뜻하게 입고 나가요";
        if (!Double.isNaN(f) && f <= 5) return "많이 추워요, 목도리 챙겨요";
        if (!Double.isNaN(f) && f >= 30) return "더워요! 물 자주 마셔요";
        if (k.equals("sun")) return "산책하기 좋은 날이에요";
        if (k.equals("fog")) return "앞이 흐려요, 조심조심";
        return "오늘 하루도 화이팅";
    }
}
