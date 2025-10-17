package htw.webtech.myapp.persistence.entity;

import java.time.LocalDateTime;

public class TodoEntry {
    String name;
    LocalDateTime due;

    TodoEntry() {

    }
    public TodoEntry(String name) {
        this.name = name;
    }

    public LocalDateTime getDue() {
        return due;
    }

    public void setDue(LocalDateTime due) {
        this.due = due;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
}
