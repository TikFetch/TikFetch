/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.despical.tikfetch.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void missingResourcesUseTheBrandedErrorPage() {
        var model = new ConcurrentModel();
        var handler = new GlobalExceptionHandler();

        String view = handler.noResource(null, model);

        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("title")).isEqualTo("Page not found");
        assertThat(model.getAttribute("message")).isEqualTo("The page you requested does not exist or may have moved.");
    }
}
