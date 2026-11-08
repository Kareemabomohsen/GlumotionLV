package com.example.glumotion.food;

import com.example.glumotion.R;
import com.example.glumotion.chat.ChatBotActivity;
import com.example.glumotion.common.DateUtil;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Small window from the bottom when the user presses "+" on a meal.
 * Options: search by name, barcode (typed), food bot, recent foods.
 */
public class AddMealBottomSheet extends BottomSheetDialogFragment {

    private String mealType = "Breakfast";
    private String dateKey;

    // Creates the sheet and passes the meal + day safely inside a Bundle
    public static AddMealBottomSheet newInstance(String mealType, String dateKey) {
        AddMealBottomSheet fragment = new AddMealBottomSheet();
        Bundle args = new Bundle();
        args.putString("MEAL_TYPE", mealType);
        args.putString("DATE", dateKey);
        fragment.setArguments(args);
        return fragment;
    }

    // Reads the meal + day from the arguments
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            if (getArguments().getString("MEAL_TYPE") != null) {
                mealType = getArguments().getString("MEAL_TYPE");
            }
            dateKey = getArguments().getString("DATE");
        }
        if (dateKey == null) {
            dateKey = DateUtil.todayKey();
        }
    }

    // Inflates the sheet and connects its buttons
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet, container, false);

        final EditText etSearch = view.findViewById(R.id.etSearchFood);
        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    openSearch(etSearch.getText().toString().trim(), false);
                    return true;
                }
                return false;
            }
        });

        view.findViewById(R.id.btnBarcode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch(null, true);
            }
        });
        view.findViewById(R.id.btnTakePhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch(etSearch.getText().toString().trim(), false);
            }
        });
        // Food Bot: tell it what you ate (or send a photo) and it adds it to THIS meal
        view.findViewById(R.id.btnFoodBot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(requireContext(), ChatBotActivity.class);
                intent.putExtra(ChatBotActivity.EXTRA_MODE, ChatBotActivity.MODE_FOOD);
                intent.putExtra(ChatBotActivity.EXTRA_MEAL, mealType);
                startActivity(intent);
                dismiss();
            }
        });
        view.findViewById(R.id.btnSavedFood).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch(null, false);
            }
        });
        return view;
    }

    // Opens the food search screen for this meal and day
    private void openSearch(String query, boolean openBarcode) {
        Intent intent = new Intent(requireContext(), FoodSearchActivity.class);
        intent.putExtra(FoodSearchActivity.EXTRA_MEAL, mealType);
        intent.putExtra(FoodSearchActivity.EXTRA_DATE, dateKey);
        intent.putExtra(FoodSearchActivity.EXTRA_OPEN_BARCODE, openBarcode);
        if (query != null) {
            intent.putExtra(FoodSearchActivity.EXTRA_QUERY, query);
        }
        startActivity(intent);
        dismiss();
    }
}
