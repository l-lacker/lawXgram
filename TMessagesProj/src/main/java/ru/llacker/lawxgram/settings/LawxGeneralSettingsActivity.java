package ru.llacker.lawxgram.settings;

import android.text.TextUtils;
import android.view.View;

import androidx.core.text.HtmlCompat;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Locale;

import ru.llacker.lawxgram.LawxConfig;
import ru.llacker.lawxgram.helpers.PopupHelper;
import ru.llacker.lawxgram.translator.Translator;
import ru.llacker.lawxgram.translator.TranslatorApps;

public class LawxGeneralSettingsActivity extends BaseLawxSettingsActivity {

    private final int ipv6Row = rowId++;

    private final int showOriginalRow = rowId++;
    private final int translatorTypeRow = rowId++;
    private final int translatorExternalAppRow = rowId++;
    private final int translationProviderRow = rowId++;
    private final int translationTargetRow = rowId++;
    private final int doNotTranslateRow = rowId++;
    private final int autoTranslateRow = rowId++;

    private final int accentAsNotificationColorRow = rowId++;
    private final int silenceNonContactsRow = rowId++;

    private final int nameOrderRow = rowId++;
    private final int idTypeRow = rowId++;

    private final int disabledInstantCameraRow = rowId++;
    private final int showGalleryCameraRow = rowId++;
    private final int askBeforeCallRow = rowId++;
    private final int openArchiveOnPullRow = rowId++;

    private CharSequence getTranslationProvider() {
        var providers = Translator.getProviders();
        var names = providers.first;
        var types = providers.second;
        if (names == null || types == null) {
            return "";
        }
        int index = types.indexOf(LawxConfig.translationProvider);
        if (index < 0) {
            return "";
        } else {
            return names.get(index);
        }
    }

    private CharSequence getTranslationTarget() {
        var language = LawxConfig.translationTarget;
        CharSequence value;
        if (language.equals("app")) {
            value = LocaleController.getString(R.string.TranslationTargetApp);
        } else {
            Locale locale = Locale.forLanguageTag(language);
            if (!TextUtils.isEmpty(locale.getScript())) {
                value = HtmlCompat.fromHtml(locale.getDisplayScript(), HtmlCompat.FROM_HTML_MODE_LEGACY);
            } else {
                value = locale.getDisplayName();
            }
        }
        return value;
    }

    private CharSequence getRestrictedLanguages() {
        var langCodes = Translator.getRestrictedLanguages();
        CharSequence value;
        if (langCodes.size() == 1) {
            Locale locale = Locale.forLanguageTag(langCodes.get(0));
            if (!TextUtils.isEmpty(locale.getScript())) {
                value = HtmlCompat.fromHtml(locale.getDisplayScript(), HtmlCompat.FROM_HTML_MODE_LEGACY);
            } else {
                value = locale.getDisplayName();
            }
        } else {
            value = LocaleController.formatPluralString("Languages", langCodes.size());
        }
        return value;
    }

    private CharSequence getTranslatorType() {
        return switch (LawxConfig.transType) {
            case LawxConfig.TRANS_TYPE_TG -> LocaleController.getString(R.string.TranslatorTypeTG);
            case LawxConfig.TRANS_TYPE_EXTERNAL ->
                    LocaleController.getString(R.string.TranslatorTypeExternal);
            default -> LocaleController.getString(R.string.TranslatorTypeLawx);
        };
    }

    private CharSequence getTranslatorExternalApp() {
        var app = TranslatorApps.getTranslatorApp();
        return app == null ? "" : app.title;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.Connection)));
        items.add(UItem.asCheck(ipv6Row, LocaleController.getString(R.string.PreferIPv6)).slug("ipv6").setChecked(LawxConfig.preferIPv6));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.Translator)));
        items.add(TextSettingsCellFactory.of(translatorTypeRow, LocaleController.getString(R.string.TranslatorType), getTranslatorType()).slug("translatorType"));
        if (LawxConfig.transType != LawxConfig.TRANS_TYPE_EXTERNAL) {
            if (LawxConfig.transType == LawxConfig.TRANS_TYPE_LAWX) {
                items.add(UItem.asCheck(showOriginalRow, LocaleController.getString(R.string.TranslatorShowOriginal)).slug("showOriginalRow").setChecked(LawxConfig.showOriginal));
            }
            items.add(TextSettingsCellFactory.of(translationProviderRow, LocaleController.getString(R.string.TranslationProviderShort), getTranslationProvider()).slug("translationProvider"));
            items.add(TextSettingsCellFactory.of(translationTargetRow, LocaleController.getString(R.string.TranslationTarget), getTranslationTarget()).slug("translationTarget"));
            items.add(TextSettingsCellFactory.of(doNotTranslateRow, LocaleController.getString(R.string.DoNotTranslate), getRestrictedLanguages()).slug("doNotTranslate"));
            items.add(UItem.asCheck(autoTranslateRow, LocaleController.getString(R.string.AutoTranslate), LocaleController.getString(R.string.AutoTranslateAbout)).slug("autoTranslate").setChecked(LawxConfig.autoTranslate));
        } else {
            items.add(TextSettingsCellFactory.of(translatorExternalAppRow, LocaleController.getString(R.string.TranslationProviderShort), getTranslatorExternalApp()).slug("translatorExternalApp"));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.TranslateMessagesInfo1)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.Notifications)));
        items.add(UItem.asCheck(accentAsNotificationColorRow, LocaleController.getString(R.string.AccentAsNotificationColor)).slug("accentAsNotificationColor").setChecked(LawxConfig.accentAsNotificationColor));
        items.add(UItem.asCheck(silenceNonContactsRow, LocaleController.getString(R.string.SilenceNonContacts)).slug("silenceNonContacts").setChecked(LawxConfig.silenceNonContacts));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SilenceNonContactsAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.UserColorTabProfile)));
        items.add(TextSettingsCellFactory.of(nameOrderRow, LocaleController.getString(R.string.NameOrder), switch (LawxConfig.nameOrder) {
            case 2 -> LocaleController.getString(R.string.LastFirst);
            default -> LocaleController.getString(R.string.FirstLast);
        }).slug("nameOrder"));
        items.add(TextSettingsCellFactory.of(idTypeRow, LocaleController.getString(R.string.IdType), switch (LawxConfig.idType) {
            case LawxConfig.ID_TYPE_HIDDEN -> LocaleController.getString(R.string.IdTypeHidden);
            case LawxConfig.ID_TYPE_BOTAPI -> LocaleController.getString(R.string.IdTypeBOTAPI);
            default -> LocaleController.getString(R.string.IdTypeAPI);
        }).slug("idType"));
        items.add(UItem.asShadow(LocaleController.getString(R.string.IdTypeAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.General)));
        items.add(UItem.asCheck(disabledInstantCameraRow, LocaleController.getString(R.string.DisableInstantCamera)).slug("disabledInstantCamera").setChecked(LawxConfig.disableInstantCamera));
        items.add(UItem.asCheck(showGalleryCameraRow, LocaleController.getString(R.string.ShowGalleryCamera)).slug("showGalleryCamera").setChecked(LawxConfig.showGalleryCamera));
        items.add(UItem.asCheck(askBeforeCallRow, LocaleController.getString(R.string.AskBeforeCalling)).slug("askBeforeCall").setChecked(LawxConfig.askBeforeCall));
        items.add(UItem.asCheck(openArchiveOnPullRow, LocaleController.getString(R.string.OpenArchiveOnPull)).slug("openArchiveOnPull").setChecked(LawxConfig.openArchiveOnPull));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        var id = item.id;
        if (id == ipv6Row) {
            LawxConfig.toggleIPv6();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.preferIPv6);
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.getInstance(a).checkConnection();
                }
            }
        } else if (id == disabledInstantCameraRow) {
            LawxConfig.toggleDisabledInstantCamera();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.disableInstantCamera);
            }
        } else if (id == showGalleryCameraRow) {
            LawxConfig.toggleShowGalleryCamera();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.showGalleryCamera);
            }
        } else if (id == nameOrderRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.FirstLast));
            types.add(1);
            arrayList.add(LocaleController.getString(R.string.LastFirst));
            types.add(2);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.NameOrder), types.indexOf(LawxConfig.nameOrder), getParentActivity(), view, i -> {
                LawxConfig.setNameOrder(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
                parentLayout.rebuildAllFragmentViews(false, false);
            }, resourcesProvider);
        } else if (id == translationProviderRow) {
            Translator.showTranslationProviderSelector(getParentActivity(), view, param -> {
                item.textValue = getTranslationProvider();
                if (param) {
                    listView.adapter.notifyItemChanged(position, PARTIAL);
                } else {
                    listView.adapter.notifyItemChanged(position, PARTIAL);
                    notifyItemChanged(translationTargetRow, PARTIAL);
                }
            }, resourcesProvider);
        } else if (id == translationTargetRow) {
            Translator.showTranslationTargetSelector(this, view, () -> {
                item.textValue = getTranslationTarget();
                listView.adapter.notifyItemChanged(position, PARTIAL);
                if (Translator.getRestrictedLanguages().size() == 1) {
                    notifyItemChanged(doNotTranslateRow, PARTIAL);
                }
            }, resourcesProvider);
        } else if (id == openArchiveOnPullRow) {
            LawxConfig.toggleOpenArchiveOnPull();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.openArchiveOnPull);
            }
        } else if (id == askBeforeCallRow) {
            LawxConfig.toggleAskBeforeCall();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.askBeforeCall);
            }
        } else if (id == idTypeRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString(R.string.IdTypeHidden));
            types.add(LawxConfig.ID_TYPE_HIDDEN);
            arrayList.add(LocaleController.getString(R.string.IdTypeAPI));
            types.add(LawxConfig.ID_TYPE_API);
            arrayList.add(LocaleController.getString(R.string.IdTypeBOTAPI));
            types.add(LawxConfig.ID_TYPE_BOTAPI);
            PopupHelper.show(arrayList, LocaleController.getString(R.string.IdType), types.indexOf(LawxConfig.idType), getParentActivity(), view, i -> {
                LawxConfig.setIdType(types.get(i));
                item.textValue = arrayList.get(i);
                listView.adapter.notifyItemChanged(position, PARTIAL);
                parentLayout.rebuildAllFragmentViews(false, false);
            }, resourcesProvider);
        } else if (id == accentAsNotificationColorRow) {
            LawxConfig.toggleAccentAsNotificationColor();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.accentAsNotificationColor);
            }
        } else if (id == silenceNonContactsRow) {
            LawxConfig.toggleSilenceNonContacts();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.silenceNonContacts);
            }
        } else if (id == translatorTypeRow) {
            int oldType = LawxConfig.transType;
            Translator.showTranslatorTypeSelector(getParentActivity(), view, () -> {
                int newType = LawxConfig.transType;
                item.textValue = getTranslatorType();
                listView.adapter.notifyItemChanged(position, PARTIAL);
                if (oldType != newType) {
                    int count = 4;
                    if (oldType == LawxConfig.TRANS_TYPE_LAWX || newType == LawxConfig.TRANS_TYPE_LAWX) {
                        count++;
                    }
                    if (oldType == LawxConfig.TRANS_TYPE_EXTERNAL) {
                        notifyItemRemoved(translatorExternalAppRow);
                        updateRows();
                        notifyItemRangeInserted(translationProviderRow, count);
                    } else if (newType == LawxConfig.TRANS_TYPE_EXTERNAL) {
                        notifyItemRangeRemoved(translationProviderRow, count);
                        updateRows();
                        notifyItemInserted(translatorExternalAppRow);
                    } else if (oldType == LawxConfig.TRANS_TYPE_LAWX) {
                        notifyItemRemoved(showOriginalRow);
                        updateRows();
                    } else if (newType == LawxConfig.TRANS_TYPE_LAWX) {
                        updateRows();
                        notifyItemInserted(showOriginalRow);
                    }
                }
            }, resourcesProvider);
        } else if (id == doNotTranslateRow) {
            presentFragment(new LawxLanguagesSelectActivity(LawxLanguagesSelectActivity.TYPE_RESTRICTED));
        } else if (id == autoTranslateRow) {
            LawxConfig.toggleAutoTranslate();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.autoTranslate);
            }
        } else if (id == showOriginalRow) {
            LawxConfig.toggleShowOriginal();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(LawxConfig.showOriginal);
            }
        } else if (id == translatorExternalAppRow) {
            Translator.showTranslationProviderSelector(getParentActivity(), view, param -> {
                item.textValue = getTranslatorExternalApp();
                listView.adapter.notifyItemChanged(position, PARTIAL);
            }, resourcesProvider);
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.General);
    }

    @Override
    protected String getKey() {
        return "g";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            var restrictedLanguageItem = listView.findItemByItemId(doNotTranslateRow);
            if (restrictedLanguageItem != null) {
                restrictedLanguageItem.textValue = getRestrictedLanguages();
                notifyItemChanged(doNotTranslateRow, PARTIAL);
            }
            var translationTargetItem = listView.findItemByItemId(translationTargetRow);
            if (translationTargetItem != null) {
                translationTargetItem.textValue = getTranslationTarget();
                notifyItemChanged(translationTargetRow, PARTIAL);
            }
        }
    }
}
