package com.dentacloud.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Field-level AES-256-GCM for clinical notes, history, allergies, phone and address, and HMAC blind
 * indexes so encrypted phones stay searchable (PRD 14).
 * <p>
 * Each clinic has its own data key, stored in {@code clinic_key} wrapped by the master key
 * (FIELD_ENC_KEY, the stand-in for KMS). Blob layout is {@code [version][iv][ciphertext]}:
 * version 1 is the legacy app-wide key, version 2 the clinic key with the clinic id bound as AAD,
 * so a value copied into another clinic's row fails to decrypt.
 * ponytail: blind indexes stay app-wide; per-clinic index keys would need a reindex of every phone.
 */
@Component
public class FieldCrypto {
    private static final Logger log = LoggerFactory.getLogger(FieldCrypto.class);
    private static final byte LEGACY = 1;
    private static final byte CLINIC = 2;
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec masterKey;
    private final SecretKeySpec indexKey;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTx;
    /** Unwrapped keys, filled only from committed rows. Keys never change, so entries never go stale. */
    private final Map<UUID, SecretKeySpec> clinicKeys = new ConcurrentHashMap<>();

    public FieldCrypto(AppProperties props, JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.masterKey = new SecretKeySpec(key(props.fieldEncKey(), "field", props), "AES");
        this.indexKey = new SecretKeySpec(key(props.blindIndexKey(), "blind", props), "HmacSHA256");
        this.jdbc = jdbc;
        this.newTx = new TransactionTemplate(tm);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Encrypts with the current clinic's key. */
    public byte[] encrypt(String plain) {
        if (plain == null) return null;
        UUID clinic = TenantContext.require();
        byte[] ct = seal(clinicKey(clinic), plain.getBytes(StandardCharsets.UTF_8), aad(clinic));
        return ByteBuffer.allocate(1 + ct.length).put(CLINIC).put(ct).array();
    }

    public String decrypt(byte[] blob) {
        if (blob == null) return null;
        byte version = blob[0];
        byte[] body = Arrays.copyOfRange(blob, 1, blob.length);
        byte[] plain = switch (version) {
            case LEGACY -> open(masterKey, body, null);
            case CLINIC -> {
                UUID clinic = TenantContext.require();
                yield open(clinicKey(clinic), body, aad(clinic));
            }
            default -> throw new IllegalStateException("unknown key version");
        };
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** Deterministic HMAC of the last 10 digits, so "+91 98450 00001" and "9845000001" match. */
    public byte[] phoneIndex(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            return mac.doFinal(digits.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("blind index failed", e);
        }
    }

    private SecretKeySpec clinicKey(UUID clinic) {
        return clinicKeys.computeIfAbsent(clinic, c -> {
            // Own committed transaction, whatever the caller's: a key used inside a transaction that
            // later rolled back would leave data elsewhere encrypted under a key nobody stored.
            // Insert-if-absent then read back, so concurrent first writers all end up on one key.
            byte[] dek = new byte[32];
            RANDOM.nextBytes(dek);
            byte[] candidate = seal(masterKey, dek, wrapAad(c));
            byte[] wrapped = newTx.execute(s -> {
                jdbc.update("insert into clinic_key (clinic_id, wrapped_key) values (?, ?) on conflict do nothing", c, candidate);
                return jdbc.queryForObject("select wrapped_key from clinic_key where clinic_id = ?", byte[].class, c);
            });
            return new SecretKeySpec(open(masterKey, wrapped, wrapAad(c)), "AES");
        });
    }

    private static byte[] aad(UUID clinic) {
        return ("field:" + clinic).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] wrapAad(UUID clinic) {
        return ("clinic-key:" + clinic).getBytes(StandardCharsets.UTF_8);
    }

    /** AES-GCM, returns {@code [iv][ciphertext+tag]}. */
    private static byte[] seal(SecretKeySpec key, byte[] plain, byte[] aad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            if (aad != null) c.updateAAD(aad);
            byte[] ct = c.doFinal(plain);
            return ByteBuffer.allocate(IV_BYTES + ct.length).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    private static byte[] open(SecretKeySpec key, byte[] sealed, byte[] aad) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, sealed, 0, IV_BYTES));
            if (aad != null) c.updateAAD(aad);
            return c.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }

    private static byte[] key(String configured, String label, AppProperties props) {
        if (configured != null && !configured.isBlank()) {
            byte[] k = Base64.getDecoder().decode(configured);
            if (k.length != 32) throw new IllegalArgumentException(label + " key must be 32 bytes (base64)");
            return k;
        }
        // Dev convenience only: derive from the JWT secret so local data survives restarts.
        log.warn("No {} key configured; deriving a development key. Set FIELD_ENC_KEY and BLIND_INDEX_KEY in production.", label);
        try {
            return MessageDigest.getInstance("SHA-256").digest((label + ":" + props.jwtSecret()).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
