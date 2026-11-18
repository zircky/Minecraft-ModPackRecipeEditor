package dev.recipeeditor.companionprotocol;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public record SessionCredentials(int protocolVersion, String host, int port, String token, String sessionId) {
    public SessionCredentials {
        if(protocolVersion!=1||!"127.0.0.1".equals(host)||port<1||port>65535||token==null||!token.matches("[a-f0-9]{64}")||sessionId==null||!sessionId.matches("[a-zA-Z0-9-]{1,64}"))throw new IllegalArgumentException("Invalid session credentials");
    }
    @Override public String toString(){return "Companion session "+sessionId+" on localhost:"+port+" (token redacted)";}
    public static String nonce(){final var bytes=new byte[32];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    public String proof(final String role,final String clientNonce,final String serverNonce) {
        if(!clientNonce.matches("[a-f0-9]{64}")||!serverNonce.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid nonce");
        try{final var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(HexFormat.of().parseHex(token),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((role+"|"+sessionId+"|"+clientNonce+"|"+serverNonce).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(GeneralSecurityException bad){throw new IllegalStateException(bad);}
    }
    public boolean matches(final String expected,final String actual){return actual!=null&&MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public void save(final Path file)throws IOException {
        final var parent=file.toAbsolutePath().getParent();for(Path existing=parent;existing!=null;existing=existing.getParent())if(Files.exists(existing,LinkOption.NOFOLLOW_LINKS))rejectLinks(existing);Files.createDirectories(parent);rejectLinks(parent);if(Files.isSymbolicLink(file))throw new IOException("Credentials symlink");
        final var temp=Files.createTempFile(parent,".mre-session-",".tmp");
        try {restrict(temp);Files.writeString(temp,ProtocolCodec.GSON.toJson(this));try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException noAtomic){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}restrict(file);}finally{Files.deleteIfExists(temp);}
    }
    public static SessionCredentials load(final Path file)throws IOException {
        rejectLinks(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>8192)throw new IOException("Invalid session file");
        try{return ProtocolCodec.GSON.fromJson(ProtocolCodec.parse(Files.readAllBytes(file)),SessionCredentials.class);}catch(RuntimeException bad){throw new ProtocolException("Invalid session credentials");}
    }
    private static void rejectLinks(final Path path)throws IOException {for(Path p=path.toAbsolutePath();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Unexpected symbolic link");if(!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new IOException("Unexpected path redirection");}
    private static void restrict(final Path file)throws IOException {
        final var posix=Files.getFileAttributeView(file,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null){posix.setPermissions(Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));return;}
        final var acl=Files.getFileAttributeView(file,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl==null)throw new IOException("Owner-only credentials permissions unavailable");
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(file)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
