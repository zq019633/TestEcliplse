package com.artifex.mupdfdemo;



import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;

import com.example.administrator.testecliplse.R;


public class NewActivity  extends Activity{
	  private Button but;

	
	
	@Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.news);
        but = (Button) findViewById(R.id.bt);
        initData();
	}

	private void initData() {
		// TODO Auto-generated method stub
		 but.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View v) {

	                Uri uri = Uri.parse("/storage/emulated/0/tencent/QQfile_recv/1.pdf");
	                Intent intent = new Intent(NewActivity.this,MuPDFActivity.class);
	                intent.setAction(Intent.ACTION_VIEW);
	                intent.setData(uri);
	                startActivity(intent);
	            }
	        });
		
	}
}
