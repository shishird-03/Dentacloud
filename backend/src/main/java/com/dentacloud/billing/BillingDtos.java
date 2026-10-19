package com.dentacloud.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** API and sync shapes; field names match the Android Room entities. Money is integer paise. */
public final class BillingDtos {
    private BillingDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvoiceLineDto(UUID id, UUID invoiceId, UUID planItemId, @NotBlank @Size(max = 200) String description,
                                 @Min(1) int qty, @PositiveOrZero long amountPaise, @PositiveOrZero int taxBasisPoints) {}

    /** Server-computed totals; status is derived from the payment ledger. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvoiceDto(UUID id, UUID clinicId, UUID patientId, String invoiceNo, long subtotalPaise,
                             long discountPaise, long taxPaise, long totalPaise, String status, long issuedAt,
                             long paidPaise, List<InvoiceLineDto> lines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewInvoice(UUID id, @NotNull UUID patientId, @PositiveOrZero long discountPaise,
                             @NotEmpty List<@Valid InvoiceLineDto> lines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentDto(UUID id, UUID clinicId, UUID invoiceId, String kind,
                             @NotNull @Pattern(regexp = "CASH|UPI|CARD|BANK") String mode,
                             @Positive long amountPaise, @Size(max = 100) String reference, String idempotencyKey,
                             UUID recordedBy, long recordedAt) {}
}
