package dev.despical.tikfetch.controller;

import dev.despical.tikfetch.dto.AdminVideoView;
import dev.despical.tikfetch.dto.AttemptView;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.form.LoginForm;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.security.AdminAuthService;
import dev.despical.tikfetch.security.AuthTokens;
import dev.despical.tikfetch.security.CookieService;
import dev.despical.tikfetch.service.download.DownloadedVideoRetentionService;
import dev.despical.tikfetch.service.admin.AdminMetricsService;
import dev.despical.tikfetch.service.RateLimiterService;
import dev.despical.tikfetch.service.admin.SystemInfoService;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
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

    private final AdminAuthService authService;
    private final CookieService cookieService;
    private final RateLimiterService rateLimiterService;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadAttemptRepository attemptRepository;
    private final DownloadedVideoRetentionService retentionService;
    private final VideoViewMapper viewMapper;
    private final SystemInfoService systemInfoService;
    private final AdminMetricsService adminMetricsService;

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
}
