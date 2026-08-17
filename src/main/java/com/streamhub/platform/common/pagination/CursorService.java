package com.streamhub.platform.common.pagination;

import com.streamhub.platform.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Signs and verifies pagination cursors with HMAC-SHA256 using the global
 * pagination salt (`app.pagination.salt`).
 * <p>
 * Every module's list endpoints share this same service, so pagination
 * behaves identically everywhere and a client cannot forge a cursor for a
 * page/limit combination it was never issued - any tampering invalidates
 * the signature and the request is rejected with 400 Bad Request.
 */
@Service
public class CursorService {

    private static final String ALGORITHM = "HmacSHA256";

    private final String salt;

    public CursorService(@Value("${app.pagination.salt}") String salt) {
        this.salt = salt;
    }

    public String encode(int page, int limit) {
        String payload = page + ":" + limit;
        String signature = sign(payload);
        String raw = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public record DecodedCursor(int page, int limit) {}

    public DecodedCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 3) {
                throw new BadRequestException("Malformed pagination cursor");
            }
            String payload = parts[0] + ":" + parts[1];
            String signature = parts[2];
            if (!sign(payload).equals(signature)) {
                throw new BadRequestException("Pagination cursor failed integrity check");
            }
            return new DecodedCursor(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid pagination cursor");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign pagination cursor", e);
        }
    }
}
