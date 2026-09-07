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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the native macOS image using only a relocated application bundle. */
final class ExportedMacApplicationImageIT {
    private static final String APPLICATION_IMAGE_PROPERTY = "beaconGarden.applicationImage";
    private static final long STARTUP_OBSERVATION_SECONDS = 3;
    private static final long TERMINATION_WAIT_SECONDS = 5;

    @TempDir
    private Path temporaryDirectory;

    /** Proves that the native launcher, bundled runtime, application code, and runtime data are self-contained. */
    @Test
    void launchesRelocatedNativeImage() throws IOException, InterruptedException {
        Path image = temporaryDirectory.resolve("Beacon Garden.app");
        copyTree(requiredPath(APPLICATION_IMAGE_PROPERTY), image);
        Path applicationRoot = image.resolve("Contents/app");
        Path launcher = image.resolve("Contents/MacOS/Beacon Garden");
        Path runtimeModules = image.resolve("Contents/runtime/Contents/Home/lib/modules");
        Path configuration = applicationRoot.resolve("Beacon Garden.cfg");
        List<String> exportedPaths = relativePaths(image);
        List<String> applicationEntries = jarEntries(applicationJar(applicationRoot));

        assertThat(exportedPaths)
                .contains(
                        "Contents/MacOS/Beacon Garden",
                        "Contents/app/project/project.json",
                        "Contents/app/project/LICENSE",
                        "Contents/app/content/imports/garden-import/active-generation",
                        "Contents/runtime/Contents/Home/lib/modules")
                .noneMatch(path -> path.endsWith(".java"))
                .doesNotContain("Contents/app/project/assets/garden.gltf", "Contents/app/project/pom.xml")
                .noneMatch(path -> path.contains("jscene3d-gltf")
                        || path.contains("jscene3d-project-export")
                        || path.contains("jgltf")
                        || path.contains("drako")
                        || path.contains("jspecify"));
        assertThat(applicationEntries)
                .contains(
                        "io/github/glynch/beacongarden/BeaconGardenRuntimeExtension.class",
                        "io/github/glynch/beacongarden/GardenBehavior.class",
                        "io/github/glynch/beacongarden/BeaconResponse.class")
                .noneMatch(path -> path.contains("BeaconGardenHeadlessSmoke") || path.endsWith("Test.class"));
        assertThat(configuration)
                .content(UTF_8)
                .contains(
                        "app.mainclass=io.github.glynch.jscene3d.project.desktop.DesktopProjectLauncher",
                        "java-options=-XstartOnFirstThread",
                        "java-options=-Djscene3d.launch.engine.version=0.1.0-SNAPSHOT",
                        "java-options=-Djscene3d.launch.project.directory=$APPDIR/project",
                        "java-options=-Djscene3d.launch.content.directory=$APPDIR/content")
                .doesNotContain("BeaconGardenHeadlessSmoke", "development/projects/beacon-garden");
        assertThat(launcher).isRegularFile().isExecutable();
        assertThat(runtimeModules).isRegularFile();

        assertNativeLauncherStarts(launcher);
    }

    /** Starts the relocated bundle and requires it to remain live until the test closes it. */
    private static void assertNativeLauncherStarts(Path launcher) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(launcher.toString())
                .directory(Objects.requireNonNull(launcher.getParent(), "launcher parent")
                        .toFile())
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(STARTUP_OBSERVATION_SECONDS, TimeUnit.SECONDS);
        if (exited) {
            String output = readOutput(process);
            fail("Relocated native application exited during startup with code %d:%n%s", process.exitValue(), output);
        }
        process.destroy();
        if (!process.waitFor(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS);
        }
        assertThat(process.isAlive()).isFalse();
    }

    /** Reads all native-launcher output after its process has exited. */
    private static String readOutput(Process process) throws IOException {
        try (InputStream output = process.getInputStream()) {
            return new String(output.readAllBytes(), UTF_8);
        }
    }

    /** Copies a complete application bundle while preserving runtime symbolic links. */
    private static void copyTree(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isSymbolicLink(path)) {
                    Files.createDirectories(target.getParent());
                    Files.createSymbolicLink(target, Files.readSymbolicLink(path));
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    /** Returns every path below the application image using portable separators. */
    private static List<String> relativePaths(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> !path.equals(root))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .toList();
        }
    }

    /** Locates the one Beacon Garden application artifact in the native image. */
    private static Path applicationJar(Path applicationRoot) throws IOException {
        try (Stream<Path> paths = Files.list(applicationRoot)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("beacon-garden-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("packaged Beacon Garden JAR is absent"));
        }
    }

    /** Reads the application artifact index without loading its classes into the test JVM. */
    private static List<String> jarEntries(Path applicationJar) throws IOException {
        try (JarFile jar = new JarFile(applicationJar.toFile())) {
            return jar.stream().map(JarEntry::getName).toList();
        }
    }

    /** Resolves one required Maven-supplied image path. */
    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is absent: " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
