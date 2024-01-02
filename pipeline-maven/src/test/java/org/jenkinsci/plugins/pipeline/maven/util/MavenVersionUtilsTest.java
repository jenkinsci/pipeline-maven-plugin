package org.jenkinsci.plugins.pipeline.maven.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MavenVersionUtilsTest {

    @Test
    public void should_detect_maven_version() {
        assertThat(MavenVersionUtils.containsMavenVersion().test(null)).isFalse();
        assertThat(MavenVersionUtils.containsMavenVersion().test("")).isFalse();
        assertThat(MavenVersionUtils.containsMavenVersion().test("   ")).isFalse();
        assertThat(MavenVersionUtils.containsMavenVersion().test("not a version"))
                .isFalse();
        assertThat(MavenVersionUtils.containsMavenVersion().test("3.8.6")).isFalse();
        assertThat(MavenVersionUtils.containsMavenVersion().test("Apache Maven 3.9.6"))
                .isFalse();

        assertThat(MavenVersionUtils.containsMavenVersion()
                        .test("Apache Maven 3.9.6 (bc0240f3c744dd6b6ec2920b3cd08dcc295161ae)"))
                .isTrue();
    }

    @Test
    public void should_parse_maven_version() {
        assertThat(MavenVersionUtils.parseMavenVersion("Apache Maven 3.9.6 (bc0240f3c744dd6b6ec2920b3cd08dcc295161ae)"))
                .isEqualTo(new MavenVersion(3, 9, 6));
        assertThat(MavenVersionUtils.parseMavenVersion("Apache Maven 3.8.8 (4c87b05d9aedce574290d1acc98575ed5eb6cd39)"))
                .isEqualTo(new MavenVersion(3, 8, 8));
        assertThat(MavenVersionUtils.parseMavenVersion("Apache Maven 3.6.1")).isEqualTo(new MavenVersion(3, 6, 1));
        assertThat(MavenVersionUtils.parseMavenVersion("Apache Maven 3.5.4")).isEqualTo(new MavenVersion(3, 5, 4));
    }
}
