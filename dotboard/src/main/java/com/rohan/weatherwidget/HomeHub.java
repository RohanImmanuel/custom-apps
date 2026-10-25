package com.rohan.weatherwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// Local API of an IKEA DIRIGERA hub (community-documented, unofficial): pairing, device list, changing a device.
final class HomeHub {
    static final String SERVICE = "_ihsp._tcp", PORT = ":8443";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("hub", Context.MODE_PRIVATE);
    }

    static boolean paired(Context c) {
        return prefs(c).getString("token", null) != null;
    }

    /**
     * The hub's certificate is self-signed, so trust exactly the one seen when pairing (trust on first use).
     * pin == null records the certificate into seen[0]; otherwise anything else is rejected.
     */
    static OkHttpClient client(String pin, String[] seen) throws Exception {
        X509TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String type) {}
            public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
                String fp = fingerprint(chain[0]);
                if (pin == null) seen[0] = fp;
                else if (!pin.equals(fp)) throw new CertificateException("hub certificate changed");
            }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new X509TrustManager[]{tm}, null);
        return new OkHttpClient.Builder()
                .sslSocketFactory(ssl.getSocketFactory(), tm)
                .hostnameVerifier((host, session) -> true) // the pin above is the identity check
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    static String fingerprint(X509Certificate cert) throws CertificateException {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    static OkHttpClient paired(Context c, String[] unused) throws Exception {
        return client(prefs(c).getString("pin", ""), unused);
    }

    static String base(Context c) {
        return "https://" + prefs(c).getString("host", "") + PORT + "/v1/";
    }

    static JSONArray devices(Context c) throws Exception {
        Request r = new Request.Builder().url(base(c) + "devices")
                .header("Authorization", "Bearer " + prefs(c).getString("token", "")).build();
        try (Response res = paired(c, new String[1]).newCall(r).execute()) {
            if (!res.isSuccessful()) throw new Exception("hub said " + res.code());
            return new JSONArray(res.body().string());
        }
    }

    /** One attribute per request, as the hub expects. */
    static void set(Context c, String deviceId, String attribute, Object value) throws Exception {
        String body = new JSONArray().put(new JSONObject().put("attributes", new JSONObject().put(attribute, value))).toString();
        Request r = new Request.Builder().url(base(c) + "devices/" + deviceId)
                .header("Authorization", "Bearer " + prefs(c).getString("token", ""))
                .patch(RequestBody.create(body, MediaType.get("application/json"))).build();
        try (Response res = paired(c, new String[1]).newCall(r).execute()) {
            if (!res.isSuccessful()) throw new Exception("hub said " + res.code());
        }
    }

    // --- pairing ---

    /** Finds the hub on the local network (it advertises _ihsp._tcp); returns its IP or null. */
    @SuppressWarnings("deprecation") // resolveService is fine for a one-off lookup
    static String discover(Context c, long timeoutMs) throws InterruptedException {
        NsdManager nsd = c.getSystemService(NsdManager.class);
        CountDownLatch found = new CountDownLatch(1);
        AtomicReference<String> host = new AtomicReference<>();
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            public void onServiceFound(NsdServiceInfo s) {
                nsd.resolveService(s, new NsdManager.ResolveListener() {
                    public void onServiceResolved(NsdServiceInfo r) { host.set(r.getHost().getHostAddress()); found.countDown(); }
                    public void onResolveFailed(NsdServiceInfo r, int err) {}
                });
            }
            public void onStartDiscoveryFailed(String t, int e) { found.countDown(); }
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onServiceLost(NsdServiceInfo s) {}
        };
        nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener);
        found.await(timeoutMs, TimeUnit.MILLISECONDS);
        try { nsd.stopServiceDiscovery(listener); } catch (Exception ignored) {}
        return host.get();
    }

    /** Pairing session: PKCE code from the hub; then {@link #poll} until the action button is pressed. */
    static class Pairing {
        final String host, verifier, code, pin;

        Pairing(String host) throws Exception {
            this.host = host;
            String alphabet = "_-~.ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            SecureRandom rnd = new SecureRandom();
            StringBuilder v = new StringBuilder();
            for (int i = 0; i < 128; i++) v.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            verifier = v.toString();
            String challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII")));
            String[] seen = new String[1];
            Request r = new Request.Builder().url("https://" + host + PORT + "/v1/oauth/authorize?audience=homesmart.local"
                    + "&response_type=code&code_challenge=" + challenge + "&code_challenge_method=S256").build();
            try (Response res = client(null, seen).newCall(r).execute()) {
                if (!res.isSuccessful()) throw new Exception("hub said " + res.code());
                code = new JSONObject(res.body().string()).getString("code");
            }
            pin = seen[0];
        }

        /** Returns the token once the button has been pressed, otherwise null. */
        String poll() throws Exception {
            RequestBody form = new FormBody.Builder().add("code", code).add("name", "Dotboard (" + android.os.Build.MODEL + ")")
                    .add("grant_type", "authorization_code").add("code_verifier", verifier).build();
            Request r = new Request.Builder().url("https://" + host + PORT + "/v1/oauth/token").post(form).build();
            try (Response res = client(pin, new String[1]).newCall(r).execute()) {
                return res.isSuccessful() ? new JSONObject(res.body().string()).getString("access_token") : null;
            }
        }

        void save(Context c, String token) {
            prefs(c).edit().putString("host", host).putString("pin", pin).putString("token", token).apply();
        }
    }
}
