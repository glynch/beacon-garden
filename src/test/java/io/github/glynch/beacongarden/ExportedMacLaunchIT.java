/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Performs explicitly requested graphical launch checks for exported macOS artifacts. */
final class ExportedMacLaunchIT {
    private static final String APPLICATION_IMAGE_PROPERTY = "beaconGarden.applicationImage";
    private static final String DISK_IMAGE_PROPERTY = "beaconGarden.diskImage";
    private static final long STARTUP_OBSERVATION_SECONDS = 3;
    private static final long TERMINATION_WAIT_SECONDS = 5;

    @TempDir
    private Path temporaryDirectory;

    /** Starts the exported application image and requires it to remain live until the test closes it. */
    @Test
    void launchesApplicationImage() throws IOException, InterruptedException {
        Path launcher = requiredPath(APPLICATION_IMAGE_PROPERTY).resolve("Contents/MacOS/Beacon Garden");

        assertNativeLauncherStarts(launcher, "application image");
    }

    /** Starts the application directly from a relocated, read-only mounted disk image. */
    @Test
    void launchesApplicationFromReadOnlyDiskImage() throws IOException, InterruptedException {
        Path diskImage = temporaryDirectory.resolve("Beacon Garden-1.0.0.dmg");
        Files.copy(requiredPath(DISK_IMAGE_PROPERTY), diskImage, StandardCopyOption.COPY_ATTRIBUTES);
        Path mountPoint = Files.createDirectory(temporaryDirectory.resolve("mounted-disk-image"));

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
            Path launcher = mountPoint.resolve("Beacon Garden.app/Contents/MacOS/Beacon Garden");
            assertNativeLauncherStarts(launcher, "read-only mounted disk image");
        } finally {
            if (mounted) {
                requireSuccessfulCommand(
                        "unmount relocated disk image", "/usr/bin/hdiutil", "detach", mountPoint.toString());
            }
        }
    }

    /** Starts one launcher, observes it running, and then closes it. */
    private static void assertNativeLauncherStarts(Path launcher, String description)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(launcher.toString())
                .directory(Objects.requireNonNull(launcher.getParent(), "launcher parent")
                        .toFile())
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(STARTUP_OBSERVATION_SECONDS, TimeUnit.SECONDS);
        if (exited) {
            String output = readOutput(process);
            fail("%s exited during startup with code %d:%n%s", description, process.exitValue(), output);
        }
        process.destroy();
        if (!process.waitFor(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS);
        }
        assertThat(process.isAlive()).isFalse();
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
