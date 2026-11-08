package com.example.glumotion.chat;

import android.graphics.Bitmap;

import java.util.ArrayList;

/**
 * One message of the chat (from the user or from the bot).
 * A bot message can have a link (e.g. a YouTube video) or a 🗑 button (foods the bot added),
 * and a user message can have a photo (a meal).
 */
public class ChatMessage {

    private final String text;
    private final boolean fromUser;
    private String link = null;       // web link shown as a button under the bot message
    private String linkLabel = null;  // text of that button ("▶ Watch on YouTube")
    private Bitmap photo = null;      // photo sent by the user (food bot)
    private ArrayList<AddedFood> addedFoods = null;   // foods the bot added → 🗑 button under the message

    // Constructor of a normal text message
    public ChatMessage(String text, boolean fromUser) {
        this.text = text;
        this.fromUser = fromUser;
    }

    // Adds a link button to the message
    public void setLink(String link, String linkLabel) {
        this.link = link;
        this.linkLabel = linkLabel;
    }

    // Adds a photo to the message
    public void setPhoto(Bitmap photo) {
        this.photo = photo;
    }

    // The foods this answer added (the user can delete them with the 🗑 button)
    public void setAddedFoods(ArrayList<AddedFood> foods) {
        this.addedFoods = foods;
    }

    // Getters
    public ArrayList<AddedFood> getAddedFoods() { return addedFoods; }
    public String getText() { return text; }
    public boolean isFromUser() { return fromUser; }
    public String getLink() { return link; }
    public String getLinkLabel() { return linkLabel; }
    public Bitmap getPhoto() { return photo; }
}
