package com.example.glumotion.recipes;

import com.example.glumotion.R;
import com.example.glumotion.common.BaseActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Recipes from the FREE API TheMealDB (with photos).
 * Category buttons + search. Tapping a recipe opens RecipeDetailActivity
 * (ingredients, preparation, video, nutrition estimate and "Add").
 */
public class SuggestedDishes extends BaseActivity {

    private final ArrayList<Recipe> recipes = new ArrayList<>();
    private RecipeAdapter adapter;
    private EditText etSearch;
    private TextView tvStatus;
    private ProgressBar pbRecipes;
    private final ArrayList<TextView> categoryButtons = new ArrayList<>();

    // Builds the screen and loads the first category
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggested_dishes);
        setupBackBar("Recipes 🍲");

        etSearch = findViewById(R.id.etRecipeSearch);
        tvStatus = findViewById(R.id.tvRecipeStatus);
        pbRecipes = findViewById(R.id.pbRecipes);

        RecyclerView rv = findViewById(R.id.recyclerViewRecipes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(recipes, new RecipeAdapter.OnRecipeClickListener() {
            @Override
            public void onRecipeClick(Recipe recipe) {
                Intent intent = new Intent(SuggestedDishes.this, RecipeDetailActivity.class);
                intent.putExtra(RecipeDetailActivity.EXTRA_ID, recipe.getId());
                startActivity(intent);
            }
        });
        rv.setAdapter(adapter);

        buildCategoryButtons();
        setupSearch();
        loadCategory(0);
    }

    // One button for every category (made in code)
    private void buildCategoryButtons() {
        LinearLayout container = findViewById(R.id.llRecipeCategories);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < RecipeApi.CATEGORIES.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(RecipeApi.CATEGORIES[i]);
            chip.setTextSize(13);
            chip.setPadding(pad, pad / 2, pad, pad / 2);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(pad / 3, 0, pad / 3, 0);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadCategory(index);
                }
            });
            container.addView(chip, params);
            categoryButtons.add(chip);
        }
    }

    // Colors the selected category button green
    private void selectCategory(int index) {
        for (int i = 0; i < categoryButtons.size(); i++) {
            TextView chip = categoryButtons.get(i);
            if (i == index) {
                chip.setBackgroundResource(R.drawable.bg_pill_filter_active);
                chip.setTextColor(0xFFFFFFFF);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                chip.setTextColor(0xFF1D2623);
            }
        }
    }

    // Search button + "search" key of the keyboard
    private void setupSearch() {
        findViewById(R.id.btnRecipeSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });
        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search();
                    return true;
                }
                return false;
            }
        });
    }

    // Recipes of one category from the API
    private void loadCategory(int index) {
        selectCategory(index);
        final String category = RecipeApi.CATEGORIES[index];
        showLoading(category + " recipes...");
        RecipeApi.byCategory(category, listCallback(category + " recipes"));
    }

    // Recipes by name from the API
    private void search() {
        String text = etSearch.getText().toString().trim();
        if (text.length() < 2) {
            etSearch.setError("Type at least 2 letters");
            return;
        }
        selectCategory(-1);
        showLoading("Searching \"" + text + "\"...");
        RecipeApi.search(text, listCallback("Results for \"" + text + "\""));
    }

    // Same answer for category and search: show the list (or a message)
    private RecipeApi.ListCallback listCallback(final String title) {
        return new RecipeApi.ListCallback() {
            @Override
            public void onRecipes(ArrayList<Recipe> list) {
                pbRecipes.setVisibility(View.GONE);
                recipes.clear();
                recipes.addAll(list);
                adapter.notifyDataSetChanged();
                if (list.isEmpty()) {
                    tvStatus.setText("No recipes found - try another word");
                } else {
                    tvStatus.setText(title + " (" + list.size() + ")");
                }
            }

            @Override
            public void onError(String error) {
                pbRecipes.setVisibility(View.GONE);
                tvStatus.setText("No internet - recipes need internet");
            }
        };
    }

    // Shows the loading circle and a text
    private void showLoading(String text) {
        pbRecipes.setVisibility(View.VISIBLE);
        tvStatus.setText(text);
    }
}
