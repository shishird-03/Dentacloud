package com.dentacloud.notification;

import com.dentacloud.shared.Claims;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff devices register their FCM token so the server can ask them to sync. */
@RestController
@RequestMapping("/api/v1/devices")
class DeviceController {
    record FcmToken(@NotBlank @Size(max = 4096) String token) {}

    private final JdbcTemplate jdbc;

    DeviceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void register(@Valid @RequestBody FcmToken body) {
        // A token moves with the device: re-registering under another login or clinic replaces it.
        jdbc.update("delete from device where fcm_token = ?", body.token());
        jdbc.update("insert into device (id, clinic_id, member_id, fcm_token) values (?, ?, ?, ?)",
                Ids.next(), TenantContext.require(), Claims.memberId(), body.token());
    }
}
