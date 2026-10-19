package com.dentacloud.shared;

import com.dentacloud.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Per-clinic data keys wrapped by the master key (PRD 14). */
class FieldCryptoTest extends IntegrationTest {
    /** Same dummy as application-test.yml. */
    static final byte[] MASTER = Base64.getDecoder().decode("ft2/lMB5NAxulFnq/2gcoEsxoRVY1vzWoQYhxU7InfQ=");

    @Autowired FieldCrypto crypto;

    @Test
    void eachClinicGetsItsOwnStoredKeyAndApiRoundTrips() throws Exception {
        Map<String, Object> a = signup("Key Alpha");
        Map<String, Object> b = signup("Key Beta");
        String pa = (String) newPatient(token(a), "Asha", "9845000001").get("id");
        newPatient(token(b), "Bala", "9845000002");

        assertThat(get("/api/v1/patients/" + pa, token(a)).getBody()).containsEntry("phone", "9845000001");
        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            var phone = c.prepareStatement("select phone_enc from patient where id = ?::uuid");
            phone.setString(1, pa);
            var rs = phone.executeQuery();
            rs.next();
            assertThat(rs.getBytes(1)[0]).as("clinic-key blob version").isEqualTo((byte) 2);

            var keys = c.prepareStatement("select wrapped_key from clinic_key where clinic_id in (?::uuid, ?::uuid)");
            keys.setString(1, (String) a.get("clinicId"));
            keys.setString(2, (String) b.get("clinicId"));
            rs = keys.executeQuery();
            rs.next();
            byte[] first = rs.getBytes(1);
            rs.next();
            assertThat(rs.getBytes(1)).isNotEqualTo(first);
        }
    }

    @Test
    void ciphertextFromAnotherClinicDoesNotDecrypt() {
        UUID a = UUID.fromString((String) signup("Swap Alpha").get("clinicId"));
        UUID b = UUID.fromString((String) signup("Swap Beta").get("clinicId"));

        byte[] blob = TenantContext.callAs(a, () -> crypto.encrypt("allergic to penicillin"));
        assertThat(TenantContext.callAs(a, () -> crypto.decrypt(blob))).isEqualTo("allergic to penicillin");
        assertThatThrownBy(() -> TenantContext.callAs(b, () -> crypto.decrypt(blob)))
                .hasMessageContaining("decryption failed");
    }

    @Test
    void legacyAppWideBlobsStillDecrypt() throws Exception {
        UUID a = UUID.fromString((String) signup("Legacy Alpha").get("clinicId"));
        byte[] iv = new byte[12];
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(MASTER, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal("old note".getBytes(StandardCharsets.UTF_8));
        byte[] v1 = ByteBuffer.allocate(1 + iv.length + ct.length).put((byte) 1).put(iv).put(ct).array();

        assertThat(TenantContext.callAs(a, () -> crypto.decrypt(v1))).isEqualTo("old note");
    }
}
