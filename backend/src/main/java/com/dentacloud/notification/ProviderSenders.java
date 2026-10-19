package com.dentacloud.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks real providers when configured: MSG91 for SMS (DLT template ids per template key) and FCM
 * HTTP v1 for push (service-account JSON). Otherwise the logging senders.
 */
@Configuration
class ProviderSenders {
    @Bean
    Senders.SmsSender smsSender(@Value("${dentacloud.msg91.auth-key:}") String authKey,
                                @Value("${dentacloud.msg91.template-id:}") String templateId) {
        if (authKey.isBlank() || templateId.isBlank()) return new Senders.LoggingSms();
        RestClient http = RestClient.create("https://control.msg91.com");
        return (phone, templateKey, vars) -> {
            Map<String, Object> recipient = new HashMap<>(vars);
            String digits = phone.replaceAll("\\D", "");
            recipient.put("mobiles", digits.length() == 10 ? "91" + digits : digits);
            JsonNode res = http.post().uri("/api/v5/flow")
                    .header("authkey", authKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("template_id", templateId, "short_url", "0", "recipients", List.of(recipient)))
                    .retrieve().body(JsonNode.class);
            return res == null ? null : res.path("message").asText(null);
        };
    }

    @Bean
    Senders.PushSender pushSender(@Value("${dentacloud.firebase.credentials:}") String credentialsPath, ObjectMapper json)
            throws Exception {
        if (credentialsPath.isBlank()) return new Senders.LoggingPush();
        JsonNode sa = json.readTree(Files.readString(Path.of(credentialsPath)));
        String projectId = sa.path("project_id").asText();
        String clientEmail = sa.path("client_email").asText();
        String pem = sa.path("private_key").asText().replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        RestClient http = RestClient.create();
        return (tokens, data) -> {
            String access = accessToken(http, clientEmail, key);
            int ok = 0;
            for (String t : tokens) {
                http.post().uri("https://fcm.googleapis.com/v1/projects/{p}/messages:send", projectId)
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", Map.of("token", t, "data", data, "android", Map.of("priority", "high"))))
                        .retrieve().toBodilessEntity();
                ok++;
            }
            return ok;
        };
    }

    /** OAuth2 JWT-bearer grant with the service account key (Nimbus is already on the classpath). */
    private static String accessToken(RestClient http, String clientEmail, RSAPrivateKey key) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .issuer(clientEmail).audience("https://oauth2.googleapis.com/token")
                .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).build());
        jwt.sign(new RSASSASigner(key));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", jwt.serialize());
        // ponytail: fetches a token per batch; cache it for its hour if push volume grows.
        JsonNode res = http.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
        return res.path("access_token").asText();
    }
}
