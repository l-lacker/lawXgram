package ru.llacker.neogram;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import ru.llacker.neogram.config.NeoOptions;

/** Network options (patch feature/prefer-ipv6). */
public final class NeoNetwork {

    private NeoNetwork() {
    }

    /** Connect to Telegram over IPv6 only when the device has an IPv6 address. */
    public static boolean preferIpv6() {
        return NeoOptions.PREFER_IPV6.value();
    }

    public static void setPreferIpv6(boolean prefer) {
        NeoOptions.PREFER_IPV6.set(prefer);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                ConnectionsManager.getInstance(account).checkConnection();
            }
        }
    }
}
