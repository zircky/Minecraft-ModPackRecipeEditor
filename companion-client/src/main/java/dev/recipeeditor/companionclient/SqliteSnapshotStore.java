package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.ProtocolCodec;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** Stores complete, immutable runtime generations. SQLite publishes a generation on commit. */
public final class SqliteSnapshotStore {
    private static final int APPLICATION_ID = 0x4d524553; // MRES
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_BYTES = 256L * 1024 * 1024;
    private static final List<String> DATASETS = List.of("registry", "tags", "recipes", "mods", "materials");
    private static final com.google.gson.Gson JSON = ProtocolCodec.GSON.newBuilder().serializeNulls().create();
    private final Path cache;

    public SqliteSnapshotStore(final Path cache) {
        this.cache = cache.toAbsolutePath().normalize();
    }

    public Path database(final ModpackFingerprint fingerprint) {
        return cache.resolve(fingerprint.value()).resolve("snapshot.db");
    }

    /** Origin is only a hint for finding stale candidates when the content identity no longer matches. */
    Optional<ModpackFingerprint> previousFor(final Path origin) throws IOException {
        if (Files.notExists(cache)) return Optional.empty();
        ModpackFingerprint previous = null;
        String latest = "";
        try (var entries = Files.newDirectoryStream(cache)) {
            for (final Path entry : entries) {
                final String name = entry.getFileName().toString();
                if (!name.matches("[0-9a-f]{64}")) continue;
                final var candidate = new ModpackFingerprint(name);
                final var environment = latestEnvironment(candidate);
                if (environment.isEmpty()) continue;
                final var evidence = environment.orElseThrow();
                if (isOrigin(origin, evidence.sourceInstance())
                        && evidence.collectedAt().compareTo(latest) > 0) {
                    previous = candidate;
                    latest = evidence.collectedAt();
                }
            }
        }
        return Optional.ofNullable(previous);
    }

    private boolean isOrigin(final Path origin, final String recorded) {
        try { return origin.equals(Path.of(recorded).toRealPath()); }
        catch (IOException | InvalidPathException unavailable) { return false; }
    }

    private Optional<Environment> latestEnvironment(final ModpackFingerprint fingerprint) {
        final Path file = database(fingerprint);
        try {
            checkPath(file);
            if (!Files.isRegularFile(file)) return Optional.empty();
            try (var connection = connect(file, true); var statement = connection.createStatement()) {
                if (pragma(connection, "application_id") != APPLICATION_ID
                        || pragma(connection, "user_version") != SCHEMA_VERSION) return Optional.empty();
                try (var result = statement.executeQuery("SELECT environment FROM snapshots ORDER BY generation DESC LIMIT 1")) {
                    return result.next()
                            ? Optional.of(decode(result.getString(1), Environment.class, ProtocolCodec.MAX_FRAME_BYTES))
                            : Optional.empty();
                }
            }
        } catch (SQLException | IOException | RuntimeException invalidCandidate) {
            return Optional.empty();
        }
    }

    public void save(final ModpackFingerprint fingerprint, final Snapshot snapshot) throws IOException {
        if (!snapshot.complete()) throw new IOException("Cannot publish a partial runtime snapshot");
        final Path file = database(fingerprint);
        checkPath(file);
        Files.createDirectories(file.getParent());
        try (var connection = connect(file, false)) {
            connection.setAutoCommit(false);
            try {
                initialize(connection);
                verify(connection);
                final long generation = insertMetadata(connection, fingerprint, snapshot);
                final var budget = new Budget();
                writeRows(connection, "registry", generation, snapshot.registries(), row -> row.kind() + ":" + row.id(), budget);
                writeRows(connection, "tags", generation, snapshot.tags(), row -> row.kind() + ":" + row.id(), budget);
                writeRows(connection, "recipes", generation, snapshot.recipes(), Recipe::id, budget);
                writeRows(connection, "mods", generation, snapshot.mods(), Mod::id, budget);
                writeRows(connection, "materials", generation, snapshot.materials(), Material::id, budget);
                connection.commit();
            } catch (SQLException | IOException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            throw new IOException("Could not save runtime snapshot database", failure);
        }
    }

    public Optional<Snapshot> loadLatest(final ModpackFingerprint fingerprint) throws IOException {
        final Path file = database(fingerprint);
        checkPath(file);
        if (Files.notExists(file)) return Optional.empty();
        try (var connection = connect(file, true)) {
            connection.setAutoCommit(false);
            verify(connection);
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT * FROM snapshots ORDER BY generation DESC LIMIT 1")) {
                if (!result.next()) return Optional.empty();
                if (!fingerprint.value().equals(result.getString("fingerprint"))) {
                    throw new IOException("Snapshot fingerprint does not match its storage directory");
                }
                final long generation = result.getLong("generation");
                final var budget = new Budget();
                return Optional.of(new Snapshot(result.getInt("dto_schema"), result.getString("snapshot_id"), true,
                        decode(result.getString("environment"), Environment.class, ProtocolCodec.MAX_FRAME_BYTES),
                        readRows(connection, "registry", generation, Registry.class, 200_000, result.getInt("registry_count"), budget),
                        readRows(connection, "tags", generation, Tag.class, 100_000, result.getInt("tag_count"), budget),
                        readRows(connection, "recipes", generation, Recipe.class, 200_000, result.getInt("recipe_count"), budget),
                        readRows(connection, "mods", generation, Mod.class, 4096, result.getInt("mod_count"), budget),
                        List.of(decode(result.getString("diagnostics"), String[].class, ProtocolCodec.MAX_FRAME_BYTES)),
                        readRows(connection, "materials", generation, Material.class, 20_000, result.getInt("material_count"), budget)));
            }
        } catch (SQLException | RuntimeException failure) {
            throw new IOException("Could not read runtime snapshot database", failure);
        }
    }

    private Connection connect(final Path file, final boolean readOnly) throws SQLException {
        final var options = new Properties();
        options.setProperty("foreign_keys", "true");
        options.setProperty("busy_timeout", "5000");
        if (!readOnly) options.setProperty("transaction_mode", "IMMEDIATE");
        return DriverManager.getConnection("jdbc:sqlite:" + file.toUri() + (readOnly ? "?mode=ro" : "?mode=rwc"), options);
    }

    private void initialize(final Connection connection) throws SQLException, IOException {
        if (pragma(connection, "application_id") != 0 || pragma(connection, "user_version") != 0) return;
        try (var statement = connection.createStatement()) {
            try (var tables = statement.executeQuery("SELECT 1 FROM sqlite_schema LIMIT 1")) {
                if (tables.next()) throw new IncompatibleSchema("Unrecognized SQLite database");
            }
            statement.executeUpdate("""
                    CREATE TABLE snapshots (
                      generation INTEGER PRIMARY KEY, fingerprint TEXT NOT NULL,
                      snapshot_id TEXT NOT NULL, dto_schema INTEGER NOT NULL,
                      environment TEXT NOT NULL, diagnostics TEXT NOT NULL,
                      registry_count INTEGER NOT NULL, tag_count INTEGER NOT NULL, recipe_count INTEGER NOT NULL,
                      mod_count INTEGER NOT NULL, material_count INTEGER NOT NULL
                    )
                    """);
            for (final String dataset : DATASETS) {
                statement.executeUpdate("""
                        CREATE TABLE %s (
                          generation INTEGER NOT NULL REFERENCES snapshots(generation),
                          ordinal INTEGER NOT NULL, resource_key TEXT NOT NULL, payload TEXT NOT NULL,
                          PRIMARY KEY (generation, ordinal), UNIQUE (generation, resource_key)
                        ) WITHOUT ROWID
                        """.formatted(dataset));
            }
            statement.executeUpdate("PRAGMA application_id = " + APPLICATION_ID);
            statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    private void verify(final Connection connection) throws SQLException, IOException {
        if (pragma(connection, "application_id") != APPLICATION_ID
                || pragma(connection, "user_version") != SCHEMA_VERSION) {
            throw new IncompatibleSchema("Unrecognized runtime database or unsupported schema version");
        }
        try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA quick_check(1)")) {
            if (!result.next() || !"ok".equals(result.getString(1))) throw new IOException("SQLite integrity check failed");
        }
    }

    private int pragma(final Connection connection, final String name) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA " + name)) {
            if (!result.next()) throw new SQLException("Missing SQLite pragma " + name);
            return result.getInt(1);
        }
    }

    private long insertMetadata(final Connection connection, final ModpackFingerprint fingerprint,
            final Snapshot snapshot) throws SQLException, IOException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO snapshots (fingerprint, snapshot_id, dto_schema, environment, diagnostics,
                    registry_count, tag_count, recipe_count, mod_count, material_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING generation
                """)) {
            statement.setString(1, fingerprint.value());
            statement.setString(2, snapshot.snapshotId());
            statement.setInt(3, snapshot.schemaVersion());
            statement.setString(4, boundedMetadata(snapshot.environment()));
            statement.setString(5, boundedMetadata(snapshot.diagnostics()));
            statement.setInt(6, snapshot.registries().size());
            statement.setInt(7, snapshot.tags().size());
            statement.setInt(8, snapshot.recipes().size());
            statement.setInt(9, snapshot.mods().size());
            statement.setInt(10, snapshot.materials().size());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing new snapshot generation");
                return result.getLong(1);
            }
        }
    }

    private String boundedMetadata(final Object metadata) throws IOException {
        final String json = JSON.toJson(metadata);
        if (json.getBytes(StandardCharsets.UTF_8).length > ProtocolCodec.MAX_FRAME_BYTES) {
            throw new IOException("Snapshot metadata exceeds byte limit");
        }
        return json;
    }

    private <T> void writeRows(final Connection connection, final String table, final long generation,
            final List<T> rows, final Function<T, String> key, final Budget budget) throws SQLException, IOException {
        try (var statement = connection.prepareStatement("INSERT INTO " + table + " VALUES (?, ?, ?, ?)")) {
            for (int index = 0; index < rows.size(); index++) {
                final T row = rows.get(index);
                final String json = JSON.toJson(row);
                budget.add(json);
                statement.setLong(1, generation);
                statement.setInt(2, index);
                statement.setString(3, key.apply(row));
                statement.setString(4, json);
                statement.addBatch();
                if ((index + 1) % 256 == 0) statement.executeBatch();
            }
            statement.executeBatch();
        }
    }

    private <T> List<T> readRows(final Connection connection, final String table, final long generation,
            final Class<T> type, final int limit, final int expectedCount, final Budget budget) throws SQLException, IOException {
        if (expectedCount < 0 || expectedCount > limit) throw new IOException("Invalid row count in " + table);
        final var rows = new ArrayList<T>();
        try (var statement = connection.prepareStatement(
                "SELECT ordinal, payload FROM " + table + " WHERE generation = ? ORDER BY ordinal")) {
            statement.setLong(1, generation);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    if (rows.size() >= expectedCount || result.getInt(1) != rows.size()) {
                        throw new IOException("Invalid record sequence in " + table);
                    }
                    final String json = result.getString(2);
                    budget.add(json);
                    rows.add(decode(json, type, ProtocolCodec.MAX_RECORD_BYTES));
                }
            }
        }
        if (rows.size() != expectedCount) throw new IOException("Missing rows in " + table);
        return rows;
    }

    private <T> T decode(final String json, final Class<T> type, final int limit) throws IOException {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new IOException("Snapshot payload exceeds byte limit");
        return Objects.requireNonNull(JSON.fromJson(ProtocolCodec.parse(bytes), type));
    }

    private void checkPath(final Path file) throws IOException {
        for (Path path = file; path != null; path = path.getParent()) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(path) || !path.toRealPath().equals(path))) {
                throw new IOException("Redirected snapshot database path: " + path);
            }
        }
    }

    private static final class Budget {
        private long total;

        void add(final String json) throws IOException {
            final int bytes = json.getBytes(StandardCharsets.UTF_8).length;
            total += bytes;
            if (bytes > ProtocolCodec.MAX_RECORD_BYTES || total > MAX_BYTES) {
                throw new IOException("Snapshot record or total byte limit exceeded");
            }
        }
    }

    static final class IncompatibleSchema extends IOException {
        IncompatibleSchema(final String message) { super(message); }
    }
}
