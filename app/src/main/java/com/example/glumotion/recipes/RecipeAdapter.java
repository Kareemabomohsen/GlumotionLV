package com.example.glumotion.recipes;

import com.example.glumotion.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Adapter of the recipes list: photo (downloaded with ImageLoader), name and category.
 */
public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    // The screen decides what happens when a recipe is clicked
    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    private final ArrayList<Recipe> recipeList;
    private final OnRecipeClickListener listener;

    // Constructor
    public RecipeAdapter(ArrayList<Recipe> recipeList, OnRecipeClickListener listener) {
        this.recipeList = recipeList;
        this.listener = listener;
    }

    // Creates one recipe card
    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    // Fills one card (Dessert gets a sugar warning tag)
    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        final Recipe recipe = recipeList.get(position);
        holder.tvTitle.setText(recipe.getTitle());
        String info = recipe.getCategory();
        if (!recipe.getArea().isEmpty()) {
            info = info + " • " + recipe.getArea();
        }
        holder.tvInfo.setText(info);
        if (recipe.isSweet()) {
            holder.tvTag.setText("⚠ High sugar - small portion");
            holder.tvTag.setTextColor(0xFFD63031);
        } else {
            holder.tvTag.setText("Tap for recipe →");
            holder.tvTag.setTextColor(0xFF4A8B5E);
        }
        ImageLoader.load(recipe.getSmallImageUrl(), holder.ivImage);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onRecipeClick(recipe);
            }
        });
    }

    // Number of recipes
    @Override
    public int getItemCount() {
        return recipeList.size();
    }

    // Views of one card
    public static class RecipeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle, tvInfo, tvTag;

        // Finds the views of the card once
        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivRecipeImage);
            tvTitle = itemView.findViewById(R.id.tvRecipeTitle);
            tvInfo = itemView.findViewById(R.id.tvRecipeInfo);
            tvTag = itemView.findViewById(R.id.tvRecipeTag);
        }
    }
}
