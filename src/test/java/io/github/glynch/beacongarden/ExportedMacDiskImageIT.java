/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the exported macOS disk image after relocating and mounting it outside the source checkout. */
final class ExportedMacDiskImageIT {
    private static final String DISK_IMAGE_PROPERTY = "beaconGarden.diskImage";

    @TempDir
    private Path temporaryDirectory;

    /** Proves structurally that the DMG contains a self-contained application image. */
    @Test
    void containsSelfContainedApplicationInRelocatedDiskImage() throws IOException, InterruptedException {
        Path diskImage = temporaryDirectory.resolve("Beacon Garden-1.0.0.dmg");
        Files.copy(requiredPath(DISK_IMAGE_PROPERTY), diskImage, StandardCopyOption.COPY_ATTRIBUTES);
        Path mountPoint = Files.createDirectory(temporaryDirectory.resolve("mounted-disk-image"));

        requireSuccessfulCommand("verify relocated disk image", "/usr/bin/hdiutil", "verify", diskImage.toString());
        boolean mounted = false;
        try {
            requireSuccessfulCommand(
                    "mount relocated disk image",
                    "/usr/bin/hdiutil",
                    "attach",
                    "-readonly",
                    "-nobrowse",
                    "-mountpoint",
                    mountPoint.toString(),
                    diskImage.toString());
            mounted = true;
            verifyMountedApplication(mountPoint.resolve("Beacon Garden.app"));
        } finally {
            if (mounted) {
                requireSuccessfulCommand(
                        "unmount relocated disk image", "/usr/bin/hdiutil", "detach", mountPoint.toString());
            }
        }
    }

    /** Verifies the application directly on the read-only mounted volume without starting it. */
    private static void verifyMountedApplication(Path image) throws IOException {
        Path applicationRoot = image.resolve("Contents/app");
        Path launcher = image.resolve("Contents/MacOS/Beacon Garden");
        Path runtimeModules = image.resolve("Contents/runtime/Contents/Home/lib/modules");
        Path metadata = applicationRoot.resolve("application-image.properties");
        Path applicationsLink = image.resolveSibling("Applications");

        assertThat(image).isDirectory();
        assertThat(applicationsLink).isSymbolicLink();
        assertThat(Files.readSymbolicLink(applicationsLink)).isEqualTo(Path.of("/Applications"));
        assertThat(launcher).isRegularFile().isExecutable();
        assertThat(runtimeModules).isRegularFile();
        assertThat(applicationRoot.resolve("project/project.json")).isRegularFile();
        assertThat(applicationRoot.resolve("content/imports/garden-import/active-generation"))
                .isRegularFile();
        assertThat(metadata).content(UTF_8).contains("application-name=Beacon Garden", "application-version=1.0.0");
    }

    /** Runs one required macOS disk-image command and includes its output in any assertion failure. */
    private static void requireSuccessfulCommand(String description, String... command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = readOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .withFailMessage("Failed to %s:%n%s", description, output)
                .isZero();
    }

    /** Reads all output from one completed or terminating process. */
    private static String readOutput(Process process) throws IOException {
        try (InputStream output = process.getInputStream()) {
            return new String(output.readAllBytes(), UTF_8);
        }
    }

    /** Resolves one required Maven-supplied path. */
    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is absent: " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
