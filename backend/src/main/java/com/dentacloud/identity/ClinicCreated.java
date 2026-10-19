package com.dentacloud.identity;

import java.util.UUID;

/** Published inside the signup transaction (tenant already bound) so other modules can seed defaults. */
public record ClinicCreated(UUID clinicId) {}
