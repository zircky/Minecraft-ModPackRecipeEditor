package dev.recipeeditor.app;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.app.release.BuildMetadata;
import dev.recipeeditor.companionprotocol.CompanionIdentity;
import dev.recipeeditor.recipeengine.editor.ProjectDocument;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class ReleaseMetadataTest {
    @Test void generatedDesktopAndCompanionIdentitiesMatchTheirRespectiveBuilds() {
        final var build = BuildMetadata.load();
        assertEquals(System.getProperty("expectedDesktopVersion"), build.desktopVersion());
        assertEquals(CompanionIdentity.version(), build.companionVersion());
        assertEquals(1, build.protocolVersion());
        assertEquals(new ProjectDocument(1, Path.of(System.getProperty("user.home")), List.of()).version(), build.projectFormatVersion());
        assertEquals("NOT READY", build.releaseStatus());
        assertTrue(build.diagnosticSummary().contains(build.desktopVersion()));
        assertEquals(build.desktopVersion(), build.applicationInfo().version());
    }

    @Test void allRequiredWindowIconSizesAreBundled() throws Exception {
        for (final int size : new int[]{16, 32, 48, 64, 128, 256}) {
            try (final var input = getClass().getResourceAsStream("/dev/recipeeditor/app/icons/app-" + size + ".png")) {
                assertNotNull(input);
                final var image = ImageIO.read(input);
                assertNotNull(image);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
            }
        }
    }
}
