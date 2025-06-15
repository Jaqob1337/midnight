package de.peter1337.midnight.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.session.Session;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class MicrosoftAuthenticator {

    private static final String CLIENT_ID = "00000000402b5328"; // Public Minecraft Client ID
    private static final Gson GSON = new Gson();
    private static final Pattern UUID_PATTERN = Pattern.compile("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})");

    /**
     * A wrapper class to hold both the resulting session and the new refresh token.
     */
    public static class AuthResult {
        public final Session session;
        public final String refreshToken;

        public AuthResult(Session session, String refreshToken) {
            this.session = session;
            this.refreshToken = refreshToken;
        }
    }

    public static AuthResult loginWithMicrosoft(String refreshToken) throws Exception {
        // 1. Refresh Microsoft Token
        String microsoftTokenUrl = "https://login.live.com/oauth20_token.srf";
        String microsoftTokenData = "client_id=" + CLIENT_ID +
                "&grant_type=refresh_token" +
                "&refresh_token=" + refreshToken;
        JsonObject microsoftTokenResponse = postJson(microsoftTokenUrl, microsoftTokenData, "application/x-www-form-urlencoded");
        String accessToken = microsoftTokenResponse.get("access_token").getAsString();
        String newRefreshToken = microsoftTokenResponse.get("refresh_token").getAsString();


        // 2. Authenticate with Xbox Live
        String xblUrl = "https://user.auth.xboxlive.com/user/authenticate";
        JsonObject xblProperties = new JsonObject();
        xblProperties.addProperty("AuthMethod", "RPS");
        xblProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProperties.addProperty("RpsTicket", "d=" + accessToken);
        JsonObject xblPayload = new JsonObject();
        xblPayload.add("Properties", xblProperties);
        xblPayload.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblPayload.addProperty("TokenType", "JWT");
        JsonObject xblResponse = postJson(xblUrl, xblPayload.toString(), "application/json");
        String xblToken = xblResponse.get("Token").getAsString();
        String userHash = xblResponse.get("DisplayClaims").getAsJsonObject().get("xui").getAsJsonArray().get(0).getAsJsonObject().get("uhs").getAsString();

        // 3. Authenticate with XSTS
        String xstsUrl = "https://xsts.auth.xboxlive.com/xsts/authorize";
        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProperties.add("UserTokens", userTokens);
        JsonObject xstsPayload = new JsonObject();
        xstsPayload.add("Properties", xstsProperties);
        xstsPayload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsPayload.addProperty("TokenType", "JWT");
        JsonObject xstsResponse = postJson(xstsUrl, xstsPayload.toString(), "application/json");
        String xstsToken = xstsResponse.get("Token").getAsString();

        // 4. Authenticate with Minecraft
        String minecraftUrl = "https://api.minecraftservices.com/authentication/login_with_xbox";
        JsonObject minecraftPayload = new JsonObject();
        minecraftPayload.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject minecraftResponse = postJson(minecraftUrl, minecraftPayload.toString(), "application/json");
        String minecraftAccessToken = minecraftResponse.get("access_token").getAsString();

        // 5. Get Minecraft Profile
        String profileUrl = "https://api.minecraftservices.com/minecraft/profile";
        JsonObject profileResponse = getJsonWithAuth(profileUrl, minecraftAccessToken);
        String uuidStr = profileResponse.get("id").getAsString();
        String username = profileResponse.get("name").getAsString();

        // Format UUID with hyphens for the Session object
        UUID uuid = UUID.fromString(UUID_PATTERN.matcher(uuidStr).replaceAll("$1-$2-$3-$4-$5"));
        Session session = new Session(username, uuid, minecraftAccessToken, Optional.empty(), Optional.empty(), Session.AccountType.MSA);

        return new AuthResult(session, newRefreshToken);
    }

    public static DeviceCode getDeviceCode() throws Exception {
        String url = "https://login.live.com/oauth20_device.srf";
        String data = "client_id=" + CLIENT_ID + "&scope=service::user.auth.xboxlive.com::MBI_SSL";
        JsonObject response = postJson(url, data, "application/x-www-form-urlencoded");
        return GSON.fromJson(response, DeviceCode.class);
    }

    public static AuthResult pollForToken(DeviceCode code) throws Exception {
        String url = "https://login.live.com/oauth20_token.srf";
        String data = "client_id=" + CLIENT_ID + "&grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=" + code.device_code;
        JsonObject response = postJson(url, data, "application/x-www-form-urlencoded");

        if(response.has("error")){
            String error = response.get("error").getAsString();
            if(error.equals("authorization_pending")){
                return null; // Still waiting for user
            }
            throw new Exception("Microsoft Auth Error: " + response.get("error_description").getAsString());
        }

        String refreshToken = response.get("refresh_token").getAsString();
        return loginWithMicrosoft(refreshToken);
    }


    private static JsonObject postJson(String url, String data, String contentType) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = data.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    private static JsonObject getJsonWithAuth(String url, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }


    public static class DeviceCode {
        public String user_code;
        public String device_code;
        public String verification_uri;
        public int expires_in;
        public int interval;
    }
}