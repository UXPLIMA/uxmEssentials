package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;

/**
 * A menu's optional {@code bedrock {}} block: an explicit native CustomForm shown to a Bedrock viewer in place of
 * the chest. The form-native widgets (dropdown, slider, toggle, multi-input) the automatic SimpleForm degradation
 * cannot express. On submit each value widget binds its value to a {@code %name%} local placeholder and the
 * {@code onSubmit} action refs run with those values bound; a Java viewer never sees the form and keeps the chest.
 *
 * <p>Pure and Bukkit-free like the rest of the spec model: the title/content and every widget string are carried
 * verbatim for the renderer to resolve per open (they may hold a {@code %token%} or {@code @key}), and the
 * {@code onSubmit} refs are parsed by the same ref parser the click actions use.
 *
 * @param title the form title (may carry {@code %token%}/{@code @key}); never {@code null}
 * @param content optional intro text shown above the widgets, or {@code null} for none
 * @param widgets the form widgets in order; never {@code null}, copied defensively
 * @param onSubmit the action refs run when the viewer submits, with the widget values bound; never {@code null}
 */
public record BedrockFormSpec(String title, @Nullable String content, List<BedrockWidget> widgets, List<Ref> onSubmit) {

    public BedrockFormSpec {
        Objects.requireNonNull(title, "title");
        widgets = List.copyOf(Objects.requireNonNull(widgets, "widgets"));
        onSubmit = List.copyOf(Objects.requireNonNull(onSubmit, "onSubmit"));
    }
}
