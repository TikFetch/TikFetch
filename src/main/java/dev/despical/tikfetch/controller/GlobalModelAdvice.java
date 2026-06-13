package dev.despical.tikfetch.controller;

import dev.despical.tikfetch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final AppProperties properties;

    @ModelAttribute("app")
    public AppProperties appProperties() {
        return properties;
    }
}
