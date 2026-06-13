/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.despical.tikfetch.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.despical.tikfetch.dto.AdminVideoView;
import dev.despical.tikfetch.dto.AttemptView;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.form.LoginForm;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.security.AdminAuthService;
import dev.despical.tikfetch.security.AdminPasskeyService;
import dev.despical.tikfetch.security.AdminPrincipal;
import dev.despical.tikfetch.security.AuthTokens;
import dev.despical.tikfetch.security.CookieService;
import dev.despical.tikfetch.service.download.DownloadedVideoRetentionService;
import dev.despical.tikfetch.service.admin.AdminMetricsService;
import dev.despical.tikfetch.service.RateLimiterService;
import dev.despical.tikfetch.service.admin.SystemInfoService;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String PASSKEY_REGISTRATION_OPTIONS = "PASSKEY_REGISTRATION_OPTIONS";
    private static final String PASSKEY_REGISTRATION_LABEL = "PASSKEY_REGISTRATION_LABEL";
    private static final String PASSKEY_ASSERTION_OPTIONS = "PASSKEY_ASSERTION_OPTIONS";

    private final AdminAuthService authService;
    private final AdminPasskeyService passkeyService;
    private final CookieService cookieService;
    private final RateLimiterService rateLimiterService;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadAttemptRepository attemptRepository;
    private final DownloadedVideoRetentionService retentionService;
    private final VideoViewMapper viewMapper;
    private final SystemInfoService systemInfoService;
    private final AdminMetricsService adminMetricsService;
    private final ObjectMapper objectMapper;

    @GetMapping("/login")
    public String login(Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm("", ""));
        }

        return "admin/login";
    }

    @PostMapping("/login")
    public String login(
        @Valid @ModelAttribute("loginForm")
        LoginForm form,
        BindingResult bindingResult,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        String clientIp = rateLimiterService.getClientIP(request);

        if (!rateLimiterService.tryConsumeLogin(clientIp)) {
            bindingResult.reject("rateLimited", "Too many login attempts. Please wait before trying again.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.loginForm", bindingResult);
            redirectAttributes.addFlashAttribute("loginForm", form);
            return "redirect:/admin/login";
        }

        try {
            AuthTokens tokens = authService.login(form.username(), form.password());
            cookieService.writeAuthCookies(response, tokens);
            return "redirect:/admin";
        } catch (BadCredentialsException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("loginForm", form);
            return "redirect:/admin/login";
        }
    }

    @PostMapping("/refresh")
    public String refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService.readCookie(request, CookieService.REFRESH_COOKIE).orElse(null);
        AuthTokens tokens = authService.refresh(refreshToken);

        cookieService.writeAuthCookies(response, tokens);
        return "redirect:/admin";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService.readCookie(request, CookieService.REFRESH_COOKIE).orElse(null);

        authService.logout(refreshToken);
        cookieService.clearAuthCookies(response);
        return "redirect:/admin/login";
    }

    @PostMapping(value = "/passkeys/login/options", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String startPasskeyLogin(HttpSession session) throws IOException {
        var assertionRequest = passkeyService.startLogin();
        session.setAttribute(PASSKEY_ASSERTION_OPTIONS, assertionRequest.toJson());

        return assertionRequest.toCredentialsGetJson();
    }

    @PostMapping(value = "/passkeys/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> finishPasskeyLogin(
        @RequestBody
        String payload,
        HttpSession session,
        HttpServletResponse response
    ) throws IOException {
        String requestJson = requiredSessionString(session, PASSKEY_ASSERTION_OPTIONS);
        String credentialJson = credentialJson(payload);

        var admin = passkeyService.finishLogin(requestJson, credentialJson);
        AuthTokens tokens = authService.issueTokens(admin);
        cookieService.writeAuthCookies(response, tokens);
        session.removeAttribute(PASSKEY_ASSERTION_OPTIONS);

        return Map.of("redirect", "/admin");
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("systemInfo", systemInfoService.current());
        model.addAttribute("videos", latestAdminVideos());
        model.addAttribute("attempts", latestAttempts());
        return "admin/dashboard";
    }

    @GetMapping("/videos")
    public String videos(Model model) {
        model.addAttribute("videos", latestAdminVideos());
        return "admin/videos";
    }

    @PostMapping("/videos/{id}/delete")
    public String deleteVideo(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        DownloadedVideo video = videoRepository.findById(id).orElse(null);

        if (video == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Video record not found.");
            return "redirect:/admin/videos";
        }

        retentionService.deleteVideoAndFiles(video);

        redirectAttributes.addFlashAttribute("successMessage", "Video record deleted.");
        return "redirect:/admin/videos";
    }

    @GetMapping("/attempts")
    public String attempts(Model model) {
        model.addAttribute("attempts", latestAttempts());
        return "admin/attempts";
    }

    @GetMapping("/system")
    public String system(Model model) {
        model.addAttribute("systemInfo", systemInfoService.current());
        return "admin/system";
    }

    @GetMapping("/metrics")
    public String metrics(Model model) {
        model.addAttribute("metrics", adminMetricsService.current());
        return "admin/metrics";
    }

    @GetMapping("/security")
    public String security(Authentication authentication, Model model) {
        var admin = currentAdmin(authentication);
        model.addAttribute("passkeys", passkeyService.list(admin));
        return "admin/security";
    }

    @PostMapping(value = "/passkeys/register/options", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String startPasskeyRegistration(
        @RequestBody
        String payload,
        Authentication authentication,
        HttpSession session
    ) throws IOException {
        String label = label(payload);
        var options = passkeyService.startRegistration(currentAdmin(authentication));

        session.setAttribute(PASSKEY_REGISTRATION_OPTIONS, options.toJson());
        session.setAttribute(PASSKEY_REGISTRATION_LABEL, label);

        return options.toCredentialsCreateJson();
    }

    @PostMapping(value = "/passkeys/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> finishPasskeyRegistration(
        @RequestBody
        String payload,
        Authentication authentication,
        HttpSession session
    ) throws IOException {
        String requestJson = requiredSessionString(session, PASSKEY_REGISTRATION_OPTIONS);
        String label = (String) session.getAttribute(PASSKEY_REGISTRATION_LABEL);
        passkeyService.finishRegistration(currentAdmin(authentication), requestJson, credentialJson(payload), label);

        session.removeAttribute(PASSKEY_REGISTRATION_OPTIONS);
        session.removeAttribute(PASSKEY_REGISTRATION_LABEL);

        return Map.of("redirect", "/admin/security");
    }

    @PostMapping("/passkeys/{id}/delete")
    public String deletePasskey(
        @PathVariable
        Long id,
        Authentication authentication,
        RedirectAttributes redirectAttributes
    ) {
        passkeyService.delete(currentAdmin(authentication), id);
        redirectAttributes.addFlashAttribute("successMessage", "Passkey removed.");
        return "redirect:/admin/security";
    }

    private List<AdminVideoView> latestAdminVideos() {
        return videoRepository.findAll(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))
            .stream()
            .map(viewMapper::toAdminView)
            .toList();
    }

    private List<AttemptView> latestAttempts() {
        return attemptRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50))
            .stream()
            .map(viewMapper::toAttemptView)
            .toList();
    }

    private dev.despical.tikfetch.entity.Admin currentAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new BadCredentialsException("Admin authentication is required.");
        }

        return principal.admin();
    }

    private String requiredSessionString(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }

        throw new BadCredentialsException("Passkey challenge expired. Please try again.");
    }

    private String credentialJson(String payload) throws IOException {
        JsonNode credential = objectMapper.readTree(payload).path("credential");
        if (credential.isMissingNode() || credential.isNull()) {
            throw new BadCredentialsException("Missing passkey response.");
        }

        return objectMapper.writeValueAsString(credential);
    }

    private String label(String payload) throws IOException {
        return objectMapper.readTree(payload).path("label").asText("Passkey");
    }
}
