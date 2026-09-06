package com.uxplima.uxmessentials.persistence.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * Loads (or, on first run, creates) the AES key the {@link TotpSecretCipher} encrypts TOTP secrets under. The key
 * is 256 bits of {@link SecureRandom}, Base64-encoded into a small key-file beside the plugin's data (e.g.
 * {@code modules/security/secret.key}), and read back verbatim on later starts, so every server run decrypts the
 * same stored secrets. Keeping the key in a file rather than the config or the database is deliberate: an operator
 * backs up and permissions it separately from the world data a rollback would touch.
 *
 * <p>On a POSIX filesystem the file is <b>created</b> owner-read/write, not created and then tightened: the
 * permissions are part of the create call, so there is no instant, however brief, where another local account can
 * read the key. On a non-POSIX one (Windows) it relies on the directory ACL. The key itself is never logged, and the
 * file is created atomically with {@code CREATE_NEW} so two starts cannot race two different keys into place.
 */
public final class SecurityKeyFile {

    /** 256-bit AES key. */
    private static final int KEY_BYTES = 32;

    private SecurityKeyFile() {}

    /** The key bytes from {@code keyFile}, generating and persisting a new key there if the file is absent. */
    public static byte[] loadOrCreate(Path keyFile) {
        Objects.requireNonNull(keyFile, "keyFile");
        try {
            if (Files.exists(keyFile)) {
                return readExisting(keyFile);
            }
            return createNew(keyFile);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load or create the security key-file at " + keyFile, failure);
        }
    }

    private static byte[] readExisting(Path keyFile) throws IOException {
        byte[] key = Base64.getDecoder()
                .decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim());
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("security key-file holds an invalid AES key length: " + key.length);
        }
        return key;
    }

    private static byte[] createNew(Path keyFile) throws IOException {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        Path parent = keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] encoded = Base64.getEncoder().encodeToString(key).getBytes(StandardCharsets.UTF_8);
        try (SeekableByteChannel channel = Files.newByteChannel(keyFile, createOptions(), ownerOnly(keyFile))) {
            channel.write(ByteBuffer.wrap(encoded));
        }
        return key;
    }

    private static Set<OpenOption> createOptions() {
        return Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /**
     * The attributes the key-file is created with: owner read/write on POSIX, nothing at all elsewhere. Passing the
     * permissions to the create call is the point: a file created world-readable and chmodded afterwards is
     * readable by every local account for the moment in between, which is all a determined one needs.
     */
    private static FileAttribute<?>[] ownerOnly(Path keyFile) {
        boolean posix = keyFile.getFileSystem().supportedFileAttributeViews().contains("posix");
        if (!posix) {
            // Non-POSIX filesystem (Windows): the OS/directory ACL governs access; there is nothing to set here.
            return new FileAttribute<?>[0];
        }
        return new FileAttribute<?>[] {
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        };
    }
}
