package com.example.glumotion.start;

import com.example.glumotion.R;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

public class ProfileQuestionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // عند فتح هذه الشاشة لأول مرة، يتم عرض PQ1 تلقائياً داخل الـ fragment_container
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new PQ1());
            transaction.commit();
        }
    }
}