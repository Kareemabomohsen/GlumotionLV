package com.example.glumotion.workout;

import com.example.glumotion.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Adapter of the "Explanation" tab - shows every exercise as a card.
 */
public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder> {

    // What happens when the user clicks inside a card (the Activity decides)
    public interface OnExerciseClickListener {
        void onStartClick(Exercise exercise);

        void onVideoClick(Exercise exercise);
    }

    private final ArrayList<Exercise> exercises;
    private final OnExerciseClickListener listener;

    // Constructor
    public ExerciseAdapter(ArrayList<Exercise> exercises, OnExerciseClickListener listener) {
        this.exercises = exercises;
        this.listener = listener;
    }

    // Creates one card from item_exercise_reference.xml
    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise_reference, parent, false);
        return new ExerciseViewHolder(view);
    }

    // Fills the card with the exercise data
    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        final Exercise ex = exercises.get(position);

        holder.ivImage.setImageResource(ex.getImageRes());
        holder.tvName.setText(ex.getName());
        holder.tvMuscles.setText(ex.getMusclesText());
        holder.tvSetsReps.setText(ex.getSetsRepsText());
        holder.tvRest.setText("Rest: " + ex.getRestSeconds() + "s");
        holder.tvCues.setText(ex.getCuesText());
        holder.tvProgression.setText("Progression: " + ex.getProgression());
        holder.btnStart.setText("Start " + ex.getName() + " Workout");

        holder.btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onStartClick(ex);
            }
        });

        View.OnClickListener videoListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onVideoClick(ex);
            }
        };
        holder.btnPlay.setOnClickListener(videoListener);
        holder.frameImage.setOnClickListener(videoListener);
    }

    // Number of cards
    @Override
    public int getItemCount() {
        return exercises.size();
    }

    // Holds the views of one card
    public static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        View frameImage;
        ImageView ivImage, btnPlay;
        TextView tvName, tvMuscles, tvSetsReps, tvRest, tvCues, tvProgression;
        Button btnStart;

        public ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            frameImage = itemView.findViewById(R.id.frameExerciseImage);
            ivImage = itemView.findViewById(R.id.ivExerciseImage);
            btnPlay = itemView.findViewById(R.id.btnPlayVideo);
            tvName = itemView.findViewById(R.id.tvExerciseName);
            tvMuscles = itemView.findViewById(R.id.tvExerciseMuscles);
            tvSetsReps = itemView.findViewById(R.id.tvExerciseSetsReps);
            tvRest = itemView.findViewById(R.id.tvExerciseRest);
            tvCues = itemView.findViewById(R.id.tvExerciseCues);
            tvProgression = itemView.findViewById(R.id.tvExerciseProgression);
            btnStart = itemView.findViewById(R.id.btnStartExercise);
        }
    }
}
