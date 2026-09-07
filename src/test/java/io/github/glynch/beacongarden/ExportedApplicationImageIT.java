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
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the assembled application using only a relocated runtime image. */
final class ExportedApplicationImageIT {
    private static final String APPLICATION_IMAGE_PROPERTY = "beaconGarden.applicationImage";
    private static final String TEST_CLASSES_PROPERTY = "beaconGarden.testClasses";

    @TempDir
    private Path temporaryDirectory;

    /** Proves that the exported application is complete, relocatable, and free of development-only content. */
    @Test
    void runsRelocatedRuntimeImage() throws IOException, InterruptedException {
        Path relocated = temporaryDirectory.resolve("Beacon Garden");
        copyTree(requiredPath(APPLICATION_IMAGE_PROPERTY), relocated);

        List<String> exportedPaths = relativePaths(relocated);
        Path applicationJar = applicationJar(relocated.resolve("lib"));
        List<String> applicationEntries = jarEntries(applicationJar);
        String launcher = Files.readString(relocated.resolve("bin/beacon-garden"), UTF_8);

        assertThat(exportedPaths)
                .contains("project/project.json", "content/imports/garden-import/active-generation")
                .noneMatch(path -> path.endsWith(".java"))
                .doesNotContain("project/assets/garden.gltf", "project/pom.xml")
                .noneMatch(path -> path.contains("jscene3d-gltf")
                        || path.contains("jgltf")
                        || path.contains("drako")
                        || path.contains("jspecify"));
        assertThat(applicationEntries)
                .contains(
                        "io/github/glynch/beacongarden/BeaconGardenRuntimeExtension.class",
                        "io/github/glynch/beacongarden/GardenBehavior.class",
                        "io/github/glynch/beacongarden/BeaconResponse.class")
                .noneMatch(path -> path.contains("BeaconGardenHeadlessSmoke") || path.endsWith("Test.class"));
        assertThat(launcher)
                .contains("io.github.glynch.jscene3d.project.desktop.DesktopProjectLauncher")
                .doesNotContain("BeaconGardenHeadlessSmoke");

        String output = runHeadlessProbe(relocated, temporaryDirectory);

        assertThat(output)
                .contains(
                        "Loaded project = Beacon Garden",
                        "Pulse status after boundary = ACTIVE",
                        "Entered overlaps = 2",
                        "Physics adapter closed = true, spatial adapter closed = true");
    }

    /** Copies the assembled image so its original repository location cannot satisfy runtime paths. */
    private static void copyTree(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    /** Runs the test-only probe with application and engine classes supplied solely by the relocated image. */
    private static String runHeadlessProbe(Path applicationImage, Path workingDirectory)
            throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classPath = requiredPath(TEST_CLASSES_PROPERTY)
                + System.getProperty("path.separator")
                + applicationImage.resolve("lib/*");
        Process process = new ProcessBuilder(
                        javaExecutable.toString(),
                        "-classpath",
                        classPath,
                        BeaconGardenHeadlessSmoke.class.getName(),
                        applicationImage.resolve("project").toString(),
                        applicationImage.resolve("content").toString())
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream processOutput = process.getInputStream()) {
            output = new String(processOutput.readAllBytes(), UTF_8);
        }
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .withFailMessage("Relocated application failed:%n%s", output)
                .isZero();
        return output;
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

    /** Locates the one application artifact among the exported runtime dependencies. */
    private static Path applicationJar(Path libraryDirectory) throws IOException {
        try (Stream<Path> libraries = Files.list(libraryDirectory)) {
            return libraries
                    .filter(path -> path.getFileName().toString().startsWith("beacon-garden-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("exported application JAR is absent"));
        }
    }

    /** Reads the application artifact index without loading its classes into the test JVM. */
    private static List<String> jarEntries(Path applicationJar) throws IOException {
        try (JarFile jar = new JarFile(applicationJar.toFile())) {
            return jar.stream().map(JarEntry::getName).toList();
        }
    }

    /** Resolves one required Maven-supplied test path. */
    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is absent: " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
