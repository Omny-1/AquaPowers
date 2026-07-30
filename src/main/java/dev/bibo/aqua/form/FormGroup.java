package dev.bibo.aqua.form;

import java.util.Arrays;
import java.util.List;

/** A named group of up to five abilities, selected by a number key. */
public final class FormGroup {

    private final String name;
    private final String color; // legacy colour code, e.g. "&b"
    private final List<WaterForm> forms;

    public FormGroup(String name, String color, WaterForm... forms) {
        this.name = name;
        this.color = color;
        this.forms = Arrays.asList(forms);
    }

    public String name() { return name; }
    public String color() { return color; }
    public List<WaterForm> forms() { return forms; }
    public int size() { return forms.size(); }

    public WaterForm get(int slot) {
        if (slot < 0 || slot >= forms.size()) return null;
        return forms.get(slot);
    }
}
