package ro.pub.cs.systems.eim.model_colocviu2;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;


public class CommunicationThread extends Thread {

    private final ServerThread serverThread;
    private final Socket socket;

    public CommunicationThread(ServerThread serverThread, Socket socket) {
        this.serverThread = serverThread;
        this.socket = socket;
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        conn.disconnect();
        return sb.toString();
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            String city = in.readLine();
            String type = in.readLine();

            if (city == null || city.trim().isEmpty() || type == null || type.trim().isEmpty()) {
                out.println("ERROR: city/type missing");
                socket.close();
                return;
            }

            city = city.trim();
            type = type.trim();

            WeatherForecastInformation info = serverThread.getFromCache(city);

            if (info == null) {
                info = fetchWeather(city);
                if (info != null) {
                    serverThread.putInCache(city, info);
                }
            }

            if (info == null) {
                out.println("ERROR: could not fetch data");
            } else {
                out.println(formatResponse(city, type, info));
            }

            socket.close();

        } catch (Exception e) {
            Log.e(Constants.TAG, "[COMM] " + e.getMessage());
        }
    }

    private WeatherForecastInformation fetchWeather(String city) {
        try {
            String encCity = URLEncoder.encode(city, "UTF-8");
            String url = String.format(Constants.WEATHER_URL, encCity);

            // folosim metoda ta care merge sigur
            String json = httpGet(url);

            JSONObject root = new JSONObject(json);
            JSONArray ccArr = root.getJSONArray("current_condition");
            JSONObject cc = ccArr.getJSONObject(0);

            WeatherForecastInformation info = new WeatherForecastInformation();
            info.setTemperature(cc.optString("temp_C", ""));
            info.setWindSpeed(cc.optString("windspeedKmph", ""));
            info.setPressure(cc.optString("pressure", ""));
            info.setHumidity(cc.optString("humidity", ""));

            JSONArray descArr = cc.getJSONArray("weatherDesc");
            String cond = descArr.getJSONObject(0).optString("value", "");
            info.setCondition(cond);

            return info;

        } catch (Exception e) {
            Log.e(Constants.TAG, "[HTTP] " + e.getMessage());
            return null;
        }
    }


    private String formatResponse(String city, String type, WeatherForecastInformation info) {
        switch (type) {
            case Constants.TEMP:
                return city + " temperature: " + info.getTemperature() + " C";
            case Constants.WIND:
                return city + " wind: " + info.getWindSpeed() + " km/h";
            case Constants.COND:
                return city + " condition: " + info.getCondition();
            case Constants.PRESS:
                return city + " pressure: " + info.getPressure() + " hPa";
            case Constants.HUM:
                return city + " humidity: " + info.getHumidity() + " %";
            case Constants.ALL:
            default:
                return city + " | temp=" + info.getTemperature() + "C, wind=" + info.getWindSpeed() +
                        "km/h, cond=" + info.getCondition() + ", press=" + info.getPressure() +
                        "hPa, hum=" + info.getHumidity() + "%";
        }
    }
}

