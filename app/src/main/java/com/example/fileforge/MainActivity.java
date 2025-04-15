package com.example.fileforge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout fileConverterCard = findViewById(R.id.fileConverterCard);
        LinearLayout fileResizerCard = findViewById(R.id.fileResizerCard);

        fileConverterCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MainActivity.this,FileConverter.class);
                startActivity(intent);
            }
        });

        fileResizerCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MainActivity.this, FileResizer.class);
                startActivity(intent);
            }
        });
    }
}
