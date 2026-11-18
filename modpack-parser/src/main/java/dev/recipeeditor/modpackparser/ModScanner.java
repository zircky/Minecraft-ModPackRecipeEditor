package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import dev.recipeeditor.core.importing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.*;

/** Inspects exact archive entries; never loads classes or extracts archives. */
public final class ModScanner {
    private final ImportLimits limits;
    private final List<ModMetadataParser> parsers;
    private final List<String> metadataPaths = List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "fabric.mod.json", "quilt.mod.json");
    public ModScanner(final ImportLimits limits, final List<ModMetadataParser> parsers) {
        this.limits = Objects.requireNonNull(limits); this.parsers = List.copyOf(parsers);
    }
    public ModScanner() { this(new ImportLimits(), List.of(new ForgeMetadataParser(), new NeoForgeMetadataParser(), new FabricMetadataParser(), new QuiltMetadataParser())); }

    public ModScanResult scan(final Path root, final Path jar, final ReadBudget budget) {
        final var diagnostics = new ArrayList<ImportDiagnostic>();
        final var metadata = new ArrayList<ModMetadata>();
        try {
            SafeFiles.checkCancelled();
            final var safe = SafeFiles.inside(root,jar);
            if (!Files.isRegularFile(safe) || !Files.isReadable(safe) || Files.size(safe) > limits.maxJarBytes())
                throw new IOException("JAR is inaccessible or exceeds size limit");
            try (final var zip = new ZipFile(safe.toFile())) {
                if (zip.size() > limits.maxEntries()) throw new IOException("JAR exceeds archive entry limit");
                Map<String,String> attributes;
                try { attributes = manifest(zip,budget); }
                catch (IOException error) {
                    attributes = Map.of();
                    diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"META-INF/MANIFEST.MF","Cannot read manifest: " + error.getMessage()));
                }
                boolean found = false;
                for (final var path : metadataPaths) {
                    SafeFiles.checkCancelled();
                    final var entry = zip.getEntry(path);
                    if (entry == null) continue;
                    found = true;
                    final var parser = parsers.stream().filter(p -> p.supports(path)).findFirst();
                    if (parser.isEmpty()) continue;
                    try {
                        final byte[] bytes = readEntry(zip,entry,budget);
                        final var parsed = parser.get().parse(new ByteArrayInputStream(bytes), new MetadataContext(path,attributes));
                        metadata.addAll(parsed.mods()); diagnostics.addAll(parsed.diagnostics());
                    } catch (IOException error) { diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR,path,error.getMessage())); }
                }
                if (!found) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,jar.toString(),"No supported mod metadata. This may be a library or legacy mod."));
                if (zip.getEntry("META-INF/jarjar/metadata.json") != null) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,jar.toString(),"Jar-in-Jar dependencies are not scanned; dependency presence may be incomplete"));
            }
        } catch (IOException | IllegalArgumentException error) {
            diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR, jar.toString(), "Cannot scan JAR: " + error.getMessage()));
        }
        final var status = diagnostics.stream().anyMatch(d -> d.severity() == ScanStatus.ERROR) ? ScanStatus.ERROR
                : diagnostics.isEmpty() ? ScanStatus.OK : ScanStatus.WARNING;
        final var contextual = diagnostics.stream().map(d -> new ImportDiagnostic(d.severity(),
                jar + "!" + d.source(),d.message())).toList();
        final var mods = metadata.stream().map(m -> new ModDefinition(m, jar, status, contextual)).toList();
        return new ModScanResult(jar,mods,status,contextual);
    }
    private Map<String,String> manifest(final ZipFile zip, final ReadBudget budget) throws IOException {
        final var entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) return Map.of();
        final var manifest = new Manifest(new ByteArrayInputStream(readEntry(zip,entry,budget)));
        final var result = new HashMap<String,String>();
        manifest.getMainAttributes().forEach((key,value) -> result.put(key.toString(),value.toString()));
        return result;
    }
    private byte[] readEntry(final ZipFile zip, final ZipEntry entry, final ReadBudget budget) throws IOException {
        if (entry.getSize() > limits.maxMetadataBytes()) throw new IOException("Metadata exceeds size limit");
        if (entry.getSize() > 65536 && entry.getCompressedSize() > 0 && entry.getSize() / entry.getCompressedSize() > 200)
            throw new IOException("Suspicious metadata compression ratio");
        try (final var input = zip.getInputStream(entry)) {
            final var output = new ByteArrayOutputStream();
            final var buffer = new byte[8192];
            while (true) {
                SafeFiles.checkCancelled();
                final int count = input.read(buffer,0,Math.min(buffer.length,budget.remaining()));
                if (count == -1) return output.toByteArray();
                budget.consume(count);
                if (output.size() + count > limits.maxMetadataBytes()) throw new IOException("Metadata exceeds size limit");
                output.write(buffer,0,count);
            }
        }
    }
    /** Per-import mutable budget; confined to the import worker. */
    public static final class ReadBudget {
        private long remaining;
        public ReadBudget(final long limit) { if (limit < 1) throw new IllegalArgumentException(); remaining = limit; }
        int remaining() throws IOException {
            if (remaining <= 0) throw new IOException("Total metadata budget exhausted");
            return (int)Math.min(Integer.MAX_VALUE,remaining);
        }
        void consume(final int count) { remaining -= count; }
        public boolean exhausted() { return remaining <= 0; }
    }
}
