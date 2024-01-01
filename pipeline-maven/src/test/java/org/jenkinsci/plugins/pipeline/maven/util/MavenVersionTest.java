package org.jenkinsci.plugins.pipeline.maven.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MavenVersionTest {

    @Test
    public void should_parse_version_from_string() {
        assertThrows(IllegalArgumentException.class, () -> {
            MavenVersion.fromString(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            MavenVersion.fromString("");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            MavenVersion.fromString("not a version");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            MavenVersion.fromString("1.2");
        });

        assertThat(MavenVersion.fromString("3.9.6")).isEqualTo(new MavenVersion(3, 9, 6));
    }

    @Test
    public void should_compare() {
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 9, 7)).isFalse();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 9, 6)).isTrue();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 9, 5)).isTrue();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 8, 8)).isTrue();

        assertThat(new MavenVersion(3, 9, 6).isAtLeast(4, 0)).isFalse();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 10)).isFalse();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 9)).isTrue();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(3, 8)).isTrue();
        assertThat(new MavenVersion(3, 9, 6).isAtLeast(2, 0)).isTrue();
    }
}
