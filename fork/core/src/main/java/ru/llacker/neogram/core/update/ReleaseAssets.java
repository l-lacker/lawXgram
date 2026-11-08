package ru.llacker.neogram.core.update;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Files of a Neogram GitHub release: APKs named {@code <App>-<version>-<versionCode>-<abi>.apk}
 * (abi is an Android ABI or {@code universal}, see fork/gradle/neo-app.gradle) and an optional
 * {@code SHA256SUMS} file in {@code sha256sum} format.
 */
public final class ReleaseAssets {

    public static final String UNIVERSAL = "universal";
    public static final String CHECKSUMS = "SHA256SUMS";

    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-fA-F]{64}) [ *]?(.+)$");

    /** One APK of a release. */
    public static final class Apk {
        public final String name;
        public final String versionName;
        public final long versionCode;
        public final String abi;

        Apk(String name, String versionName, long versionCode, String abi) {
            this.name = name;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.abi = abi;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private ReleaseAssets() {
    }

    /** Null when the name is not a Neogram APK of {@code appName}. */
    public static Apk parse(String name, String appName) {
        if (name == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("^" + Pattern.quote(appName) + "-(\\d[0-9A-Za-z.+_-]*?)-(\\d+)-([a-z0-9_-]+)\\.apk$")
                .matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        long versionCode;
        try {
            versionCode = Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Apk(name, matcher.group(1), versionCode, matcher.group(3));
    }

    /**
     * The APK for this device: built for its most preferred ABI ({@code supportedAbis} in the order
     * of {@code Build.SUPPORTED_ABIS}), else the universal one; among equals the highest version.
     * Null when the release has no APK this device can install.
     */
    public static Apk pick(List<String> names, String appName, String[] supportedAbis) {
        Apk best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String name : names) {
            Apk apk = parse(name, appName);
            if (apk == null) {
                continue;
            }
            int rank = rank(apk.abi, supportedAbis);
            if (rank < 0) {
                continue;
            }
            if (best == null || rank < bestRank || rank == bestRank && apk.versionCode > best.versionCode) {
                best = apk;
                bestRank = rank;
            }
        }
        return best;
    }

    private static int rank(String abi, String[] supportedAbis) {
        if (supportedAbis != null) {
            for (int i = 0; i < supportedAbis.length; i++) {
                if (abi.equals(supportedAbis[i])) {
                    return i;
                }
            }
        }
        return UNIVERSAL.equals(abi) ? Integer.MAX_VALUE - 1 : -1;
    }

    /** File name to lower-case SHA-256 from a {@code sha256sum} listing; malformed lines are skipped. */
    public static Map<String, String> parseChecksums(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (String line : text.split("\\r?\\n")) {
            Matcher matcher = CHECKSUM_LINE.matcher(line.trim());
            if (matcher.matches()) {
                result.put(matcher.group(2).trim(), matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
