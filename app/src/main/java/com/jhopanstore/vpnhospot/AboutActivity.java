package com.jhopanstore.vpnhospot;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.parseColor("#0F1115"));

        int dp16 = dp(16);
        int dp12 = dp(12);
        int dp8 = dp(8);
        int dp4 = dp(4);
        int dp24 = dp(24);
        int dp20 = dp(20);

        // ── Root: vertical LinearLayout inside ScrollView ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0F1115"));
        scrollView.setFillViewport(true);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F1115"));
        root.setPadding(dp24, dp24, dp24, dp24);

        // ── Back button ──
        TextView btnBack = new TextView(this);
        btnBack.setText("← Back");
        btnBack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnBack.setTextColor(Color.parseColor("#00E5A8"));
        btnBack.setPadding(dp8, dp8, dp8, dp8);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backParams.bottomMargin = dp24;
        root.addView(btnBack, backParams);

        // ── Center content wrapper ──
        LinearLayout centerWrapper = new LinearLayout(this);
        centerWrapper.setOrientation(LinearLayout.VERTICAL);
        centerWrapper.setGravity(Gravity.CENTER_HORIZONTAL);

        // "JS" logo
        TextView tvLogo = new TextView(this);
        tvLogo.setText("JS");
        tvLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        tvLogo.setTypeface(null, Typeface.BOLD);
        tvLogo.setTextColor(Color.parseColor("#00E5A8"));
        tvLogo.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logoParams.bottomMargin = dp8;
        centerWrapper.addView(tvLogo, logoParams);

        // App name
        TextView tvAppName = new TextView(this);
        tvAppName.setText("VPN Hotspot");
        tvAppName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvAppName.setTypeface(null, Typeface.BOLD);
        tvAppName.setTextColor(Color.WHITE);
        tvAppName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams appNameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        appNameParams.bottomMargin = dp4;
        centerWrapper.addView(tvAppName, appNameParams);

        // Version
        TextView tvVersion = new TextView(this);
        tvVersion.setText("Version 1.0.0");
        tvVersion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvVersion.setTextColor(Color.parseColor("#A0A0A0"));
        tvVersion.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        versionParams.bottomMargin = dp24;
        centerWrapper.addView(tvVersion, versionParams);

        root.addView(centerWrapper, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // ── Developer Info Card ──
        LinearLayout card = createCard();
        card.setPadding(dp20, dp20, dp20, dp20);

        // "Developer" label
        card.addView(createLabel("Developer"));
        card.addView(createValue("JhopanStore", dp12));

        // Separator
        View separator = new View(this);
        separator.setBackgroundColor(Color.parseColor("#2A2D35"));
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
        );
        sepParams.topMargin = dp12;
        sepParams.bottomMargin = dp12;
        card.addView(separator, sepParams);

        // "Description" label
        card.addView(createLabel("Description"));
        card.addView(createValue("Lightweight HTTP + SOCKS5 proxy for hotspot and USB tethering routing", 0));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp24;
        root.addView(card, cardParams);

        // ── Links Section ──
        TextView tvLinksTitle = new TextView(this);
        tvLinksTitle.setText("Links");
        tvLinksTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvLinksTitle.setTypeface(null, Typeface.BOLD);
        tvLinksTitle.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams linksTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        linksTitleParams.bottomMargin = dp12;
        root.addView(tvLinksTitle, linksTitleParams);

        LinearLayout linksCard = createCard();
        linksCard.setPadding(dp20, dp16, dp20, dp16);

        linksCard.addView(createLinkItem("✈  Telegram", "@jhopanstore"));

        View linkSep = new View(this);
        linkSep.setBackgroundColor(Color.parseColor("#2A2D35"));
        LinearLayout.LayoutParams linkSepParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
        );
        linkSepParams.topMargin = dp12;
        linkSepParams.bottomMargin = dp12;
        linksCard.addView(linkSep, linkSepParams);

        linksCard.addView(createLinkItem("⚙  GitHub", "jhopanstore"));

        LinearLayout.LayoutParams linksCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        linksCardParams.bottomMargin = dp24;
        root.addView(linksCard, linksCardParams);

        // ── Spacer to push footer down ──
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(spacer, spacerParams);

        // ── Footer ──
        TextView tvFooter = new TextView(this);
        tvFooter.setText("Powered by JhopanStore");
        tvFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvFooter.setTextColor(Color.parseColor("#606060"));
        tvFooter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        footerParams.topMargin = dp24;
        root.addView(tvFooter, footerParams);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    // ── Helper: create a card with dark background and rounded corners ──
    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1A1D24"));
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);

        return card;
    }

    // ── Helper: section label (small, muted) ──
    private TextView createLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(Color.parseColor("#A0A0A0"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(4);
        tv.setLayoutParams(params);
        return tv;
    }

    // ── Helper: section value (white, normal weight) ──
    private TextView createValue(String text, int bottomMarginDp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(bottomMarginDp);
        tv.setLayoutParams(params);
        return tv;
    }

    // ── Helper: link item row ──
    private LinearLayout createLinkItem(String label, String handle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvLabel.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        row.addView(tvLabel, labelParams);

        TextView tvHandle = new TextView(this);
        tvHandle.setText(handle);
        tvHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvHandle.setTextColor(Color.parseColor("#00E5A8"));
        row.addView(tvHandle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return row;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }
}
