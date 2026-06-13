package dev.despical.tikfetch.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record LoginForm(

    @NotBlank(message = "Username is required.")
    @Size(max = 64, message = "Username is too long.")
    String username,

    @NotBlank(message = "Password is required.")
    @Size(max = 256, message = "Password is too long.")
    String password
) {
}
