package won.bot.skeleton.model;

import java.util.ArrayList;
import java.util.List;

public class Poll {
    private String title;
    private List<String> answers;
    private List<String> tags;
    private long id;

    public Poll(){
        answers = new ArrayList<>();
        tags = new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public void addAnswer(String ans){
        answers.add(ans);
    }

    public void addTags(String tag) {
        tags.add(tag);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        String text = "";
        for(String a : answers){
            text += "\n\t " + a;
        }
        return "Umfrage: " + title + "\nAntworten: \n{" + text + "\n}";
    }
}
