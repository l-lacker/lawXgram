package ru.llacker.neogram.update;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

import ru.llacker.neogram.NeoStrings;

/**
 * "Update Neogram" bar above the main tabs (ApplicationLoader.takeUpdateLayout): download,
 * progress with cancel, then install. Modelled on the update bar of Telegram's beta builds.
 */
public class NeoUpdateLayout extends IUpdateLayout {

    private final Activity activity;
    private final ViewGroup container;
    private FrameLayout layout;
    private RadialProgress2 icon;
    private AnimatedTextView text;

    public NeoUpdateLayout(Activity activity, ViewGroup container) {
        super(activity, container);
        this.activity = activity;
        this.container = container;
    }

    @Override
    public void updateFileProgress(Object[] args) {
        if (layout == null || text == null || !NeoUpdater.isDownloading()) {
            return;
        }
        float progress = NeoUpdater.getDownloadProgress();
        icon.setProgress(progress, true);
        text.setText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) (progress * 100)));
        layout.invalidate();
    }

    @Override
    public void createUpdateUI(int currentAccount) {
        if (container == null || layout != null) {
            return;
        }
        layout = new FrameLayout(activity);
        layout.setVisibility(View.INVISIBLE);
        layout.setTranslationY(dp(44));
        layout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        container.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        layout.setOnClickListener(v -> onClick(currentAccount));

        text = new AnimatedTextView(activity, true, true, true) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.translate(dp(15), 0);
                super.onDraw(canvas);
                canvas.translate((getMeasuredWidth() - width()) / 2f - dp(30), dp(11));
                icon.draw(canvas);
                canvas.restore();
            }
        };
        text.setTextSize(dp(15));
        text.setTypeface(AndroidUtilities.bold());
        text.setTextColor(0xffffffff);
        text.setGravity(Gravity.CENTER);
        layout.addView(text, LayoutHelper.createFrameMatchParent());
        text.setText(NeoStrings.get(R.string.NeoUpdateApp), false);

        icon = new RadialProgress2(text);
        icon.setColors(0xffffffff, 0xffffffff, Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButton));
        icon.setProgressRect(0, 0, dp(22), dp(22));
        icon.setCircleRadius(dp(11));
        icon.setAsMini();
    }

    private void onClick(int currentAccount) {
        int state = icon.getIcon();
        if (state == MediaActionDrawable.ICON_DOWNLOAD) {
            NeoUpdater.download();
        } else if (state == MediaActionDrawable.ICON_CANCEL) {
            NeoUpdater.cancelDownload();
        } else {
            NeoUpdater.install(activity);
        }
        updateAppUpdateViews(currentAccount, true);
    }

    @Override
    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (container == null) {
            return;
        }
        if (NeoUpdater.getUpdate() != null) {
            createUpdateUI(currentAccount);
            File file = NeoUpdater.getDownloadedFile();
            if (file != null) {
                icon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                text.setText(LocaleController.getString(R.string.AppUpdateNow), animated);
            } else if (NeoUpdater.isDownloading()) {
                float progress = NeoUpdater.getDownloadProgress();
                icon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                icon.setProgress(progress, true);
                text.setText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) (progress * 100)), animated);
            } else {
                icon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                text.setText(NeoStrings.get(R.string.NeoUpdateApp), animated);
            }
            if (layout.getTag() != null) {
                return;
            }
            layout.setVisibility(View.VISIBLE);
            layout.setTag(1);
            if (animated) {
                layout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).start();
            } else {
                layout.setTranslationY(0);
            }
        } else {
            if (layout == null || layout.getTag() == null) {
                return;
            }
            layout.setTag(null);
            if (animated) {
                layout.animate().translationY(dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (layout.getTag() == null) {
                            layout.setVisibility(View.INVISIBLE);
                        }
                    }
                }).setDuration(180).start();
            } else {
                layout.setTranslationY(dp(44));
                layout.setVisibility(View.INVISIBLE);
            }
        }
    }
}
