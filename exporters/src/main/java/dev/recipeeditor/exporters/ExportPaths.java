package dev.recipeeditor.exporters;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
final class ExportPaths {
    private ExportPaths(){}
    static String relative(final String value) {
        if(value.isBlank()||value.startsWith("/")||value.contains("\\")||value.contains(":"))throw new IllegalArgumentException("Unsafe output path: "+value);
        for(final String part:value.split("/",-1)) {
            final String device=part.split("\\.",2)[0].toUpperCase(Locale.ROOT);
            if(part.isEmpty()||part.equals(".")||part.equals("..")||part.length()>200||part.endsWith(".")||part.endsWith(" ")||part.matches(".*[<>|?*\\x00-\\x1f].*")||device.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IllegalArgumentException("Unsafe Windows output path: "+value);
        }return value;
    }
    static void managed(final String profileId,final String value) {
        relative(value);
        if(!profileId.matches("[a-z0-9]{8,32}") || !(value.startsWith("datapacks/recipe_editor_"+profileId+"/")&&(value.endsWith(".json")||value.endsWith("/pack.mcmeta"))
                || value.equals("kubejs/server_scripts/recipe_editor_"+profileId+"_generated.js") || value.equals("scripts/recipe_editor_"+profileId+"_generated.zs")))
            throw new IllegalArgumentException("File is outside this profile's generated namespace: "+value);
    }
    static Path inside(final Path realRoot,final String relative) throws IOException {
        final var path=realRoot.resolve(relative(relative)).normalize();
        if(!path.startsWith(realRoot))throw new IOException("Path escapes export directory");
        Path current=realRoot;
        for(final var part:realRoot.relativize(path)) {
            current=current.resolve(part);
            if(Files.isSymbolicLink(current))throw new IOException("Symbolic link in export path: "+current);
            if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)&&!current.toRealPath().equals(current.toAbsolutePath().normalize()))throw new IOException("Junction/reparse redirection in export path: "+current);
        }return path;
    }
    static String hash(final Path file) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>48L*1024*1024)throw new IOException("Output is not a bounded regular file: "+file);
        try(final var input=Files.newInputStream(file)){return ExportText.hash(dev.recipeeditor.core.io.SafeFiles.read(input,48*1024*1024));}
    }
}
