package com.dentacloud.app.data.remote;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/** The HTTP client without auth headers or refresh, used for the /auth endpoints. */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Plain {}
