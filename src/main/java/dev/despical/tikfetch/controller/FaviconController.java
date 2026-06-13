package dev.despical.tikfetch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Controller
public class FaviconController {

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/favicon.svg";
    }
}
