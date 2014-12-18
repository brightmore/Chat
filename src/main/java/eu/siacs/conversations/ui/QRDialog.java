package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.view.View;
import android.view.Window;
import android.content.DialogInterface;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class QRDialog extends Dialog {
    public QRDialog(Context context, View view) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(view);
        getWindow().getDecorView().setBackgroundResource(android.R.color.white);
    }
}