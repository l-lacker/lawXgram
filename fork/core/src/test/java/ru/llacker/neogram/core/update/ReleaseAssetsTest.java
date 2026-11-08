package ru.llacker.neogram.core.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ReleaseAssetsTest {

    private static final List<String> RELEASE = Arrays.asList(
            "Neogram-13.2.1-72911-arm64-v8a.apk",
            "Neogram-13.2.1-72911-armeabi-v7a.apk",
            "Neogram-13.2.1-72911-x86_64.apk",
            "Neogram-13.2.1-72911-universal.apk",
            "SHA256SUMS",
            "mapping.txt");

    @Test
    public void parsesApkNames() {
        ReleaseAssets.Apk apk = ReleaseAssets.parse("Neogram-13.2.1-72911-arm64-v8a.apk", "Neogram");
        assertEquals("13.2.1", apk.versionName);
        assertEquals(72911L, apk.versionCode);
        assertEquals("arm64-v8a", apk.abi);
        assertEquals("x86_64", ReleaseAssets.parse("Neogram-13.2.1-72911-x86_64.apk", "Neogram").abi);
        assertNull(ReleaseAssets.parse("Neogram-13.2.1-72911-arm64-v8a.apk", "Other"));
        assertNull(ReleaseAssets.parse("Neogram-13.2.1-arm64-v8a.apk", "Neogram"));
        assertNull(ReleaseAssets.parse("SHA256SUMS", "Neogram"));
        assertNull(ReleaseAssets.parse(null, "Neogram"));
    }

    @Test
    public void picksThePreferredAbi() {
        assertEquals("Neogram-13.2.1-72911-arm64-v8a.apk",
                ReleaseAssets.pick(RELEASE, "Neogram", new String[]{"arm64-v8a", "armeabi-v7a", "armeabi"}).name);
        assertEquals("Neogram-13.2.1-72911-armeabi-v7a.apk",
                ReleaseAssets.pick(RELEASE, "Neogram", new String[]{"armeabi-v7a", "armeabi"}).name);
    }

    @Test
    public void fallsBackToUniversal() {
        assertEquals("Neogram-13.2.1-72911-universal.apk",
                ReleaseAssets.pick(RELEASE, "Neogram", new String[]{"riscv64"}).name);
    }

    @Test
    public void nothingToInstall() {
        assertNull(ReleaseAssets.pick(Arrays.asList("Neogram-13.2.1-72911-x86.apk", "notes.txt"), "Neogram",
                new String[]{"arm64-v8a"}));
    }

    @Test
    public void parsesChecksums() {
        String hash = "0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef";
        Map<String, String> sums = ReleaseAssets.parseChecksums(hash + "  Neogram-13.2.1-72911-arm64-v8a.apk\r\n"
                + hash + " *Neogram-13.2.1-72911-universal.apk\nbroken line\n");
        assertEquals(2, sums.size());
        assertEquals(hash.toLowerCase(), sums.get("Neogram-13.2.1-72911-arm64-v8a.apk"));
        assertEquals(hash.toLowerCase(), sums.get("Neogram-13.2.1-72911-universal.apk"));
        assertEquals(0, ReleaseAssets.parseChecksums(null).size());
    }
}
