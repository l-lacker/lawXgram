package ru.llacker.lawxgram.helpers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;
import java.util.function.Consumer;

import ru.llacker.lawxgram.settings.BaseLawxSettingsActivity;
import ru.llacker.lawxgram.settings.LawxAppearanceSettingsActivity;
import ru.llacker.lawxgram.settings.LawxChatSettingsActivity;
import ru.llacker.lawxgram.settings.LawxDonateActivity;
import ru.llacker.lawxgram.settings.LawxEmojiSettingsActivity;
import ru.llacker.lawxgram.settings.LawxExperimentalSettingsActivity;
import ru.llacker.lawxgram.settings.LawxGeneralSettingsActivity;
import ru.llacker.lawxgram.settings.LawxPasscodeSettingsActivity;

public class SettingsHelper {
    public static final String SETTINGS_DEEPLINK_SLUG = "lawxsettings";
    private static final String LEGACY_SETTINGS_DEEPLINK_SLUG = "nekosettings";

    public static boolean isSettingsDeepLink(String firstSegment) {
        return SETTINGS_DEEPLINK_SLUG.equalsIgnoreCase(firstSegment) || LEGACY_SETTINGS_DEEPLINK_SLUG.equalsIgnoreCase(firstSegment);
    }

    public static String createSettingsLink(String linkPrefix, String key, String row) {
        String baseLink = String.format(Locale.ENGLISH, "https://%s/%s/%s", linkPrefix, SETTINGS_DEEPLINK_SLUG, key);
        if (TextUtils.isEmpty(row)) {
            return baseLink;
        }
        return baseLink + "?r=" + Uri.encode(row);
    }

    public static void processDeepLink(Uri uri, Consumer<BaseFragment> callback, Runnable unknown, Browser.Progress progress) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments == null || segments.size() != 2 || !isSettingsDeepLink(segments.get(0))) {
            unknown.run();
            return;
        }
        BaseLawxSettingsActivity fragment;
        var segment = segments.get(1);
        if (PasscodeHelper.getSettingsKey().equals(segment)) {
            fragment = new LawxPasscodeSettingsActivity();
        } else {
            switch (segment.toLowerCase(Locale.US)) {
                case "appearance":
                case "a":
                    fragment = new LawxAppearanceSettingsActivity();
                    break;
                case "chat":
                case "chats":
                case "c":
                    fragment = new LawxChatSettingsActivity();
                    break;
                case "donate":
                case "d":
                    fragment = new LawxDonateActivity();
                    break;
                case "experimental":
                case "e":
                    fragment = new LawxExperimentalSettingsActivity();
                    break;
                case "emoji":
                    fragment = new LawxEmojiSettingsActivity();
                    break;
                case "general":
                case "g":
                    fragment = new LawxGeneralSettingsActivity();
                    break;
                case "reportid":
                    SettingsHelper.copyReportId();
                    return;
                case "update":
                    LaunchActivity.instance.checkAppUpdate(true, progress);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.accept(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(rowFinal, unknown));
        }
    }

    public static void copyReportId() {
        AndroidUtilities.addToClipboard(AnalyticsHelper.userId);
        BulletinFactory.global().createSimpleBulletin(R.raw.copy, LocaleController.getString(R.string.TextCopied), LocaleController.getString(R.string.CopyReportIdDescription)).show();
    }

    public static void restartApplication(Activity activity) {
        if (activity == null) {
            return;
        }
        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (launchIntent == null) {
            return;
        }
        Intent restartIntent = launchIntent.getComponent() != null
            ? Intent.makeRestartActivityTask(launchIntent.getComponent())
            : new Intent(launchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.finishAffinity();
        activity.startActivity(restartIntent);
        System.exit(0);
    }
}
