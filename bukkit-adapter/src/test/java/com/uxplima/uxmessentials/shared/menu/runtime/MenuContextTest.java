package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class MenuContextTest {

    @Test
    void subjectCastsOrThrows() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), "hello", 0);
        assertThat(ctx.subject(String.class)).isEqualTo("hello");
        assertThatThrownBy(() -> ctx.subject(Integer.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withEntryAddsListElement() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 1).withEntry("warpA");
        assertThat(ctx.entry(String.class)).isEqualTo("warpA");
        assertThat(ctx.page()).isEqualTo(1);
    }

    @Test
    void theThreeArgumentFactoryCarriesNoCommandArguments() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
        assertThat(ctx.arguments()).isEmpty();
    }

    @Test
    void argumentsAreCarriedImmutably() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("amount", "5"));
        assertThat(ctx.arguments()).containsEntry("amount", "5");
        assertThatThrownBy(() -> ctx.arguments().put("hacked", "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withPageAndWithEntryPreserveArguments() {
        var base = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("target", "Steve"));

        assertThat(base.withPage(2).arguments()).containsEntry("target", "Steve");
        assertThat(base.withPageCount(4).arguments()).containsEntry("target", "Steve");
        assertThat(base.withEntry("row").arguments()).containsEntry("target", "Steve");
    }

    @Test
    void aFreshContextCarriesNoLocalPlaceholders() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
        assertThat(ctx.localPlaceholders()).isEmpty();
    }

    @Test
    void withLocalPlaceholdersSetsTheMapImmutably() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0)
                .withLocalPlaceholders(Map.of("header", "<gold>The Shop"));

        assertThat(ctx.localPlaceholders()).containsEntry("header", "<gold>The Shop");
        assertThatThrownBy(() -> ctx.localPlaceholders().put("hacked", "x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesPreserveLocalPlaceholders() {
        var base = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0)
                .withLocalPlaceholders(Map.of("header", "Shop"));

        assertThat(base.withPage(2).localPlaceholders()).containsEntry("header", "Shop");
        assertThat(base.withPageCount(4).localPlaceholders()).containsEntry("header", "Shop");
        assertThat(base.withEntry("row").localPlaceholders()).containsEntry("header", "Shop");
    }

    @Test
    void aFreshContextAttributesTheOpenToTheViewer() {
        var viewer = new PlayerRef(UUID.randomUUID(), "Viewer");
        var ctx = MenuContext.of(viewer, null, 0);

        assertThat(ctx.executor())
                .as("a self-open's executor defaults to the viewer")
                .isEqualTo(viewer);
    }

    @Test
    void withExecutorSetsTheOpener() {
        var viewer = new PlayerRef(UUID.randomUUID(), "Target");
        var opener = new PlayerRef(UUID.randomUUID(), "Opener");
        var ctx = MenuContext.of(viewer, null, 0).withExecutor(opener);

        assertThat(ctx.executor()).isEqualTo(opener);
        assertThat(ctx.viewer())
                .as("the viewer is untouched. Only who triggered the open changes")
                .isEqualTo(viewer);
    }

    @Test
    void copiesPreserveTheExecutor() {
        var viewer = new PlayerRef(UUID.randomUUID(), "Target");
        var opener = new PlayerRef(UUID.randomUUID(), "Opener");
        var base = MenuContext.of(viewer, null, 0).withExecutor(opener);

        assertThat(base.withPage(2).executor()).isEqualTo(opener);
        assertThat(base.withPageCount(4).executor()).isEqualTo(opener);
        assertThat(base.withEntry("row").executor()).isEqualTo(opener);
        assertThat(base.withLocalPlaceholders(Map.of("header", "Shop")).executor())
                .isEqualTo(opener);
    }

    @Test
    void withExecutorRejectsNull() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
        assertThatThrownBy(() -> ctx.withExecutor(null)).isInstanceOf(NullPointerException.class);
    }
}
