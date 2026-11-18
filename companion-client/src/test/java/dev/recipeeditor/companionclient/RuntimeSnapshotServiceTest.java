package dev.recipeeditor.companionclient;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("integration")
final class RuntimeSnapshotServiceTest {
    @TempDir Path root;
    @Test void completeSnapshotRoundTripAndPreviousGoodBackup()throws Exception {final var store=new RuntimeSnapshotService();final var first=new RuntimeFixtures().snapshot(root,"session",1);final var path=root.resolve("last.mresnapshot");store.save(path,first);assertEquals(first,store.load(path));final var before=Files.readAllBytes(path);store.save(path,new RuntimeFixtures().snapshot(root,"session",2));assertArrayEquals(before,Files.readAllBytes(root.resolve("last.mresnapshot.bak")));}
    @Test void partialDataCannotReplaceLastCompleteSnapshot()throws Exception {final var store=new RuntimeSnapshotService();final var data=new RuntimeFixtures().snapshot(root,"session",1);final var file=root.resolve("last.mresnapshot");store.save(file,data);final var partial=new dev.recipeeditor.companionprotocol.RuntimeData.Snapshot(1,"partial",false,data.environment(),data.registries(),data.tags(),data.recipes(),data.mods(),data.diagnostics());assertThrows(IOException.class,()->store.save(file,partial));assertEquals(data,store.load(file));}
    @Test void dataAndMetadataCorruptionAreRejected()throws Exception {final var store=new RuntimeSnapshotService();final var data=new RuntimeFixtures().snapshot(root,"session",1);for(final String target:List.of("recipes.jsonl","manifest.json")){final var file=root.resolve(target+".mresnapshot");store.save(file,data);final var entries=new LinkedHashMap<String,byte[]>();try(final var zip=new ZipFile(file.toFile())){for(final var entry:zip.stream().toList())try(final var in=zip.getInputStream(entry)){entries.put(entry.getName(),in.readAllBytes());}}entries.put(target,new String(entries.get(target),java.nio.charset.StandardCharsets.UTF_8).replace(target.equals("manifest.json")?"47.4.10":"minecraft:iron_ingot",target.equals("manifest.json")?"47.4.11":"minecraft:gold_ingot").getBytes(java.nio.charset.StandardCharsets.UTF_8));try(final var zip=new ZipOutputStream(Files.newOutputStream(file))){for(final var entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}assertThrows(IOException.class,()->store.load(file));}}
    @Test void credentialsRoundTripUsesOwnerOnlyFileAndRejectsCorruption()throws Exception {final var c=new dev.recipeeditor.companionprotocol.SessionCredentials(1,"127.0.0.1",12345,dev.recipeeditor.companionprotocol.SessionCredentials.nonce(),"session");final var file=root.resolve("session.json");c.save(file);assertEquals(c,dev.recipeeditor.companionprotocol.SessionCredentials.load(file));Files.writeString(file,"{}");assertThrows(IOException.class,()->dev.recipeeditor.companionprotocol.SessionCredentials.load(file));}
}
