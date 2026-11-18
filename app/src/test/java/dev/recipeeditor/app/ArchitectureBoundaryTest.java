package dev.recipeeditor.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Checks compiled production references, including descriptors, without an extra test library. */
final class ArchitectureBoundaryTest {
    private final Path root = Path.of(System.getProperty("architectureRoot"));

    @Test
    void businessBytecodeDoesNotReferenceUiOrMinecraftApis() throws Exception {
        for (final String module : List.of("core", "registry", "recipe-engine", "modpack-parser",
                "integrations", "exporters", "recipe-analyzer", "companion-client", "companion-protocol", "companion-server")) {
            final var classes = root.resolve(module + "/build/classes/java/main");
            assertTrue(Files.isDirectory(classes), "Compile production classes before testing: " + module);
            try (final var paths = Files.walk(classes)) {
                for (final var file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    final var constants = utf8Constants(file);
                    for (final String forbidden : List.of("javafx/", "net/minecraft/", "net/minecraftforge/",
                            "dev/recipeeditor/ui/", "dev/recipeeditor/app/")) {
                        assertTrue(constants.stream().noneMatch(value -> value.contains(forbidden)),
                                module + ": forbidden compiled reference " + forbidden + " in " + file.getFileName());
                    }
                }
            }
        }
    }

    @Test
    void protocolAndServerStayJava17Compatible() throws Exception {
        for (final String module : List.of("companion-protocol", "companion-server")) {
            try (final var files = Files.walk(root.resolve(module + "/build/classes/java/main"))) {
                for (final var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    try (final var input = new DataInputStream(Files.newInputStream(file))) {
                        assertEquals(0xCAFEBABE, input.readInt());
                        input.readUnsignedShort();
                        assertEquals(61, input.readUnsignedShort(), module + " must remain Java 17 bytecode");
                    }
                }
            }
        }
    }

    private List<String> utf8Constants(final Path file) throws IOException {
        try (final var input = new DataInputStream(Files.newInputStream(file))) {
            if (input.readInt() != 0xCAFEBABE) throw new IOException("Invalid class file");
            input.readUnsignedShort();
            input.readUnsignedShort();
            final int size = input.readUnsignedShort();
            final var result = new ArrayList<String>();
            for (int i = 1; i < size; i++) {
                switch (input.readUnsignedByte()) {
                    case 1 -> result.add(input.readUTF());
                    case 3, 4 -> input.skipNBytes(4);
                    case 5, 6 -> { input.skipNBytes(8); i++; }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unsupported class constant tag");
                }
            }
            return result;
        }
    }
}
