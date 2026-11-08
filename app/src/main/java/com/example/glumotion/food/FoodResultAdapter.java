package com.example.glumotion.food;

import com.example.glumotion.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Adapter for the list of foods (search results / recent foods).
 */
public class FoodResultAdapter extends RecyclerView.Adapter<FoodResultAdapter.FoodViewHolder> {

    // The screen decides what happens on click
    public interface OnFoodClickListener {
        void onFoodClick(FoodItem food);
    }

    private final ArrayList<FoodItem> foods;
    private final OnFoodClickListener listener;

    // Constructor
    public FoodResultAdapter(ArrayList<FoodItem> foods, OnFoodClickListener listener) {
        this.foods = foods;
        this.listener = listener;
    }

    // Creates one row from item_food_result.xml
    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food_result, parent, false);
        return new FoodViewHolder(view);
    }

    // Fills one row
    @Override
    public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
        final FoodItem food = foods.get(position);
        holder.tvName.setText(food.getFullName());
        holder.tvDetails.setText(food.getPer100Text());

        // Green tag = low sugar, red tag = high sugar (important for diabetics)
        if (food.isDiabetesFriendly()) {
            holder.tvTag.setText("Low sugar");
            holder.tvTag.setTextColor(0xFF059669);
            holder.tvTag.setBackgroundResource(R.drawable.bg_tag_friendly);
        } else {
            holder.tvTag.setText("High sugar " + FoodItem.round(food.getSugar100()) + "g");
            holder.tvTag.setTextColor(0xFFD63031);
            holder.tvTag.setBackgroundResource(R.drawable.bg_tag_sugar);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFoodClick(food);
            }
        });
    }

    // Number of rows
    @Override
    public int getItemCount() {
        return foods.size();
    }

    // Holds the views of one row
    public static class FoodViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDetails, tvTag;

        public FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFoodName);
            tvDetails = itemView.findViewById(R.id.tvFoodDetails);
            tvTag = itemView.findViewById(R.id.tvFoodTag);
        }
    }
}
