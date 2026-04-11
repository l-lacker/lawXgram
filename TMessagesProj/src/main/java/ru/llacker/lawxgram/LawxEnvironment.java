package ru.llacker.lawxgram;

import org.telegram.messenger.BuildConfig;

import java.util.Collections;
import java.util.List;

import ru.llacker.lawxgram.helpers.UserHelper;
import ru.llacker.lawxgram.helpers.remote.ConfigHelper;

public final class LawxEnvironment {
    private LawxEnvironment() {
    }

    public static int getTelegramApiId() {
        return BuildConfig.LAWX_TELEGRAM_API_ID;
    }

    public static String getTelegramApiHash() {
        return BuildConfig.LAWX_TELEGRAM_API_HASH;
    }

    public static String getPlaystoreAppUrl() {
        return BuildConfig.LAWX_PLAYSTORE_APP_URL.isEmpty() ? "https://github.com/l-lacker/lawXgram/releases" : BuildConfig.LAWX_PLAYSTORE_APP_URL;
    }

    public static boolean isDirectApp() {
        return "release".equals(BuildConfig.BUILD_TYPE) || "debug".equals(BuildConfig.BUILD_TYPE);
    }

    public static boolean forceAnalytics() {
        return BuildConfig.LAWX_FORCE_ANALYTICS;
    }

    public static String getSentryDsn() {
        return BuildConfig.LAWX_SENTRY_DSN;
    }

    public static String getTlViewerUrl() {
        return BuildConfig.LAWX_TLV_URL;
    }

    public static String getTwitterInlineBotUsername() {
        return BuildConfig.LAWX_TWPIC_BOT_USERNAME.isEmpty() ? null : BuildConfig.LAWX_TWPIC_BOT_USERNAME;
    }

    public static List<ConfigHelper.News> getDefaultNews() {
        return Collections.emptyList();
    }

    public static UserHelper.BotInfo getHelperBot() {
        return null;
    }

    public static UserHelper.UserInfoBot getUserInfoBot(boolean fallback) {
        return null;
    }

    public static boolean isTrustedBot(long id) {
        return false;
    }
}
