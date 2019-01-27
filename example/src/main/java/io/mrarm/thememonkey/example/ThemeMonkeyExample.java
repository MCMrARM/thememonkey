package io.mrarm.thememonkey.example;

import android.content.res.TypedArray;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.mrarm.thememonkey.Theme;
import io.mrarm.arsc.ArscWriter;
import io.mrarm.arsc.chunks.ResTable;
import io.mrarm.arsc.chunks.ResValue;

public class ThemeMonkeyExample extends AppCompatActivity {

    private static Random random = new Random();

    private static int expectedColor = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        expectedColor = new Random().nextInt();
        File themeFile = writeTheme(expectedColor);
        Theme theme = new Theme(this, themeFile.getAbsolutePath());
        theme.applyToActivity(this);
        setTheme(ResTable.makeReference(0x7e, 2, 0));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_monkey_example);
        ((TextView) findViewById(R.id.status)).setText(
                String.format("Current: #%06X; expected: #%06X", getPrimaryColor(), expectedColor));

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recreate();
            }
        });
    }

    private int getPrimaryColor() {
        TypedArray arr = obtainStyledAttributes(new int[] { R.attr.colorPrimary });
        int color = arr.getColor(0, 0);
        arr.recycle();
        return color;
    }

    private ResTable createResTable(int color) {
        ResTable table = new ResTable();
        ResTable.Package pkg = new ResTable.Package(0x7e, "io.mrarm.thememonkey.theme");

        ResTable.TypeSpec colorTypeSpec = new ResTable.TypeSpec(1, "color",
                new int[] { 0 });
        pkg.addType(colorTypeSpec);
        ResTable.Type colorType = new ResTable.Type(1, new ResTable.Config());
        ResTable.Entry colorPrimary = new ResTable.Entry(0, "colorPrimary",
                new ResValue.Integer(ResValue.TYPE_INT_COLOR_ARGB8, color));
        colorType.addEntry(colorPrimary);
        pkg.addType(colorType);

        ResTable.TypeSpec styleTypeSpec = new ResTable.TypeSpec(2, "style",
                new int[] { 0 });
        pkg.addType(styleTypeSpec);
        ResTable.Type styleType = new ResTable.Type(2, new ResTable.Config());
        ResTable.MapEntry appTheme = new ResTable.MapEntry(0, "AppTheme");
        appTheme.setParent(R.style.AppTheme);
        appTheme.addValue(R.attr.colorPrimary, new ResValue.Reference(pkg, colorTypeSpec, colorPrimary));
        styleType.addEntry(appTheme);
        pkg.addType(styleType);

        table.addPackage(pkg);
        return table;
    }

    public File writeTheme(int color) {
        File file = new File(getCacheDir(), "theme-" + color + ".apk");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ZipOutputStream outStream = new ZipOutputStream(new BufferedOutputStream(fos));

            ZipEntry entry = new ZipEntry("resources.arsc");
            outStream.putNextEntry(entry);

            ArscWriter writer = new ArscWriter(createResTable(color));
            writer.write(outStream);

            outStream.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

}
